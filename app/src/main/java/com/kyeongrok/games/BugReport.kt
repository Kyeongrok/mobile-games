package com.kyeongrok.games

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * 버그 리포트(용의기사2 리메이크처럼) — 설정의 「버그 리포트」가 글 쓰는 창을 띄우고,
 * 쓴 글과 앱이 저절로 붙인 것([collect])을 Cloudflare Worker(`tools/bug-worker` 의 `POST /v1/bug`)로 보낸다.
 *
 * 못 보내면 앱 데이터의 `bug-queue` 에 두었다가 다음에 켤 때 다시 보낸다.
 * 누가 보냈는지는 설치할 때 만든 무작위 번호([AppSettings.installId])뿐이다.
 */
object BugReport {
    private const val URL_BASE = "https://mobile-games.oceanfog1.workers.dev"
    const val MAX_TEXT = 8000
    private const val KEEP = 30

    private fun queue(context: Context) = File(context.filesDir, "bug-queue")
    private fun crashLog(context: Context) = File(context.filesDir, "crash.txt")

    @Volatile private var crashHandlerInstalled = false

    /** 앱이 죽을 때 그 까닭을 crash.txt 에 남긴다(다음 버그 리포트에 끝부분이 붙는다). 원래 처리기는 그대로 부른다. */
    fun installCrashLog(context: Context) {
        if (crashHandlerInstalled) return
        crashHandlerInstalled = true
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val file = crashLog(app)
                if (file.length() > 64 * 1024) file.delete()
                file.appendText("${utcNow()} v${BuildConfig.VERSION_NAME} [${thread.name}]\n${error.stackTraceToString()}\n")
            } catch (_: Exception) {
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** 앱이 저절로 붙이는 것 — 받는 곳의 `bug_reports.context` 에 JSON 으로. */
    fun collect(context: Context, screen: String, lastGame: String?): JSONObject {
        val metrics = context.resources.displayMetrics
        val config = context.resources.configuration
        return JSONObject().apply {
            put("app", BuildConfig.VERSION_NAME)
            put("android", "${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
            put("frame", "${config.screenWidthDp}x${config.screenHeightDp}dp @${metrics.density}x")
            put("theme", "${AppSettings.loadTheme(context).name} · ${AppSettings.loadColor(context).name}")
            put("screen", screen)
            lastGame?.let { put("lastGame", it) }
            crashTail(context)?.let { put("crashLog", it) }
        }
    }

    /** 창 아래에 보일 요약 — 무엇이 함께 가는지. */
    fun summary(c: JSONObject): String =
        "함께 보내는 것: v${c.optString("app")} · Android ${c.optString("android")} · ${c.optString("device")} · 화면 ${c.optString("frame")}" +
            (c.optString("lastGame").takeIf { it.isNotEmpty() }?.let { " · 마지막 게임 $it" } ?: "") +
            (if (c.has("crashLog")) " · 오류 기록 끝" else "") +
            "\n이름 · 계정 · IP 는 보내지 않습니다."

    /** 글 하나를 줄에 세운다. 적어 두지 못하면 false. */
    fun enqueue(context: Context, text: String, c: JSONObject): Boolean {
        val body = text.trim().take(MAX_TEXT)
        if (body.isEmpty()) return false
        return try {
            val dir = queue(context).apply { mkdirs() }
            val json = JSONObject()
                .put("v", 1)
                .put("install", AppSettings.installId(context))
                .put("version", BuildConfig.VERSION_NAME)
                .put("text", body)
                .put("context", c)
            val stamp = SimpleDateFormat("yyyyMMddHHmmssSSS", Locale.US).format(Date())
            File(dir, "$stamp-${UUID.randomUUID().toString().replace("-", "")}.json").writeText(json.toString())
            true
        } catch (_: IOException) {
            false
        }
    }

    /**
     * 줄에 선 리포트를 보낸다 — 켤 때와 보내기를 누른 뒤. 받은 것 · 받는 쪽이 못 쓴다고 한 것(4xx, 429 빼고)은 지우고 나머지는 다음에.
     * 다 보냈으면 true.
     */
    suspend fun upload(context: Context): Boolean = uploading.withLock { withContext(Dispatchers.IO) { uploadQueue(context) } }

    /** 켤 때 보내는 것과 보내기를 누른 것이 겹쳐 같은 글을 두 번 보내지 않게 한 번에 하나씩. */
    private val uploading = Mutex()

    private fun uploadQueue(context: Context): Boolean {
        val files = queue(context).listFiles { f -> f.name.endsWith(".json") }?.sortedBy { it.name } ?: return true
        files.dropLast(KEEP).forEach { it.delete() } // 오래 못 보낸 것이 쌓이지 않게 새 것 30 개만
        for (file in files.takeLast(KEEP)) {
            val code = try {
                post(file.readBytes())
            } catch (_: IOException) {
                return false
            }
            if (code in 200..299 || (code in 400..499 && code != 429)) file.delete()
            if (code == 429 || code >= 500) return false
        }
        return true
    }

    private fun post(body: ByteArray): Int {
        val connection = URL("$URL_BASE/v1/bug").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.doOutput = true
            connection.setRequestProperty("content-type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(body) }
            return connection.responseCode
        } finally {
            connection.disconnect()
        }
    }

    /** 오류 기록(crash.txt)의 끝 — 마지막 100 줄 · 16KB 까지. */
    private fun crashTail(context: Context): String? = try {
        crashLog(context).takeIf { it.exists() }?.readText()?.takeLast(16 * 1024)
            ?.lines()?.takeLast(100)?.joinToString("\n")?.takeIf { it.isNotBlank() }
    } catch (_: IOException) {
        null
    }

    private fun utcNow(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())
}
