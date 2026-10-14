package com.stride.run

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.AggregateGroupByDurationRequest
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import java.time.ZoneId

/** Health Connect step sync, shared by the connection screen and the dashboard's silent refresh. */
object HealthSync {
    val readSteps = setOf(HealthPermission.getReadPermission(StepsRecord::class))
    /** Steps are required to count as connected; the rest are optional extras for the dashboard. */
    val allPermissions = readSteps + setOf(
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class)
    )
    @JvmStatic fun isRunning() = running
    private const val SAMSUNG = "com.sec.android.app.shealth"
    @JvmStatic fun label(pkg: String) = when {
        pkg == SAMSUNG -> "Samsung Health"
        pkg.startsWith("com.android.healthconnect.phone") -> "Phone (Health Connect)"
        pkg == "com.google.android.apps.fitness" -> "Google Fit"
        pkg.isEmpty() -> "Health Connect"
        else -> pkg.substringAfterLast('.')
    }
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

        // Health Connect adds every source together, so Samsung Health plus the phone's own tracker double the day.
        // Read each source on its own and use the most complete one. Samsung Health shows the phone tracker's count
        // live but writes its own record to Health Connect late, so the highest source is the one that matches it.
        val dayRange = TimeRangeFilter.between(start, end)
        val perOrigin = linkedMapOf<String, Long>()
        val all = hc.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), dayRange))
        for (origin in all.dataOrigins) {
            perOrigin[origin.packageName] = hc.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), dayRange, setOf(origin)))[StepsRecord.COUNT_TOTAL] ?: 0L
            origins.add(origin.packageName)
        }
        val preferred = perOrigin.maxByOrNull { it.value }?.key
        val dayTotal = preferred?.let { perOrigin[it] } ?: (all[StepsRecord.COUNT_TOTAL] ?: 0L)
        val filter = preferred?.let { setOf(DataOrigin(it)) } ?: emptySet()

        val prefs = context.getSharedPreferences("kadamm_health", Context.MODE_PRIVATE)
        val days = try { JSONObject(prefs.getString("healthDays", "{}") ?: "{}") } catch (_: Exception) { JSONObject() }
        val fresh = JSONObject()
        // Past days: the highest single source per day, for the same reason.
        val window = TimeRangeFilter.between(today.minusDays(29).atStartOfDay(), LocalDateTime.now(zone))
        for (origin in perOrigin.keys.ifEmpty { listOf("") }) {
            try {
                val groups = hc.aggregateGroupByPeriod(
                    AggregateGroupByPeriodRequest(setOf(StepsRecord.COUNT_TOTAL), window, Period.ofDays(1),
                        if (origin.isEmpty()) emptySet() else setOf(DataOrigin(origin)))
                )
                for (group in groups) {
                    val count = group.result[StepsRecord.COUNT_TOTAL] ?: continue
                    val key = group.startTime.toLocalDate().toString()
                    if (fresh.optLong(key, -1) < count) fresh.put(key, count)
                }
            } catch (_: Exception) {
                // Daily history is a bonus for the calendar; today's total is what matters.
            }
        }
        for (key in fresh.keys()) days.put(key, fresh.getLong(key))

        val granted = try { hc.permissionController.getGrantedPermissions() } catch (_: Exception) { emptySet() }
        val edit = prefs.edit()
        edit.remove("healthDistanceM").remove("healthKcal").remove("healthActiveKcal")
        suspend fun best(metric: androidx.health.connect.client.aggregate.AggregateMetric<*>, value: (Any) -> Double): Double? {
            var top: Double? = null
            for (origin in perOrigin.keys) {
                val v = try { hc.aggregate(AggregateRequest(setOf(metric), dayRange, setOf(DataOrigin(origin))))[metric] } catch (_: Exception) { null } ?: continue
                val d = value(v); if (top == null || d > top) top = d
            }
            return top ?: try { hc.aggregate(AggregateRequest(setOf(metric), dayRange))[metric]?.let(value) } catch (_: Exception) { null }
        }
        if (granted.contains(HealthPermission.getReadPermission(DistanceRecord::class)))
            best(DistanceRecord.DISTANCE_TOTAL) { (it as androidx.health.connect.client.units.Length).inMeters }?.let { edit.putFloat("healthDistanceM", it.toFloat()) }
        if (granted.contains(HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class)))
            best(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL) { (it as androidx.health.connect.client.units.Energy).inKilocalories }?.let { edit.putFloat("healthActiveKcal", it.toFloat()) }
        if (granted.contains(HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class)))
            best(TotalCaloriesBurnedRecord.ENERGY_TOTAL) { (it as androidx.health.connect.client.units.Energy).inKilocalories }?.let { edit.putFloat("healthKcal", it.toFloat()) }
        // Active minutes: minutes of the day with 60+ steps from the chosen source, close to Samsung Health's "active time".
        edit.remove("healthActiveMin")
        try {
            val minutes = hc.aggregateGroupByDuration(AggregateGroupByDurationRequest(setOf(StepsRecord.COUNT_TOTAL), dayRange, Duration.ofMinutes(1), filter))
            edit.putInt("healthActiveMin", minutes.count { (it.result[StepsRecord.COUNT_TOTAL] ?: 0L) >= 60L })
        } catch (_: Exception) {
        }
        // Each Kadamm session from the last 30 days gets what Health Connect recorded in its time window,
        // re-read until six hours after it ended (Samsung Health writes late), then left alone.
        try {
            val history = context.getSharedPreferences("kadamm_history", Context.MODE_PRIVATE)
            val sessionsAll = JSONArray(history.getString("sessions", "[]"))
            val now = System.currentTimeMillis(); var changed = false
            val activeAllowed = granted.contains(HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class))
            for (i in 0 until sessionsAll.length()) {
                val s = sessionsAll.optJSONObject(i) ?: continue
                val from = s.optLong("startTimeMs", 0L); val to = s.optLong("endTimeMs", 0L)
                if (to <= from || now - to > 30L * 86400000L || s.optLong("hcSyncedAt", 0L) > to + 6L * 3600000L) continue
                val range = TimeRangeFilter.between(Instant.ofEpochMilli(from), Instant.ofEpochMilli(to))
                var steps = 0L; var kcal = -1.0
                for (origin in perOrigin.keys) {
                    val o = setOf(DataOrigin(origin))
                    steps = maxOf(steps, hc.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), range, o))[StepsRecord.COUNT_TOTAL] ?: 0L)
                    if (activeAllowed) hc.aggregate(AggregateRequest(setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL), range, o))[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]
                        ?.let { kcal = maxOf(kcal, it.inKilocalories) }
                }
                s.put("hcSteps", steps); if (kcal >= 0) s.put("hcActiveKcal", Math.round(kcal)) else s.remove("hcActiveKcal")
                s.put("hcSyncedAt", now); changed = true
            }
            if (changed) history.edit().putString("sessions", sessionsAll.toString()).apply()
        } catch (_: Exception) {
        }
        edit.putLong("healthDayTotal", dayTotal)
            .putString("healthOrigin", preferred ?: "")
            .putString("healthSourceTotals", perOrigin.entries.joinToString(" · ") { "${label(it.key)} %,d".format(it.value) })
            .putLong("healthPhoneAtSync", DaySteps.phoneOn(context, today.toString()))
            .apply()

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
        val status = if (dayTotal > 0L) {
            "Connected · %,d steps today from %s.".format(dayTotal, label(preferred ?: ""))
        } else if (outside == 0L) {
            "Connected, but Health Connect has no step records for today yet. If your steps are in Samsung Health, open Samsung Health > Settings > Health Connect and allow it to share Steps, then come back and refresh."
        } else {
            "Connected · %,d steps today from Health Connect (%,d outside Kadamm sessions, %,d during sessions).".format(verified, outside, sessionSteps)
        }
        prefs.edit().putString("healthStatus", status).apply()
        return status
    }
}
