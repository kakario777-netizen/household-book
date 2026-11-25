package com.kakario.householdbook.backup

import android.accounts.Account
import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import com.kakario.householdbook.data.Store
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 구글 드라이브 백업.
 *  - 권한: drive.file (이 앱이 만든 파일만 보고 쓸 수 있음 — 드라이브의 다른 파일은 못 봄)
 *  - 내 드라이브 > "가계부 백업" 폴더에 하루 한 파일(가계부_2026-11-25.json), 최근 30개만 남김
 *  - 로그인 토큰은 구글 플레이 서비스가 관리 (앱에 비밀번호·토큰 저장 안 함)
 */
object DriveBackup {
    private const val SCOPE = "https://www.googleapis.com/auth/drive.file"
    private const val FOLDER = "가계부 백업"
    const val KEEP = 30

    data class FileInfo(val id: String, val name: String, val created: String, val size: Long)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("book", Context.MODE_PRIVATE)

    fun account(ctx: Context): String = prefs(ctx).getString("bk_account", "") ?: ""
    fun setAccount(ctx: Context, email: String) =
        prefs(ctx).edit().putString("bk_account", email.trim()).remove("bk_folder").apply()

    fun enabled(ctx: Context) = account(ctx).isNotEmpty()

    /** 자동 백업 시각 (0 = 밤 12시) */
    fun hour(ctx: Context): Int = prefs(ctx).getInt("bk_hour", 0)
    fun setHour(ctx: Context, h: Int) = prefs(ctx).edit().putInt("bk_hour", h).apply()

    fun lastOkTime(ctx: Context): Long = prefs(ctx).getLong("bk_last_ok", 0L)
    fun status(ctx: Context): String = prefs(ctx).getString("bk_status", "") ?: ""
    fun needsReconnect(ctx: Context): Boolean = prefs(ctx).getBoolean("bk_reconnect", false)

    fun saveStatus(ctx: Context, ok: Boolean, msg: String, reconnect: Boolean = false) {
        val e = prefs(ctx).edit().putString("bk_status", msg).putBoolean("bk_reconnect", reconnect)
        if (ok) e.putLong("bk_last_ok", System.currentTimeMillis())
        e.apply()
    }

    fun request(email: String): AuthorizationRequest =
        AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(SCOPE)))
            .setAccount(Account(email, "com.google"))
            .build()

    /** 화면 없이 토큰 받기 (백그라운드용). 다시 동의가 필요하면 null */
    fun tokenSilently(ctx: Context): String? {
        val email = account(ctx)
        if (email.isEmpty()) return null
        val r = Tasks.await(Identity.getAuthorizationClient(ctx).authorize(request(email)), 60, TimeUnit.SECONDS)
        return if (r.hasResolution()) null else r.accessToken
    }

    /* ---------- 백업 / 목록 / 복원 ---------- */

    /** 오늘 날짜 파일로 백업 (같은 날 다시 하면 덮어씀). 돌려주는 값 = 파일 이름 */
    fun backupNow(ctx: Context, token: String): String {
        val name = "가계부_" + SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(Date()) + ".json"
        val body = Store.exportAll(ctx).toString()
        val folder = try { folderId(ctx, token) } catch (e: HttpError) {
            if (e.code == 404) { prefs(ctx).edit().remove("bk_folder").apply(); folderId(ctx, token) } else throw e
        }
        val q = "name='${name}' and '${folder}' in parents and trashed=false"
        val existing = JSONObject(http("GET", "$API/files?q=${enc(q)}&fields=files(id)", token)).getJSONArray("files")
        if (existing.length() > 0) {
            val id = existing.getJSONObject(0).getString("id")
            http("POST", "$UPLOAD/files/$id?uploadType=media", token, body.toByteArray(), "application/json",
                extra = mapOf("X-HTTP-Method-Override" to "PATCH"))
        } else {
            val meta = JSONObject().put("name", name).put("parents", org.json.JSONArray().put(folder)).put("mimeType", "application/json")
            val b = "hb" + System.nanoTime()
            val multipart = "--$b\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$meta\r\n" +
                "--$b\r\nContent-Type: application/json\r\n\r\n$body\r\n--$b--"
            http("POST", "$UPLOAD/files?uploadType=multipart&fields=id", token, multipart.toByteArray(),
                "multipart/related; boundary=$b")
        }
        // 오래된 것 정리 (최근 30개만)
        list(ctx, token).drop(KEEP).forEach { runCatching { http("DELETE", "$API/files/${it.id}", token) } }
        return name
    }

    fun list(ctx: Context, token: String): List<FileInfo> {
        val folder = folderId(ctx, token)
        val q = "'${folder}' in parents and trashed=false"
        val arr = JSONObject(
            http("GET", "$API/files?q=${enc(q)}&orderBy=createdTime%20desc&pageSize=100&fields=files(id,name,createdTime,size)", token)
        ).getJSONArray("files")
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            FileInfo(o.getString("id"), o.getString("name"), o.optString("createdTime"), o.optString("size").toLongOrNull() ?: 0L)
        }
    }

    fun restore(ctx: Context, token: String, id: String) {
        val text = http("GET", "$API/files/$id?alt=media", token)
        Store.importAll(ctx, JSONObject(text))
    }

    /* ---------- 드라이브 API ---------- */

    private const val API = "https://www.googleapis.com/drive/v3"
    private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"

    class HttpError(val code: Int, msg: String) : IOException(msg)

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private fun folderId(ctx: Context, token: String): String {
        prefs(ctx).getString("bk_folder", null)?.let { return it }
        val q = "name='$FOLDER' and mimeType='application/vnd.google-apps.folder' and trashed=false"
        val arr = JSONObject(http("GET", "$API/files?q=${enc(q)}&fields=files(id)", token)).getJSONArray("files")
        val id = if (arr.length() > 0) arr.getJSONObject(0).getString("id") else {
            val meta = JSONObject().put("name", FOLDER).put("mimeType", "application/vnd.google-apps.folder")
            JSONObject(http("POST", "$API/files?fields=id", token, meta.toString().toByteArray(), "application/json")).getString("id")
        }
        prefs(ctx).edit().putString("bk_folder", id).apply()
        return id
    }

    private fun http(
        method: String, url: String, token: String, body: ByteArray? = null, type: String? = null,
        extra: Map<String, String> = emptyMap(),
    ): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 20_000; c.readTimeout = 60_000
            c.setRequestProperty("Authorization", "Bearer $token")
            extra.forEach { (k, v) -> c.setRequestProperty(k, v) }
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", type ?: "application/octet-stream")
                c.setFixedLengthStreamingMode(body.size)
                c.outputStream.use { it.write(body) }
            }
            val code = c.responseCode
            if (code !in 200..299) {
                val err = runCatching { c.errorStream?.readBytes()?.toString(Charsets.UTF_8) }.getOrNull().orEmpty()
                val reason = runCatching { JSONObject(err).getJSONObject("error").optString("message") }.getOrNull()
                throw HttpError(code, "드라이브 오류 $code" + (reason?.let { ": $it" } ?: ""))
            }
            return c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } finally {
            c.disconnect()
        }
    }
}
