package com.stride.run

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseRouteResult
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.Period
import java.time.ZoneId
import java.util.Date
import java.util.Locale

/**
 * Brings past activity into Kadamm through Health Connect: walks and runs recorded by Samsung Health, Zepp,
 * Google Fit or any app that shares workouts there (with their distance, steps, calories, heart rate and route
 * when shared), and daily step totals for the calendar. Nothing is counted twice: a workout overlapping a Kadamm
 * session is skipped, re-imports are recognised by the workout's id, and daily totals keep the larger figure.
 * Beyond 30 days back needs Health Connect's "past data" permission.
 */
object HealthImport {
    const val ROUTES = "android.permission.health.READ_EXERCISE_ROUTES"
    val exercise = HealthPermission.getReadPermission(ExerciseSessionRecord::class)
    @JvmStatic fun wanted(): Set<String> = setOf(exercise, HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY, ROUTES)

    interface Listener { fun progress(message: String); fun done(sessions: Int, days: Int, note: String?) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val walkTypes = setOf(ExerciseSessionRecord.EXERCISE_TYPE_WALKING, ExerciseSessionRecord.EXERCISE_TYPE_HIKING)
    private val runTypes = setOf(ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL)

    /** Imports the last {@code days} days; the listener is called on the main thread. */
    @JvmStatic fun run(context: Context, days: Int, listener: Listener) {
        val app = context.applicationContext; val main = Handler(Looper.getMainLooper())
        fun say(m: String) = main.post { listener.progress(m) }
        scope.launch {
            var sessions = 0; var dayCount = 0; var note: String? = null
            try {
                if (HealthConnectClient.getSdkStatus(app) != HealthConnectClient.SDK_AVAILABLE) throw IllegalStateException("Health Connect isn't available on this phone.")
                val hc = HealthConnectClient.getOrCreate(app)
                val granted = hc.permissionController.getGrantedPermissions()
                if (!granted.contains(exercise)) throw IllegalStateException("Allow \"Exercise\" for Kadamm in Health Connect first.")
                val history = granted.contains(HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY)
                val back = if (history) days else minOf(days, 30)
                if (!history && days > 30) note = "Health Connect only shares the last 30 days until you allow \"past data\"; imported 30 days."
                val zone = ZoneId.systemDefault(); val end = Instant.now(); val start = end.minusSeconds(back * 86400L)
                val range = TimeRangeFilter.between(start, end)

                // Daily steps for the calendar, from the preferred source (the larger figure is kept).
                say("Reading daily steps…")
                val healthPrefs = app.getSharedPreferences("kadamm_health", Context.MODE_PRIVATE)
                val origin = healthPrefs.getString("healthOrigin", "") ?: ""
                val filter = if (origin.isEmpty()) emptySet() else setOf(DataOrigin(origin))
                val stepDays = try { JSONObject(healthPrefs.getString("healthDays", "{}") ?: "{}") } catch (_: Exception) { JSONObject() }
                try {
                    val from = LocalDateTime.ofInstant(start, zone).toLocalDate().atStartOfDay(); val to = LocalDateTime.now(zone)
                    val rows = hc.aggregateGroupByPeriod(AggregateGroupByPeriodRequest(setOf(StepsRecord.COUNT_TOTAL), TimeRangeFilter.between(from, to), Period.ofDays(1), filter))
                    for (r in rows) { val n = r.result[StepsRecord.COUNT_TOTAL] ?: continue; val k = r.startTime.toLocalDate().toString(); if (stepDays.optLong(k, -1) < n) stepDays.put(k, n); if (n > 0) dayCount++ }
                    healthPrefs.edit().putString("healthDays", stepDays.toString()).apply()
                } catch (_: Exception) {}

                // Workouts
                say("Finding walks and runs…")
                val hist = app.getSharedPreferences("kadamm_history", Context.MODE_PRIVATE)
                val all = JSONArray(hist.getString("sessions", "[]"))
                val known = HashSet<String>(); val windows = ArrayList<LongArray>(); val ids = HashSet<Long>()
                for (i in 0 until all.length()) { val o = all.optJSONObject(i) ?: continue; o.optString("importId").takeIf { it.isNotEmpty() }?.let { known.add(it) }; windows.add(longArrayOf(o.optLong("startTimeMs"), o.optLong("endTimeMs"))); ids.add(o.optLong("id")) }
                var token: String? = null; val found = ArrayList<ExerciseSessionRecord>()
                do {
                    val page = hc.readRecords(ReadRecordsRequest(ExerciseSessionRecord::class, range, pageToken = token))
                    for (r in page.records) if ((r.exerciseType in walkTypes || r.exerciseType in runTypes) && r.metadata.dataOrigin.packageName != app.packageName) found.add(r)
                    token = page.pageToken
                } while (!token.isNullOrEmpty())
                var n = 0
                for (r in found.sortedBy { it.startTime }) {
                    n++; if (n % 5 == 0) say("Importing workout $n of ${found.size}…")
                    val id = r.metadata.id; if (known.contains(id)) continue
                    val s0 = r.startTime.toEpochMilli(); val e0 = r.endTime.toEpochMilli(); if (e0 - s0 < 3 * 60000L) continue
                    // Skip anything that overlaps a Kadamm session by half or more: that walk is already here.
                    if (windows.any { w -> val ov = minOf(e0, w[1]) - maxOf(s0, w[0]); ov > 0 && ov >= (e0 - s0) / 2 }) continue
                    val o = DataOrigin(r.metadata.dataOrigin.packageName); val win = TimeRangeFilter.between(r.startTime, r.endTime)
                    val steps = try { hc.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), win, setOf(o)))[StepsRecord.COUNT_TOTAL] ?: 0L } catch (_: Exception) { 0L }
                    val metres = try { hc.aggregate(AggregateRequest(setOf(DistanceRecord.DISTANCE_TOTAL), win, setOf(o)))[DistanceRecord.DISTANCE_TOTAL]?.inMeters ?: 0.0 } catch (_: Exception) { 0.0 }
                    val kcal = try { hc.aggregate(AggregateRequest(setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL), win, setOf(o)))[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories ?: -1.0 } catch (_: Exception) { -1.0 }
                    val run = r.exerciseType in runTypes; val indoor = r.exerciseType == ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL
                    val source = HealthSync.label(r.metadata.dataOrigin.packageName); val km = metres / 1000.0; val ms = e0 - s0
                    var sid = e0; while (ids.contains(sid)) sid++; ids.add(sid)
                    val s = JSONObject()
                    s.put("id", sid); s.put("startTimeMs", s0); s.put("endTimeMs", e0)
                    s.put("day", SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(s0))); s.put("date", SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault()).format(Date(e0))); s.put("time", SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(e0)))
                    s.put("type", if (run) "Run" else "Walk"); s.put("activityMode", if (indoor) "indoor" else "outdoor")
                    s.put("activityLabel", (if (indoor) "Indoor " else "Outdoor ") + (if (run) "run" else "walk") + " · imported from " + source)
                    s.put("imported", true); s.put("importId", id); s.put("source", source)
                    s.put("durationMs", ms); s.put("movingMs", ms); s.put("distanceKm", km); s.put("distanceEstimated", false)
                    s.put("steps", steps); s.put("stepsAvailable", steps > 0)
                    s.put("walkDistanceKm", if (run) 0.0 else km); s.put("runDistanceKm", if (run) km else 0.0); s.put("walkDurationMs", if (run) 0L else ms); s.put("runDurationMs", if (run) ms else 0L)
                    s.put("calories", if (kcal >= 0) Math.round(kcal) else Calories.estimate(app, if (run) 0.0 else km, if (run) 0L else ms, if (run) km else 0.0, if (run) ms else 0L, steps))
                    if (!r.title.isNullOrBlank()) s.put("name", r.title)
                    // Route, when the source shared one and Health Connect lets Kadamm read it.
                    val routeResult = r.exerciseRouteResult
                    if (routeResult is ExerciseRouteResult.Data) {
                        val pts = JSONArray()
                        for (p in routeResult.exerciseRoute.route) { val q = JSONObject(); q.put("lat", p.latitude); q.put("lon", p.longitude); q.put("s", (p.time.toEpochMilli() - s0) / 1000L); p.altitude?.let { q.put("a", Math.round(it.inMeters * 10) / 10.0) }; if (run) q.put("t", "r"); pts.put(q) }
                        s.put("route", pts.toString())
                    } else s.put("route", "[]")
                    HealthSync.importHeartRate(hc, s, win, s0)
                    all.put(s); windows.add(longArrayOf(s0, e0)); sessions++
                }
                if (sessions > 0) {
                    // Keep History in time order.
                    val list = ArrayList<JSONObject>(); for (i in 0 until all.length()) all.optJSONObject(i)?.let { list.add(it) }
                    list.sortBy { it.optLong("startTimeMs", it.optLong("id")) }
                    val sorted = JSONArray(); for (o in list) sorted.put(o)
                    hist.edit().putString("sessions", sorted.toString()).apply()
                }
                app.getSharedPreferences("kadamm_settings", Context.MODE_PRIVATE).edit().putLong("importedAt", System.currentTimeMillis()).putInt("importedDays", back).apply()
            } catch (e: Exception) {
                note = e.message ?: "Import stopped."
            }
            val s = sessions; val d = dayCount; val m = note
            main.post { listener.done(s, d, m) }
        }
    }
}
