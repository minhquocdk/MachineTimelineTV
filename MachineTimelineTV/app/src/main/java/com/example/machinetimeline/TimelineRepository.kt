package com.example.machinetimeline

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

const val BASE_URL = "http://192.168.3.251:8000"

/** Parse an toàn: null/rỗng/sai định dạng -> null (không crash). */
fun parseTimeMs(s: String?): Long? {
    if (s.isNullOrBlank() || s == "null") return null
    return runCatching { OffsetDateTime.parse(s).toInstant().toEpochMilli() }
        .recoverCatching {
            LocalDateTime.parse(s.replace(' ', 'T')).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.getOrNull()
}

private fun JSONObject.str(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

private fun JSONObject.time(key: String): Long? = parseTimeMs(str(key))

class TimelineRepository(
    private val baseUrl: String = BASE_URL,
    private val client: OkHttpClient = OkHttpClient()
) {
    private suspend fun get(path: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(baseUrl + path).build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code} $path")
            r.body?.string() ?: throw IOException("Empty body $path")
        }
    }

    private fun <T : Any> parseList(json: String, map: (JSONObject) -> T?): List<T> {
        val arr = JSONArray(json)
        val out = ArrayList<T>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            runCatching { map(o) }.getOrNull()?.let(out::add)
        }
        return out
    }

    suspend fun serverTimeMs(): Long? =
        runCatching { JSONObject(get("/time")).getLong("timestamp") }.getOrNull()

    suspend fun statuses(from: String, to: String): List<StatusRecord> =
        parseList(get("/lsm/?from_date=$from&to_date=$to")) { o ->
            val m = o.str("MaSo"); val s = o.time("GioBD")
            if (m == null || s == null) null
            else StatusRecord(m, s, o.time("GioKT"), o.str("status") ?: "Off")
        }

    suspend fun work(from: String, to: String): List<WorkRecord> =
        parseList(get("/congviec/?from_date=$from&to_date=$to")) { o ->
            val m = o.str("May"); val s = o.time("GioBD")
            if (m == null || s == null) null
            else WorkRecord(m, s, o.time("GioKT"), o.str("NoiDung") ?: "N/A", o.str("NhanVien") ?: "N/A")
        }

    suspend fun plans(from: String, to: String): List<PlanRecord> =
        parseList(get("/kehoachXCK/?from_date=$from&to_date=$to")) { o ->
            val m = o.str("may"); val s = o.time("GioBD")
            if (m == null || s == null) null
            else PlanRecord(m, s, o.time("GioKT"), o.str("ChiTiet") ?: "N/A", o.optInt("PhanLoaiKeHoach", 1))
        }

    suspend fun teams(): List<MachineTeam> =
        parseList(get("/mays")) { o ->
            val m = o.str("May"); val t = o.str("ToSX")
            if (m == null || t == null) null else MachineTeam(m, t)
        }
}
