package com.stride.run

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
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
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(Vo2MaxRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class)
    ) + WatchHealth.readPermissions + WatchHealth.writePermissions + HealthImport.wanted()
    @JvmStatic fun isRunning() = running
    /** Lets Kadamm read while closed, so the widget and reminders stay current. Only offered where Health Connect supports it. */
    const val BACKGROUND = HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND
    @JvmStatic fun backgroundSupported(context: Context): Boolean = try {
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE &&
            HealthConnectClient.getOrCreate(context).features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
    } catch (_: Throwable) { false }
    /** Skin temperature is only asked for where this phone's Health Connect supports it. */
    @JvmStatic fun requested(context: Context): Set<String> {
        val base = if (WatchHealth.skinSupported(context)) allPermissions else allPermissions - (WatchHealth.permissionFor("skin") ?: "")
        return if (backgroundSupported(context)) base + BACKGROUND else base
    }
    @JvmStatic fun backgroundGranted(context: Context) = context.getSharedPreferences("kadamm_health", Context.MODE_PRIVATE).getBoolean("hcBackground", false)
    private const val SAMSUNG = "com.sec.android.app.shealth"
    @JvmStatic fun label(pkg: String) = when {
        pkg == SAMSUNG -> "Samsung Health"
        pkg.startsWith("com.android.healthconnect.phone") -> "Phone (Health Connect)"
        pkg == "com.google.android.apps.fitness" -> "Google Fit"
        pkg == "com.huami.watch.hmwatchmanager" || pkg.startsWith("com.huami") || pkg.startsWith("com.zepp") -> "Zepp"
        pkg == "com.fitbit.FitbitMobile" -> "Fitbit"
        pkg == "com.garmin.android.apps.connectmobile" -> "Garmin Connect"
        pkg == "com.strava" -> "Strava"
        pkg == "com.stride.run" -> "You (Kadamm)"
        pkg.isEmpty() -> "Health Connect"
        else -> pkg.substringAfterLast('.')
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var running = false

    /** Refreshes in the background when access is already granted. Never opens a permission screen. */
    @JvmStatic
    fun refresh(context: Context, done: Runnable?) {
        val app = context.applicationContext
        if (running || HealthConnectClient.getSdkStatus(app) != HealthConnectClient.SDK_AVAILABLE) { done?.run(); return }
        running = true
        scope.launch {
            try {
                val client = HealthConnectClient.getOrCreate(app)
                val settings = app.getSharedPreferences("kadamm_settings", Context.MODE_PRIVATE)
                val granted = client.permissionController.getGrantedPermissions()
                // Step sources switched off: Kadamm counts on its own; only a watch's heart rate is still read.
                // Watch measurements are read whatever the step source: they don't depend on steps.
                if (WatchHealth.readPermissions.any { granted.contains(it) }) try { WatchHealth.read(app, client, granted) } catch (_: Exception) { }
                app.getSharedPreferences("kadamm_health", Context.MODE_PRIVATE).edit().putLong("vitalsCheckedAt", System.currentTimeMillis()).putBoolean("hcAvailable", true).apply()
                if (!settings.getBoolean("hcSteps", true)) { if (settings.getBoolean("hcHeart", true)) heartOnly(app, client, granted) }
                else if (granted.containsAll(readSteps)) { sync(app, client); DaySteps.rebalance(app) }
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
            val hrAllowed = granted.contains(HealthPermission.getReadPermission(HeartRateRecord::class)) && context.getSharedPreferences("kadamm_settings", Context.MODE_PRIVATE).getBoolean("hcHeart", true)
            for (i in 0 until sessionsAll.length()) {
                val s = sessionsAll.optJSONObject(i) ?: continue
                val from = s.optLong("startTimeMs", 0L); val to = s.optLong("endTimeMs", 0L)
                if (to <= from || now - to > 30L * 86400000L) continue
                val range = TimeRangeFilter.between(Instant.ofEpochMilli(from), Instant.ofEpochMilli(to))
                // Heart rate from a watch (via Samsung Health or any app writing to Health Connect), read once per session.
                if (hrAllowed && !s.optBoolean("hrChecked", false) && now - to > 30L * 60000L) { heartRate(hc, s, range, from); changed = true }
                // Steps minute by minute from the preferred source: cadence over time for sessions recorded before 8.2.
                if (!s.has("hcMin") && now - to > 30L * 60000L && to - from >= 3L * 60000L) { minuteSteps(hc, s, range, from, filter); changed = true }
                if (s.optLong("hcSyncedAt", 0L) > to + 6L * 3600000L) continue
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
        try { dailyMinutes(hc, prefs, today, zone, filter) } catch (_: Exception) { }
        edit.putBoolean("hcBackground", granted.contains(BACKGROUND))
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

    /**
     * Stores a session's heart rate as {seconds since start, bpm}, averaged into 5-second buckets, plus the source
     * app. Sessions with no heart-rate records (no watch worn) are only marked as checked: nothing is invented.
     */
    private suspend fun heartRate(hc: HealthConnectClient, s: JSONObject, range: TimeRangeFilter, from: Long) {
        try {
            val buckets = sortedMapOf<Long, MutableList<Long>>(); var token: String? = null; var origin = ""
            do {
                val page = hc.readRecords(ReadRecordsRequest(HeartRateRecord::class, range, pageToken = token))
                for (r in page.records) {
                    if (origin.isEmpty()) origin = r.metadata.dataOrigin.packageName
                    for (x in r.samples) {
                        val sec = (x.time.toEpochMilli() - from) / 1000L
                        if (sec >= 0 && x.beatsPerMinute in 30L..230L) buckets.getOrPut(sec / 5L * 5L) { mutableListOf() }.add(x.beatsPerMinute)
                    }
                }
                token = page.pageToken
            } while (!token.isNullOrEmpty())
            if (buckets.size >= 6) {
                val out = JSONArray()
                for ((sec, v) in buckets) out.put(JSONArray().put(sec).put(Math.round(v.average())))
                s.put("hr", out); s.put("hrSource", label(origin))
            }
            s.put("hrChecked", true)
        } catch (_: Exception) {
        }
    }

    /** Heart rate for an imported workout, read the same way as for Kadamm's own sessions. */
    suspend fun importHeartRate(hc: HealthConnectClient, s: JSONObject, range: TimeRangeFilter, from: Long) = heartRate(hc, s, range, from)

    /** Heart rate for recent sessions only, when step sharing is off but heart rate is on. */
    private suspend fun heartOnly(context: Context, hc: HealthConnectClient, granted: Set<String>) {
        if (!granted.contains(HealthPermission.getReadPermission(HeartRateRecord::class))) return
        val history = context.getSharedPreferences("kadamm_history", Context.MODE_PRIVATE)
        val all = JSONArray(history.getString("sessions", "[]")); val now = System.currentTimeMillis(); var changed = false
        for (i in 0 until all.length()) {
            val s = all.optJSONObject(i) ?: continue
            val from = s.optLong("startTimeMs", 0L); val to = s.optLong("endTimeMs", 0L)
            if (to <= from || now - to > 30L * 86400000L || s.optBoolean("hrChecked", false) || now - to < 30L * 60000L) continue
            heartRate(hc, s, TimeRangeFilter.between(Instant.ofEpochMilli(from), Instant.ofEpochMilli(to)), from); changed = true
        }
        if (changed) history.edit().putString("sessions", all.toString()).apply()
    }

    /** {seconds since start, steps in that minute} for a session, from Health Connect's per-minute step totals. */
    private suspend fun minuteSteps(hc: HealthConnectClient, s: JSONObject, range: TimeRangeFilter, from: Long, filter: Set<DataOrigin>) {
        try {
            val rows = hc.aggregateGroupByDuration(AggregateGroupByDurationRequest(setOf(StepsRecord.COUNT_TOTAL), range, Duration.ofMinutes(1), filter))
            val out = JSONArray()
            for (r in rows) { val n = r.result[StepsRecord.COUNT_TOTAL] ?: 0L; out.put(JSONArray().put((r.startTime.toEpochMilli() - from) / 1000L).put(n)) }
            s.put("hcMin", out)
        } catch (_: Exception) {
        }
    }

    /**
     * Per-day movement quality from minute-level steps, for the last 30 days (past days once, today every sync):
     * peak-30 cadence (the mean of the day's 30 highest minutes, Tudor-Locke), brisk minutes (100+ steps a
     * minute), the longest sitting stretch between 7 am and 11 pm (minutes under 10 steps), and steps per hour.
     */
    /**
     * What a watch measures, from any brand that shares into Health Connect (Galaxy Watch through Samsung Health,
     * Pixel Watch through Fitbit, Zepp, Garmin, Wear OS apps): sleep per night, resting heart rate and VO2max for the
     * last 30 days. Saved as "vitals" {sleep:{day:min}, rhr:{day:bpm}, vo2:{day:value}, sources:[apps]}; a day only
     * appears when a watch actually recorded it.
     */
    private suspend fun vitals(hc: HealthConnectClient, prefs: android.content.SharedPreferences, granted: Set<String>, today: LocalDate, zone: ZoneId) {
        val range = TimeRangeFilter.between(today.minusDays(30).atStartOfDay(zone).toInstant(), Instant.now())
        val out = JSONObject(); val sleep = JSONObject(); val rhr = JSONObject(); val vo2 = JSONObject(); val sources = java.util.TreeSet<String>()
        if (granted.contains(HealthPermission.getReadPermission(SleepSessionRecord::class))) {
            var token: String? = null
            do {
                val page = hc.readRecords(ReadRecordsRequest(SleepSessionRecord::class, range, pageToken = token))
                for (r in page.records) {
                    val min = Duration.between(r.startTime, r.endTime).toMinutes(); if (min < 60 || min > 16 * 60) continue
                    val key = r.endTime.atZone(zone).toLocalDate().toString()
                    sleep.put(key, sleep.optLong(key, 0) + min); sources.add(r.metadata.dataOrigin.packageName)
                }
                token = page.pageToken
            } while (token != null)
        }
        if (granted.contains(HealthPermission.getReadPermission(RestingHeartRateRecord::class))) {
            val page = hc.readRecords(ReadRecordsRequest(RestingHeartRateRecord::class, range))
            for (r in page.records) { if (r.beatsPerMinute in 30..120) { rhr.put(r.time.atZone(zone).toLocalDate().toString(), r.beatsPerMinute); sources.add(r.metadata.dataOrigin.packageName) } }
        }
        if (granted.contains(HealthPermission.getReadPermission(Vo2MaxRecord::class))) {
            val page = hc.readRecords(ReadRecordsRequest(Vo2MaxRecord::class, range))
            for (r in page.records) { if (r.vo2MillilitersPerMinuteKilogram in 10.0..90.0) { vo2.put(r.time.atZone(zone).toLocalDate().toString(), r.vo2MillilitersPerMinuteKilogram); sources.add(r.metadata.dataOrigin.packageName) } }
        }
        // Body weight from a scale, Samsung Health or any app that logs it: the last 120 days, one reading per day (the latest).
        val weight = JSONObject()
        if (granted.contains(HealthPermission.getReadPermission(WeightRecord::class))) {
            var token: String? = null
            val wr = TimeRangeFilter.between(today.minusDays(120).atStartOfDay(zone).toInstant(), Instant.now())
            do {
                val page = hc.readRecords(ReadRecordsRequest(WeightRecord::class, wr, pageToken = token))
                for (r in page.records) { val kg = r.weight.inKilograms; if (kg in 25.0..300.0) { weight.put(r.time.atZone(zone).toLocalDate().toString(), Math.round(kg * 10) / 10.0); sources.add(r.metadata.dataOrigin.packageName) } }
                token = page.pageToken
            } while (token != null)
        }
        out.put("sleep", sleep).put("rhr", rhr).put("vo2", vo2).put("weight", weight).put("sources", JSONArray(sources.toList())).put("at", System.currentTimeMillis())
        prefs.edit().putString("vitals", out.toString()).apply()
    }

    private suspend fun dailyMinutes(hc: HealthConnectClient, prefs: android.content.SharedPreferences, today: LocalDate, zone: ZoneId, filter: Set<DataOrigin>) {
        val days = try { JSONObject(prefs.getString("minuteDays", "{}") ?: "{}") } catch (_: Exception) { JSONObject() }
        for (back in 0..29) {
            val day = today.minusDays(back.toLong()); val key = day.toString()
            if (back > 0 && days.has(key) && (days.optJSONObject(key)?.let { it.has("b") || it.has("empty") } == true)) continue
            val start = day.atStartOfDay(zone).toInstant(); val end = if (back == 0) Instant.now() else day.plusDays(1).atStartOfDay(zone).toInstant()
            val rows = try { hc.aggregateGroupByDuration(AggregateGroupByDurationRequest(setOf(StepsRecord.COUNT_TOTAL), TimeRangeFilter.between(start, end), Duration.ofMinutes(1), filter)) } catch (_: Exception) { continue }
            val perMinute = LongArray(1440); val hours = LongArray(24)
            for (r in rows) { val m = ((r.startTime.toEpochMilli() - start.toEpochMilli()) / 60000L).toInt(); if (m in 0..1439) { val n = r.result[StepsRecord.COUNT_TOTAL] ?: 0L; perMinute[m] = n; hours[m / 60] += n } }
            if (perMinute.sum() == 0L && back > 0) { days.put(key, JSONObject().put("empty", true)); continue }
            val top = perMinute.sortedDescending().take(30)
            val brisk = perMinute.count { it >= 100L }
            val lastMinute = if (back == 0) ((Instant.now().toEpochMilli() - start.toEpochMilli()) / 60000L).toInt().coerceAtMost(1380) else 1380
            var sit = 0; var run = 0; val sits = JSONArray()
            for (m in 420 until lastMinute) {
                if (perMinute[m] < 10L) { run++; if (run > sit) sit = run }
                else { if (run >= 30) sits.put(JSONArray().put(m - run).put(run)); run = 0 }
            }
            val h = JSONArray(); for (v in hours) h.put(v)
            // Minutes by step rate, for movement load: 20–59, 60–99, 100–129 and 130+ steps a minute.
            val bands = JSONArray().put(perMinute.count { it in 20L..59L }).put(perMinute.count { it in 60L..99L }).put(perMinute.count { it in 100L..129L }).put(perMinute.count { it >= 130L })
            // The last minute with 10+ steps (from the watch or phone), so "sitting now" also knows when the watch saw you walk.
            var lastMove = -1; for (mm in 0 until 1440) if (perMinute[mm] >= 10L) lastMove = mm
            days.put(key, JSONObject().put("p30", Math.round(top.average())).put("brisk", brisk).put("sit", sit).put("sits", sits).put("h", h).put("b", bands).put("lastMove", lastMove))
        }
        prefs.edit().putString("minuteDays", days.toString()).apply()
    }
}
