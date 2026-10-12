package com.stride.run

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import java.time.ZoneId

/** Health Connect step sync, shared by the connection screen and the dashboard's silent refresh. */
object HealthSync {
    val readSteps = setOf(HealthPermission.getReadPermission(StepsRecord::class))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var running = false

    /** Refreshes in the background when access is already granted. Never opens a permission screen. */
    @JvmStatic
    fun refresh(context: Context, done: Runnable?) {
        val app = context.applicationContext
        if (running || HealthConnectClient.getSdkStatus(app) != HealthConnectClient.SDK_AVAILABLE) return
        running = true
        scope.launch {
            try {
                val client = HealthConnectClient.getOrCreate(app)
                if (client.permissionController.getGrantedPermissions().containsAll(readSteps)) sync(app, client)
                else app.getSharedPreferences("kadamm_health", Context.MODE_PRIVATE).edit()
                    .putBoolean("healthConnected", false).apply()
            } catch (_: Exception) {
            } finally {
                running = false
                if (done != null) Handler(Looper.getMainLooper()).post(done)
            }
        }
    }

    /**
     * Reads today's steps outside Kadamm session windows (those are counted from the session
     * itself), then the last 30 daily totals for the calendar. Returns the status line to show.
     */
    suspend fun sync(context: Context, hc: HealthConnectClient): String {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val start = today.atStartOfDay(zone).toInstant()
        val end = Instant.now()
        val sessions = JSONArray(
            context.getSharedPreferences("kadamm_history", Context.MODE_PRIVATE).getString("sessions", "[]")
        )
        val intervals = mutableListOf<Pair<Instant, Instant>>()
        var sessionSteps = 0L
        for (i in 0 until sessions.length()) {
            val session = sessions.optJSONObject(i) ?: continue
            if (!session.optBoolean("stepsAvailable", false)) continue
            val endMs = session.optLong("endTimeMs", session.optLong("id", 0L))
            val startMs = session.optLong("startTimeMs", endMs - session.optLong("durationMs", 0L))
            val from = Instant.ofEpochMilli(startMs).coerceAtLeast(start)
            val to = Instant.ofEpochMilli(endMs).coerceAtMost(end)
            if (to.isAfter(from)) {
                intervals.add(from to to)
                if (LocalDate.ofInstant(Instant.ofEpochMilli(endMs), zone) == today) sessionSteps += session.optLong("steps", 0L)
            }
        }
        val tracking = context.getSharedPreferences("stride_state", Context.MODE_PRIVATE)
        if (tracking.getBoolean("active", false) && tracking.getBoolean("stepsAvailable", false)) {
            val endMs = System.currentTimeMillis()
            val startMs = tracking.getLong("sessionStartTimeMs", endMs - tracking.getLong("elapsed", 0L))
            val from = Instant.ofEpochMilli(startMs).coerceAtLeast(start)
            if (end.isAfter(from)) intervals.add(from to end)
            if (LocalDate.ofInstant(Instant.ofEpochMilli(startMs), zone) == today) sessionSteps += tracking.getInt("steps", 0).toLong()
        }
        val merged = intervals.sortedBy { it.first }.fold(mutableListOf<Pair<Instant, Instant>>()) { out, item ->
            if (out.isEmpty() || item.first.isAfter(out.last().second)) out.add(item)
            else if (item.second.isAfter(out.last().second)) out[out.lastIndex] = out.last().first to item.second
            out
        }
        var cursor = start
        var outside = 0L
        val origins = sortedSetOf<String>()
        suspend fun aggregate(from: Instant, to: Instant) {
            if (!to.isAfter(from)) return
            val response = hc.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), TimeRangeFilter.between(from, to)))
            outside += response[StepsRecord.COUNT_TOTAL] ?: 0L
            response.dataOrigins.forEach { origins.add(it.packageName) }
        }
        for ((from, to) in merged) {
            aggregate(cursor, from)
            if (to.isAfter(cursor)) cursor = to
        }
        aggregate(cursor, end)

        val prefs = context.getSharedPreferences("kadamm_health", Context.MODE_PRIVATE)
        val days = try { JSONObject(prefs.getString("healthDays", "{}") ?: "{}") } catch (_: Exception) { JSONObject() }
        try {
            val groups = hc.aggregateGroupByPeriod(
                AggregateGroupByPeriodRequest(
                    setOf(StepsRecord.COUNT_TOTAL),
                    TimeRangeFilter.between(today.minusDays(29).atStartOfDay(), LocalDateTime.now(zone)),
                    Period.ofDays(1)
                )
            )
            for (group in groups) {
                val count = group.result[StepsRecord.COUNT_TOTAL] ?: continue
                days.put(group.startTime.toLocalDate().toString(), count)
            }
        } catch (_: Exception) {
            // Daily history is a bonus for the calendar; today's total above is what matters.
        }

        val verified = outside + sessionSteps
        prefs.edit()
            .putString("healthDate", today.toString())
            .putLong("healthSteps", verified)
            .putLong("healthImportedSteps", outside)
            .putLong("healthSessionSteps", sessionSteps)
            .putLong("healthSyncedAt", System.currentTimeMillis())
            .putString("healthSources", origins.joinToString(","))
            .putString("healthDays", days.toString())
            .putBoolean("healthConnected", true)
            .apply()
        val status = if (outside == 0L) {
            "Connected, but Health Connect has no step records for today yet. If your steps are in Samsung Health, open Samsung Health > Settings > Health Connect and allow it to share Steps, then come back and refresh."
        } else {
            "Connected · %,d steps today from Health Connect (%,d outside Kadamm sessions, %,d during sessions).".format(verified, outside, sessionSteps)
        }
        prefs.edit().putString("healthStatus", status).apply()
        return status
    }
}
