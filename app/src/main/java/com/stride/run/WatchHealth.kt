package com.stride.run

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyTemperatureRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SkinTemperatureRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Pressure
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.reflect.KClass
import kotlinx.coroutines.launch

/**
 * Everything a watch shares into Health Connect, from any brand (Galaxy Watch through Samsung Health, Pixel Watch
 * and Fitbit, Garmin, Zepp/Amazfit, Withings, Oura, Polar, Wear OS apps): sleep with stages, heart rate all day,
 * resting heart rate, HRV, breathing rate, SpO2, skin and body temperature, blood pressure, VO2max and weight.
 *
 * Saved as kadamm_health "vitals": one value per day for each measure (only days that really have a reading),
 * plus heart rate summarised per day as a histogram of minutes (for strain and stress with your own resting and
 * maximum heart rate), today's heart rate minute by minute, and which apps supplied what. Past days are read once;
 * today and yesterday are re-read on every sync, so new readings appear as soon as the watch's app shares them.
 */
object WatchHealth {
    const val DAYS = 30
    /** Heart-rate histogram bins: 5 bpm each from 30 bpm, 39 bins (30–224). */
    const val BIN0 = 30; const val BIN = 5; const val BINS = 39

    @JvmField val readTypes: Map<String, KClass<out Record>> = linkedMapOf(
        "sleep" to SleepSessionRecord::class, "hr" to HeartRateRecord::class, "rhr" to RestingHeartRateRecord::class,
        "hrv" to HeartRateVariabilityRmssdRecord::class, "resp" to RespiratoryRateRecord::class, "spo2" to OxygenSaturationRecord::class,
        "skin" to SkinTemperatureRecord::class, "btemp" to BodyTemperatureRecord::class, "bp" to BloodPressureRecord::class,
        "vo2" to Vo2MaxRecord::class, "weight" to WeightRecord::class,
    )
    val readPermissions: Set<String> get() = readTypes.values.map { HealthPermission.getReadPermission(it) }.toSet()
    /** What you can add yourself and save to Health Connect too. */
    val writePermissions: Set<String> = setOf(
        HealthPermission.getWritePermission(SleepSessionRecord::class), HealthPermission.getWritePermission(WeightRecord::class),
        HealthPermission.getWritePermission(BloodPressureRecord::class),
    )
    @JvmStatic fun permissionFor(key: String): String? = readTypes[key]?.let { HealthPermission.getReadPermission(it) }

    /** Skin temperature is a newer Health Connect type; it only exists where the Health Connect module supports it. */
    @JvmStatic fun skinSupported(context: Context): Boolean = try {
        HealthConnectClient.getOrCreate(context).features.getFeatureStatus(HealthConnectFeatures.FEATURE_SKIN_TEMPERATURE) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
    } catch (_: Throwable) { false }

    suspend fun read(context: Context, hc: HealthConnectClient, granted: Set<String>) {
        val prefs = context.getSharedPreferences("kadamm_health", Context.MODE_PRIVATE)
        val old = try { JSONObject(prefs.getString("vitals", "{}") ?: "{}") } catch (_: Exception) { JSONObject() }
        val zone = ZoneId.systemDefault(); val today = LocalDate.now(zone)
        val range = TimeRangeFilter.between(today.minusDays(DAYS.toLong()).atStartOfDay(zone).toInstant(), Instant.now())
        val out = JSONObject(); val sources = JSONObject(); val allSources = java.util.TreeSet<String>()
        fun has(k: String) = readTypes[k]?.let { granted.contains(HealthPermission.getReadPermission(it)) } == true
        fun src(k: String, pkg: String) { val a = sources.optJSONArray(k) ?: JSONArray().also { sources.put(k, it) }; var seen = false; for (i in 0 until a.length()) if (a.optString(i) == pkg) seen = true; if (!seen) a.put(pkg); allSources.add(pkg) }
        suspend fun <T : Record> all(type: KClass<T>, r: TimeRangeFilter = range): List<T> {
            val list = mutableListOf<T>(); var token: String? = null
            do { val page = hc.readRecords(ReadRecordsRequest(type, r, pageToken = token)); list.addAll(page.records); token = page.pageToken } while (!token.isNullOrEmpty())
            return list
        }
        fun day(t: Instant) = t.atZone(zone).toLocalDate().toString()
        /** Per day: the median of the day's readings (robust to one odd value), rounded to 0.1. */
        fun medianByDay(values: Map<String, MutableList<Double>>): JSONObject {
            val o = JSONObject(); for ((k, v) in values) { val s = v.sorted(); val m = if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2; o.put(k, Math.round(m * 10) / 10.0) }; return o
        }
        val counts = JSONObject()

        // Sleep: the night's main session (the longest ending that day), its stages, and time actually asleep.
        if (has("sleep")) try {
            val sleep = JSONObject(); val detail = JSONObject()
            for (r in all(SleepSessionRecord::class)) {
                val total = Duration.between(r.startTime, r.endTime).toMinutes(); if (total < 60 || total > 16 * 60) continue
                val key = day(r.endTime); val prev = detail.optJSONObject(key)
                // The same night often arrives twice: from the watch's own app and copied by Google Fit. Keep the
                // watch app's (Fit only when it's clearly longer), else the longer one.
                if (prev != null) {
                    val fit = "com.google.android.apps.fitness"; val prevFit = prev.optString("src") == fit; val thisFit = r.metadata.dataOrigin.packageName == fit
                    if (prevFit != thisFit) { if (thisFit && total < prev.optLong("inBed") + 30) continue; if (!thisFit && total + 30 < prev.optLong("inBed")) continue }
                    else if (prev.optLong("inBed") >= total) continue
                }
                var light = 0L; var deep = 0L; var rem = 0L; var awake = 0L; var asleepUnknown = 0L; val st = JSONArray()
                for (s in r.stages) {
                    val m = Duration.between(s.startTime, s.endTime).toMinutes(); if (m <= 0) continue
                    when (s.stage) {
                        SleepSessionRecord.STAGE_TYPE_LIGHT -> light += m; SleepSessionRecord.STAGE_TYPE_DEEP -> deep += m
                        SleepSessionRecord.STAGE_TYPE_REM -> rem += m
                        SleepSessionRecord.STAGE_TYPE_AWAKE, SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED, SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> awake += m
                        else -> asleepUnknown += m
                    }
                    st.put(JSONArray().put(Duration.between(r.startTime, s.startTime).toMinutes()).put(m).put(s.stage))
                }
                val asleep = if (r.stages.isNotEmpty()) light + deep + rem + asleepUnknown else total
                detail.put(key, JSONObject().put("s", r.startTime.toEpochMilli()).put("e", r.endTime.toEpochMilli()).put("inBed", total).put("asleep", asleep)
                    .put("light", light).put("deep", deep).put("rem", rem).put("awake", awake).put("staged", light + deep + rem > 0).put("st", st).put("src", r.metadata.dataOrigin.packageName))
                sleep.put(key, asleep); src("sleep", r.metadata.dataOrigin.packageName)
            }
            out.put("sleep", sleep).put("sleepDetail", detail); counts.put("sleep", sleep.length())
        } catch (_: Exception) { }

        suspend fun <T : Record> simple(key: String, type: KClass<T>, time: (T) -> Instant, value: (T) -> Double?, ok: (Double) -> Boolean) {
            if (!has(key)) return
            try {
                val byDay = mutableMapOf<String, MutableList<Double>>(); var n = 0
                for (r in all(type)) { val v = value(r) ?: continue; if (!ok(v)) continue; byDay.getOrPut(day(time(r))) { mutableListOf() }.add(v); src(key, r.metadata.dataOrigin.packageName); n++ }
                out.put(key, medianByDay(byDay)); counts.put(key, n)
            } catch (_: Exception) { }
        }
        // HRV and breathing rate are compared night to night: readings taken while asleep (inside that night's sleep
        // window) are used when there are 5 or more; otherwise the day's readings.
        val nights = mutableMapOf<String, LongArray>()
        out.optJSONObject("sleepDetail")?.let { sd -> for (k in sd.keys()) sd.optJSONObject(k)?.let { nights[k] = longArrayOf(it.optLong("s"), it.optLong("e")) } }
        suspend fun <T : Record> nightly(key: String, type: KClass<T>, time: (T) -> Instant, value: (T) -> Double, ok: (Double) -> Boolean) {
            if (!has(key)) return
            try {
                val night = mutableMapOf<String, MutableList<Double>>(); val dayVals = mutableMapOf<String, MutableList<Double>>(); var n = 0
                for (r in all(type)) {
                    val v = value(r); if (!ok(v)) continue; val t = time(r).toEpochMilli(); n++; src(key, r.metadata.dataOrigin.packageName)
                    val nk = nights.entries.firstOrNull { t >= it.value[0] && t <= it.value[1] }?.key
                    if (nk != null) night.getOrPut(nk) { mutableListOf() }.add(v) else dayVals.getOrPut(day(time(r))) { mutableListOf() }.add(v)
                }
                val merged = mutableMapOf<String, MutableList<Double>>(); val asleep = JSONObject()
                for ((k, v) in dayVals) merged[k] = v
                for ((k, v) in night) if (v.size >= 5) { merged[k] = v; asleep.put(k, true) }
                out.put(key, medianByDay(merged)).put(key + "Night", asleep); counts.put(key, n)
            } catch (_: Exception) { }
        }
        nightly("hrv", HeartRateVariabilityRmssdRecord::class, { it.time }, { it.heartRateVariabilityMillis }, { it in 3.0..250.0 })
        nightly("resp", RespiratoryRateRecord::class, { it.time }, { it.rate }, { it in 4.0..40.0 })
        simple("rhr", RestingHeartRateRecord::class, { it.time }, { it.beatsPerMinute.toDouble() }, { it in 30.0..120.0 })
        simple("spo2", OxygenSaturationRecord::class, { it.time }, { it.percentage.value }, { it in 70.0..100.0 })
        simple("btemp", BodyTemperatureRecord::class, { it.time }, { it.temperature.inCelsius }, { it in 34.0..42.0 })
        simple("vo2", Vo2MaxRecord::class, { it.time }, { it.vo2MillilitersPerMinuteKilogram }, { it in 10.0..90.0 })
        simple("weight", WeightRecord::class, { it.time }, { it.weight.inKilograms }, { it in 25.0..300.0 })
        // The lowest SpO2 of each night matters as much as the average.
        if (has("spo2")) try {
            // The night's low: the 5th percentile of the day's readings (10+ needed), so one motion artefact doesn't count.
            val byDay = mutableMapOf<String, MutableList<Double>>(); for (r in all(OxygenSaturationRecord::class)) { val v = r.percentage.value; if (v in 70.0..100.0) byDay.getOrPut(day(r.time)) { mutableListOf() }.add(v) }
            val low = JSONObject(); for ((k, v) in byDay) { val s = v.sorted(); low.put(k, if (s.size >= 10) s[(s.size * .05).toInt()] else s.first()) }
            out.put("spo2Low", low)
        } catch (_: Exception) { }
        // Skin temperature: change from the person's own baseline, in °C (what watches measure overnight).
        if (has("skin") && skinSupported(context)) try {
            val byDay = mutableMapOf<String, MutableList<Double>>(); var n = 0
            for (r in all(SkinTemperatureRecord::class)) {
                if (r.deltas.isEmpty()) continue; val avg = r.deltas.map { it.delta.inCelsius }.average(); if (avg !in -5.0..5.0) continue
                byDay.getOrPut(day(r.endTime)) { mutableListOf() }.add(avg); src("skin", r.metadata.dataOrigin.packageName); n++
            }
            out.put("skin", medianByDay(byDay)); counts.put("skin", n)
        } catch (_: Exception) { }
        if (has("bp")) try {
            val sys = mutableMapOf<String, MutableList<Double>>(); val dia = mutableMapOf<String, MutableList<Double>>(); var n = 0
            for (r in all(BloodPressureRecord::class)) {
                val s = r.systolic.inMillimetersOfMercury; val d = r.diastolic.inMillimetersOfMercury; if (s !in 60.0..250.0 || d !in 30.0..150.0) continue
                val k = day(r.time); sys.getOrPut(k) { mutableListOf() }.add(s); dia.getOrPut(k) { mutableListOf() }.add(d); src("bp", r.metadata.dataOrigin.packageName); n++
            }
            out.put("bpSys", medianByDay(sys)).put("bpDia", medianByDay(dia)); counts.put("bp", n)
        } catch (_: Exception) { }

        // Heart rate all day: minute averages, summarised per day. Past days come from the saved copy.
        if (has("hr")) try {
            val hist = old.optJSONObject("hrHist") ?: JSONObject(); val low = old.optJSONObject("hrRest") ?: JSONObject(); val high = old.optJSONObject("hrMax") ?: JSONObject()
            val stress = old.optJSONObject("stress") ?: JSONObject()
            val rest = restBaseline(out.optJSONObject("rhr"), low); val max = SessionMetrics.maxHeartRate(context)
            var n = 0; var lastBpm = 0L; var lastAt = 0L; var lastSrc = ""; var todayMinutes: IntArray? = null
            for (back in 0 until DAYS) {
                val d = today.minusDays(back.toLong()); val key = d.toString()
                if (back > 1 && hist.has(key) && old.optInt("stressV") == 2) continue
                val from = d.atStartOfDay(zone).toInstant(); val to = if (back == 0) Instant.now() else d.plusDays(1).atStartOfDay(zone).toInstant()
                val sum = DoubleArray(1440); val cnt = IntArray(1440)
                for (r in all(HeartRateRecord::class, TimeRangeFilter.between(from, to))) {
                    for (x in r.samples) {
                        val m = ((x.time.toEpochMilli() - from.toEpochMilli()) / 60000L).toInt(); if (m !in 0..1439 || x.beatsPerMinute !in 30L..230L) continue
                        sum[m] += x.beatsPerMinute.toDouble(); cnt[m]++; n++
                        if (x.time.toEpochMilli() > lastAt) { lastAt = x.time.toEpochMilli(); lastBpm = x.beatsPerMinute; lastSrc = r.metadata.dataOrigin.packageName }
                    }
                    src("hr", r.metadata.dataOrigin.packageName)
                }
                val minute = IntArray(1440) { if (cnt[it] > 0) Math.round(sum[it] / cnt[it]).toInt() else 0 }
                val have = minute.count { it > 0 }
                if (have == 0) { if (back > 1) hist.put(key, JSONArray()); continue }
                if (back == 0) todayMinutes = minute
                val h = IntArray(BINS); var mx = 0
                for (v in minute) if (v > 0) { h[((v - BIN0) / BIN).coerceIn(0, BINS - 1)]++; if (v > mx) mx = v }
                hist.put(key, JSONArray().apply { h.forEach { put(it) } }); high.put(key, mx)
                // Resting heart rate from the readings: the lowest 30-minute average (needs 20+ readings in the window).
                var best = 0.0
                for (s in 0..1410 step 5) { var a = 0.0; var c = 0; for (i in s until s + 30) if (minute[i] > 0) { a += minute[i]; c++ }; if (c >= 20) { val avg = a / c; if (best == 0.0 || avg < best) best = avg } }
                if (best > 0) low.put(key, Math.round(best * 10) / 10.0)
                if (rest > 0 && max > rest) { val sd = stressDay(minute, rest, max, d == today, Trends.hours(context, key)); if (sd != null) stress.put(key, sd) else stress.remove(key) }
            }
            out.put("hrHist", hist).put("hrRest", low).put("hrMax", high).put("stress", stress).put("stressV", 2); counts.put("hr", n)
            if (todayMinutes != null) out.put("hrToday", JSONArray().apply { todayMinutes.forEach { put(it) } }).put("hrTodayDay", today.toString())
            if (lastAt > 0) out.put("hrLast", JSONObject().put("bpm", lastBpm).put("at", lastAt).put("src", lastSrc))
            else old.optJSONObject("hrLast")?.let { out.put("hrLast", it) }
        } catch (_: Exception) { }

        out.put("sources", JSONArray(allSources.toList())).put("by", sources).put("counts", counts).put("at", System.currentTimeMillis())
            .put("granted", JSONArray().apply { readTypes.keys.filter { has(it) }.forEach { put(it) } })
        prefs.edit().putString("vitals", out.toString()).apply()
    }

    /** Resting heart rate baseline: the median of the last 14 days, from the watch's own figure or else the readings. */
    private fun restBaseline(rhr: JSONObject?, low: JSONObject): Double {
        val v = mutableListOf<Double>(); val src = if (rhr != null && rhr.length() >= 3) rhr else low
        val keys = src.keys().asSequence().sorted().toList().takeLast(14); for (k in keys) v.add(src.optDouble(k))
        if (v.isEmpty()) return 0.0; v.sort(); return v[v.size / 2]
    }

    /**
     * Stress from heart rate while still (no stress data type exists in Health Connect, so it's worked out here):
     * minutes between 7 am and 11 pm count when heart rate is under 30 % of your heart-rate reserve, not within 5
     * minutes of activity, and in an hour with under 250 steps (phone or Health Connect). Each counts by how far heart
     * rate sits above resting: up to 8 % of reserve (calm sitting) is 0, 28 % or more is 100. Levels: rest 0–25, low
     * 26–50, medium 51–75, high 76–100. Needs 12+ such readings over 2+ hours.
     */
    private fun stressDay(minute: IntArray, rest: Double, max: Int, today: Boolean, hours: DoubleArray?): JSONObject? {
        val reserve = max - rest; var sum = 0.0; var n = 0; val lv = IntArray(4)
        val end = if (today) minOf(1380, (java.time.LocalTime.now().toSecondOfDay() / 60)) else 1380
        var first = -1; var last = -1
        for (m in 420 until end) {
            val v = minute[m]; if (v <= 0) continue
            val f = (v - rest) / reserve; if (f > .30) continue
            if (hours != null && hours[m / 60] >= 250) continue
            var recent = false; for (k in maxOf(0, m - 5) until m) if (minute[k] > 0 && (minute[k] - rest) / reserve > .30) recent = true
            if (recent) continue
            val s = ((f - .08) / .20 * 100).coerceIn(0.0, 100.0); sum += s; n++; if (first < 0) first = m; last = m
            lv[when { s <= 25 -> 0; s <= 50 -> 1; s <= 75 -> 2; else -> 3 }]++
        }
        if (n < 12 || last - first < 120) return null
        return JSONObject().put("score", Math.round(sum / n)).put("min", n).put("rest", lv[0]).put("low", lv[1]).put("med", lv[2]).put("high", lv[3])
    }

    // ---- Your own entries, also saved to Health Connect when allowed ----
    @JvmStatic fun writeSleep(context: Context, startMs: Long, endMs: Long, done: java.util.function.Consumer<String?>) = write(context, done) { hc ->
        val z = ZoneId.systemDefault().rules
        hc.insertRecords(listOf(SleepSessionRecord(Instant.ofEpochMilli(startMs), z.getOffset(Instant.ofEpochMilli(startMs)), Instant.ofEpochMilli(endMs), z.getOffset(Instant.ofEpochMilli(endMs)), Metadata.manualEntry(), "Added in Kadamm", null, emptyList())))
    }
    @JvmStatic fun writeWeight(context: Context, kg: Double, done: java.util.function.Consumer<String?>) = write(context, done) { hc ->
        val now = Instant.now(); hc.insertRecords(listOf(WeightRecord(now, ZoneId.systemDefault().rules.getOffset(now), Mass.kilograms(kg), Metadata.manualEntry())))
    }
    @JvmStatic fun writeBloodPressure(context: Context, sys: Double, dia: Double, done: java.util.function.Consumer<String?>) = write(context, done) { hc ->
        val now = Instant.now(); hc.insertRecords(listOf(BloodPressureRecord(now, ZoneId.systemDefault().rules.getOffset(now), Metadata.manualEntry(), Pressure.millimetersOfMercury(sys), Pressure.millimetersOfMercury(dia))))
    }
    /** Runs a Health Connect write when its permission is granted; the callback gets null when saved there, else why not. */
    private fun write(context: Context, done: java.util.function.Consumer<String?>, block: suspend (HealthConnectClient) -> Unit) {
        val app = context.applicationContext; val main = android.os.Handler(android.os.Looper.getMainLooper())
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            val result: String? = try {
                if (HealthConnectClient.getSdkStatus(app) != HealthConnectClient.SDK_AVAILABLE) "Health Connect isn't available"
                else { val hc = HealthConnectClient.getOrCreate(app); block(hc); null }
            } catch (e: SecurityException) { "Saved in Kadamm. Allow Kadamm to write this in Health Connect to share it with other apps." }
            catch (e: Exception) { "Saved in Kadamm only (Health Connect didn't accept it)" }
            main.post { done.accept(result) }
        }
    }
}
