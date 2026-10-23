package com.zabii.metropro

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/* ===== MetroOnline — Metro+ wala hi Firebase backend (Firestore REST, anonymous auth) =====
   Data: records/{stationId}_{Shift}_{yyyy-MM-dd} + photos collection — admin panel me nazar ata hai. */
object MetroOnline {
    private const val API_KEY = "AIzaSyCYFYARldvFUPCJ2_w4l4L1_aUKNm_hpIw"
    private const val PROJECT = "metro-plus-5966d"
    private val exec = Executors.newSingleThreadExecutor()

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("metropro_online", Context.MODE_PRIVATE)

    private fun postJson(urlStr: String, body: JSONObject, token: String?): JSONObject? {
        return try {
            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            if (token != null) conn.setRequestProperty("Authorization", "Bearer $token")
            conn.doOutput = true
            conn.connectTimeout = 12000
            conn.readTimeout = 12000
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.readText() ?: ""
            if (code in 200..299) JSONObject(text) else null
        } catch (e: Exception) { null }
    }

    private fun patchDoc(path: String, fields: JSONObject, token: String): Boolean {
        return try {
            val masks = StringBuilder()
            val keys = fields.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                if (masks.isNotEmpty()) masks.append("&")
                masks.append("updateMask.fieldPaths=").append(URLEncoder.encode(k, "UTF-8"))
            }
            val urlStr = "https://firestore.googleapis.com/v1/projects/$PROJECT/databases/(default)/documents/$path?$masks"
            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.requestMethod = "PATCH"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.doOutput = true
            conn.connectTimeout = 12000
            conn.readTimeout = 12000
            conn.outputStream.use { it.write(JSONObject().put("fields", fields).toString().toByteArray(Charsets.UTF_8)) }
            conn.responseCode in 200..299
        } catch (e: Exception) { false }
    }

    private fun token(ctx: Context, forceNew: Boolean = false): String? {
        val p = prefs(ctx)
        val saved = p.getString("fbToken", null)
        val age = System.currentTimeMillis() - p.getLong("fbTokenAt", 0)
        if (!forceNew && saved != null && age < 45 * 60 * 1000) return saved
        val res = postJson(
            "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=$API_KEY",
            JSONObject().put("returnSecureToken", true), null
        ) ?: return null
        val t = res.optString("idToken", "")
        if (t.isBlank()) return null
        p.edit().putString("fbToken", t).putLong("fbTokenAt", System.currentTimeMillis()).apply()
        return t
    }

    // Firestore value helpers
    private fun sv(v: String) = JSONObject().put("stringValue", v)
    private fun dv(v: Double) = JSONObject().put("doubleValue", v)
    private fun iv(v: Long) = JSONObject().put("integerValue", v.toString())
    private fun mv(fields: JSONObject) = JSONObject().put("mapValue", JSONObject().put("fields", fields))
    private fun nv() = JSONObject().put("nullValue", JSONObject.NULL)

    fun stationSlug(station: String) = station.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
    fun shiftShort(ctx: Context) = ProfileStore.get(ctx, "shift", "Morning Shift").replace(" Shift", "")

    /* Aaj ka poora record (TOM + attendance) backend pe bhejo — Metro+ ke push() jaisa */
    fun pushDay(ctx: Context) {
        val app = ctx.applicationContext
        exec.execute {
            try {
                val station = ProfileStore.get(app, "station", "Shaheed-e-Millat")
                val sid = stationSlug(station)
                if (sid.isBlank()) return@execute
                val shift = shiftShort(app)
                val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(Date())
                val dateKeyCompact = SimpleDateFormat("yyyyMMdd", Locale.ENGLISH).format(Date())

                // Local TOM data (opening + closing merged) -> Metro+ ke flat naam
                val f = WaReports.fields(app)
                fun g(k: String) = f[k] ?: ""
                fun num(k: String) = g(k).toDoubleOrNull() ?: 0.0
                val letters = listOf("a", "b", "c", "d", "e")
                val tom = JSONObject()
                tom.put("dateKey", sv(dateKey))
                tom.put("dateDisplay", sv(SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(Date())))
                tom.put("station", sv(station))
                tom.put("shift", sv(shift))
                tom.put("bop_roll_opening", sv(g("bop_op")))
                tom.put("bop_roll_receive", sv(g("bop_recv")))
                tom.put("bop_roll_distribute", sv(g("bop_dist")))
                tom.put("bop_roll_closing", sv(g("bop_cls")))
                tom.put("bop_card_count", sv(g("bop_cardcnt")))
                tom.put("bop_sale_slips", sv(g("bop_slips")))
                tom.put("bop_sale_amount", dv(num("bop_slips") * 30))
                tom.put("tom_roll_opening", sv(g("roll_op")))
                tom.put("tom_roll_receive", sv(g("roll_recv")))
                tom.put("tom_roll_distribute", sv(g("roll_dist")))
                tom.put("tom_roll_closing", sv(g("roll_cls")))
                tom.put("tkm_roll_opening", sv(g("tkm_op")))
                tom.put("tkm_roll_receive", sv(g("tkm_recv")))
                tom.put("tkm_roll_distribute", sv(g("tkm_dist")))
                tom.put("tkm_roll_closing", sv(g("tkm_cls")))
                for (i in 1..5) {
                    val L = letters[i - 1]
                    tom.put("tom${L}_opening", sv(g("tom${i}_op")))
                    tom.put("tom${L}_recharge", sv(g("tom${i}_rchg")))
                    tom.put("tom${L}_closing", sv(g("tom${i}_cls")))
                }

                // Attendance (aaj ki saved)
                var attValue: JSONObject = nv()
                val attRaw = ProfileStore.get(app, "att_$dateKeyCompact", "")
                if (attRaw.isNotBlank()) {
                    try {
                        val src = JSONObject(attRaw)
                        val af = JSONObject()
                        val ks = src.keys()
                        while (ks.hasNext()) {
                            val k = ks.next()
                            af.put(k, sv(if (src.optString(k) == "P") "Present" else "Absent"))
                        }
                        attValue = mv(af)
                    } catch (e: Exception) { }
                }

                val fields = JSONObject()
                fields.put("stationId", sv(sid))
                fields.put("stationName", sv(station))
                fields.put("shift", sv(shift))
                fields.put("date", sv(dateKey))
                fields.put("tom", mv(tom))
                fields.put("attendance", attValue)
                fields.put("byPhone", sv(ProfileStore.get(app, "mobile", "")))
                fields.put("byName", sv(ProfileStore.get(app, "name", "")))
                fields.put("updatedAt", sv(java.time.Instant.now().toString()))
                fields.put("sourceApp", sv("MetroPro"))

                val docId = "${sid}_${shift}_$dateKey"
                var t = token(app) ?: return@execute
                if (!patchDoc("records/$docId", fields, t)) {
                    t = token(app, forceNew = true) ?: return@execute
                    patchDoc("records/$docId", fields, t)
                }
            } catch (e: Exception) { }
        }
    }

    /* GPS photo thumbnail backend pe (photos collection) — Metro+ ke pushPhoto jaisa */
    fun pushPhoto(ctx: Context, bitmap: Bitmap, lat: Double?, lng: Double?) {
        val app = ctx.applicationContext
        exec.execute {
            try {
                val t = token(app) ?: return@execute
                val w = 480
                val h = (bitmap.height * (w.toFloat() / bitmap.width)).toInt().coerceAtLeast(1)
                val thumb = Bitmap.createScaledBitmap(bitmap, w, h, true)
                val baos = ByteArrayOutputStream()
                thumb.compress(Bitmap.CompressFormat.JPEG, 72, baos)
                val dataUrl = "data:image/jpeg;base64," + Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                val station = ProfileStore.get(app, "station", "Shaheed-e-Millat")
                val fields = JSONObject()
                fields.put("stationId", sv(stationSlug(station)))
                fields.put("stationName", sv(station))
                fields.put("shift", sv(shiftShort(app)))
                fields.put("date", sv(SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(Date())))
                fields.put("ts", iv(System.currentTimeMillis()))
                fields.put("lat", if (lat != null) dv(lat) else nv())
                fields.put("lng", if (lng != null) dv(lng) else nv())
                fields.put("address", sv("Islamabad, Pakistan"))
                fields.put("thumb", sv(dataUrl))
                fields.put("byPhone", sv(ProfileStore.get(app, "mobile", "")))
                fields.put("byName", sv(ProfileStore.get(app, "name", "")))
                postJson(
                    "https://firestore.googleapis.com/v1/projects/$PROJECT/databases/(default)/documents/photos",
                    JSONObject().put("fields", fields), t
                )
            } catch (e: Exception) { }
        }
    }
}
