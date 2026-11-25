package com.wein.fasttrack.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class GitHubReleaseInfo(
    val tagName: String,
    val versionName: String,
    val releaseName: String,
    val releaseNotes: String,
    val htmlUrl: String,
    val apkDownloadUrl: String?,
    val apkFileName: String?,
    val apkSizeBytes: Long
)

sealed interface UpdateCheckResult {
    data class UpdateAvailable(
        val currentVersion: String,
        val release: GitHubReleaseInfo
    ) : UpdateCheckResult

    data class UpToDate(
        val currentVersion: String
    ) : UpdateCheckResult
}

object AppUpdateManager {

    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 10_000
    private const val MAX_REDIRECTS = 5
    private const val UPDATES_DIR_NAME = "updates"
    private const val USER_AGENT = "FastTrack-Android"

    /**
     * Parses a semantic-like version string (e.g. "v3.0.0-beta.1") into integer parts.
     * Strips leading 'v'/'V' and any pre-release ('-') or build ('+') suffix.
     */
    fun parseVersionParts(raw: String): List<Int>? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val withoutPrefix = if (trimmed.startsWith("v", ignoreCase = true)) {
            trimmed.substring(1)
        } else {
            trimmed
        }
        val core = withoutPrefix
            .substringBefore('-')
            .substringBefore('+')
            .trim()
        if (core.isEmpty()) return null

        val segments = core.split('.')
        val parts = ArrayList<Int>(segments.size)
        for (segment in segments) {
            if (segment.isEmpty() || !segment.all { it.isDigit() }) {
                return null
            }
            val parsed = segment.toIntOrNull() ?: return null
            parts.add(parsed)
        }
        return parts
    }

    /**
     * Returns true strictly when [candidateVersion] represents a higher numeric version
     * than [currentVersion].
     */
    fun isNewerVersion(currentVersion: String, candidateVersion: String): Boolean {
        val currentParts = parseVersionParts(currentVersion) ?: return false
        val candidateParts = parseVersionParts(candidateVersion) ?: return false
        val maxLen = maxOf(currentParts.size, candidateParts.size)
        for (i in 0 until maxLen) {
            val currentVal = currentParts.getOrElse(i) { 0 }
            val candidateVal = candidateParts.getOrElse(i) { 0 }
            if (candidateVal != currentVal) {
                return candidateVal > currentVal
            }
        }
        return false
    }

    /**
     * Parses a GitHub `/releases/latest` JSON payload into [GitHubReleaseInfo].
     * Uses a self-contained RFC-8259 parser so JVM unit tests run without Android stubs.
     */
    fun parseLatestReleaseJson(jsonText: String): GitHubReleaseInfo? {
        val root = SimpleJsonParser.parseObjectOrNull(jsonText) ?: return null
        val tagName = (root["tag_name"] as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val versionName = if (tagName.startsWith("v", ignoreCase = true)) {
            tagName.substring(1).trim().ifEmpty { tagName }
        } else {
            tagName
        }
        val releaseName = (root["name"] as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: tagName
        val releaseNotes = (root["body"] as? String) ?: ""
        val htmlUrl = (root["html_url"] as? String)?.trim() ?: ""

        val assets = root["assets"] as? List<*>
        val apkAssets = assets
            ?.mapNotNull { it as? Map<*, *> }
            ?.filter { asset ->
                val name = asset["name"] as? String
                name != null && name.trim().endsWith(".apk", ignoreCase = true)
            }
            .orEmpty()

        val preferredAsset = apkAssets.firstOrNull { asset ->
            (asset["browser_download_url"] as? String)?.isNotBlank() == true
        } ?: apkAssets.firstOrNull()

        val apkDownloadUrl = (preferredAsset?.get("browser_download_url") as? String)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        val apkFileName = (preferredAsset?.get("name") as? String)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        val apkSizeBytes = (preferredAsset?.get("size") as? Number)
            ?.toLong()
            ?.coerceAtLeast(0L)
            ?: 0L

        return GitHubReleaseInfo(
            tagName = tagName,
            versionName = versionName,
            releaseName = releaseName,
            releaseNotes = releaseNotes,
            htmlUrl = htmlUrl,
            apkDownloadUrl = apkDownloadUrl,
            apkFileName = apkFileName,
            apkSizeBytes = apkSizeBytes
        )
    }

    suspend fun checkForUpdate(
        currentVersion: String,
        repoOwner: String = "Weinssy",
        repoName: String = "Fast-Track"
    ): Result<UpdateCheckResult> = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val endpoint = "https://api.github.com/repos/$repoOwner/$repoName/releases/latest"
            connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", USER_AGENT)
            }

            val code = connection.responseCode
            if (code !in 200..299) {
                val errorText = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                val detail = errorText.takeIf { it.isNotBlank() }?.let { " ($code)" } ?: " (HTTP $code)"
                return@withContext Result.failure(
                    IllegalStateException("Gagal memeriksa pembaruan$detail")
                )
            }

            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val release = parseLatestReleaseJson(body)
                ?: return@withContext Result.failure(
                    IllegalStateException("Respons rilis GitHub tidak valid")
                )

            val result = if (isNewerVersion(currentVersion, release.versionName)) {
                UpdateCheckResult.UpdateAvailable(currentVersion = currentVersion, release = release)
            } else {
                UpdateCheckResult.UpToDate(currentVersion = currentVersion)
            }
            Result.success(result)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            connection?.disconnect()
        }
    }

    suspend fun downloadApk(
        context: Context,
        downloadUrl: String,
        fileName: String,
        onProgress: (Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val updatesDir = File(context.cacheDir, UPDATES_DIR_NAME)
        if (!updatesDir.exists()) {
            updatesDir.mkdirs()
        }
        cleanupOldUpdates(context)

        val safeName = sanitizeApkFileName(fileName)
        val targetFile = File(updatesDir, safeName)
        val tempFile = File(updatesDir, "$safeName.part")

        var connection: HttpURLConnection? = null
        try {
            connection = openFollowingRedirects(downloadUrl)
            val code = connection.responseCode
            if (code !in 200..299) {
                return@withContext Result.failure(
                    IllegalStateException("Gagal mengunduh pembaruan (HTTP $code)")
                )
            }

            val totalBytes = connection.contentLengthLong.takeIf { it > 0L }
                ?: connection.getHeaderField("Content-Length")?.toLongOrNull()?.takeIf { it > 0L }
                ?: -1L

            var lastReportedProgress = -1
            if (totalBytes > 0L) {
                onProgress(0)
                lastReportedProgress = 0
            }

            BufferedInputStream(connection.inputStream).use { input ->
                BufferedOutputStream(FileOutputStream(tempFile)).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloaded = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (totalBytes > 0L) {
                            val percent = ((downloaded * 100L) / totalBytes).toInt().coerceIn(0, 100)
                            if (percent != lastReportedProgress) {
                                lastReportedProgress = percent
                                onProgress(percent)
                            }
                        }
                    }
                    output.flush()
                }
            }

            if (targetFile.exists()) {
                targetFile.delete()
            }
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }

            if (!targetFile.exists() || targetFile.length() <= 0L) {
                targetFile.delete()
                return@withContext Result.failure(IllegalStateException("Berkas APK kosong atau rusak"))
            }

            if (totalBytes > 0L && lastReportedProgress < 100) {
                onProgress(100)
            }
            Result.success(targetFile)
        } catch (e: CancellationException) {
            tempFile.delete()
            throw e
        } catch (e: Exception) {
            tempFile.delete()
            Result.failure(e)
        } finally {
            connection?.disconnect()
        }
    }

    fun cleanupOldUpdates(context: Context) {
        val updatesDir = File(context.cacheDir, UPDATES_DIR_NAME)
        if (!updatesDir.exists()) return
        updatesDir.listFiles()?.forEach { file ->
            file.delete()
        }
    }

    fun canInstallPackages(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openInstallUnknownAppsSettings(context: Context) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            )
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun installApk(context: Context, apkFile: File): Boolean {
        return try {
            if (!apkFile.exists()) return false
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun openFollowingRedirects(initialUrl: String): HttpURLConnection {
        var currentUrl = initialUrl
        repeat(MAX_REDIRECTS + 1) {
            val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/octet-stream")
            }
            val code = conn.responseCode
            val isRedirect = code == HttpURLConnection.HTTP_MOVED_PERM ||
                code == HttpURLConnection.HTTP_MOVED_TEMP ||
                code == HttpURLConnection.HTTP_SEE_OTHER ||
                code == 307 ||
                code == 308
            if (!isRedirect) {
                return conn
            }
            val location = conn.getHeaderField("Location")
            conn.disconnect()
            if (location.isNullOrBlank()) {
                throw IllegalStateException("Redirect kosong saat mengunduh APK")
            }
            currentUrl = URL(URL(currentUrl), location).toString()
        }
        throw IllegalStateException("Terlalu banyak pengalihan saat mengunduh APK")
    }

    private fun sanitizeApkFileName(rawName: String): String {
        val base = rawName
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .trim()
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .ifBlank { "fasttrack-update.apk" }
        return if (base.endsWith(".apk", ignoreCase = true)) base else "$base.apk"
    }
}

private class SimpleJsonParser(private val input: String) {
    private var index = 0

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun parseObjectOrNull(text: String): Map<String, Any?>? {
            if (text.isBlank()) return null
            return try {
                val parser = SimpleJsonParser(text)
                val value = parser.parseValue()
                parser.skipWhitespace()
                if (parser.index != parser.input.length) return null
                value as? Map<String, Any?>
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun skipWhitespace() {
        while (index < input.length && input[index].isJsonWhitespace()) {
            index++
        }
    }

    private fun Char.isJsonWhitespace(): Boolean =
        this == ' ' || this == '\n' || this == '\r' || this == '\t'

    private fun parseValue(): Any? {
        skipWhitespace()
        if (index >= input.length) throw IllegalStateException("Unexpected end of JSON")
        return when (val ch = input[index]) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> parseString()
            't' -> parseLiteral("true", true)
            'f' -> parseLiteral("false", false)
            'n' -> parseLiteral("null", null)
            '-', in '0'..'9' -> parseNumber()
            else -> throw IllegalStateException("Unexpected char '$ch' at $index")
        }
    }

    private fun parseObject(): Map<String, Any?> {
        expect('{')
        skipWhitespace()
        val result = LinkedHashMap<String, Any?>()
        if (peek() == '}') {
            index++
            return result
        }
        while (true) {
            skipWhitespace()
            val key = parseString()
            skipWhitespace()
            expect(':')
            val value = parseValue()
            result[key] = value
            skipWhitespace()
            when (peek()) {
                ',' -> index++
                '}' -> {
                    index++
                    return result
                }
                else -> throw IllegalStateException("Expected ',' or '}' at $index")
            }
        }
    }

    private fun parseArray(): List<Any?> {
        expect('[')
        skipWhitespace()
        val result = ArrayList<Any?>()
        if (peek() == ']') {
            index++
            return result
        }
        while (true) {
            val value = parseValue()
            result.add(value)
            skipWhitespace()
            when (peek()) {
                ',' -> index++
                ']' -> {
                    index++
                    return result
                }
                else -> throw IllegalStateException("Expected ',' or ']' at $index")
            }
        }
    }

    private fun parseString(): String {
        expect('"')
        val sb = StringBuilder()
        while (index < input.length) {
            val ch = input[index++]
            when (ch) {
                '"' -> return sb.toString()
                '\\' -> {
                    if (index >= input.length) throw IllegalStateException("Unterminated escape")
                    when (val esc = input[index++]) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            if (index + 4 > input.length) {
                                throw IllegalStateException("Invalid unicode escape")
                            }
                            val hex = input.substring(index, index + 4)
                            sb.append(hex.toInt(16).toChar())
                            index += 4
                        }
                        else -> throw IllegalStateException("Invalid escape '\\$esc'")
                    }
                }
                else -> {
                    if (ch.code < 0x20) throw IllegalStateException("Unescaped control char")
                    sb.append(ch)
                }
            }
        }
        throw IllegalStateException("Unterminated string")
    }

    private fun parseNumber(): Number {
        val start = index
        if (input[index] == '-') index++
        if (index >= input.length) throw IllegalStateException("Invalid number")
        if (input[index] == '0') {
            index++
        } else if (input[index] in '1'..'9') {
            while (index < input.length && input[index] in '0'..'9') index++
        } else {
            throw IllegalStateException("Invalid number")
        }

        var isFloat = false
        if (index < input.length && input[index] == '.') {
            isFloat = true
            index++
            if (index >= input.length || input[index] !in '0'..'9') {
                throw IllegalStateException("Invalid fraction")
            }
            while (index < input.length && input[index] in '0'..'9') index++
        }
        if (index < input.length && (input[index] == 'e' || input[index] == 'E')) {
            isFloat = true
            index++
            if (index < input.length && (input[index] == '+' || input[index] == '-')) index++
            if (index >= input.length || input[index] !in '0'..'9') {
                throw IllegalStateException("Invalid exponent")
            }
            while (index < input.length && input[index] in '0'..'9') index++
        }
        val raw = input.substring(start, index)
        return if (isFloat) {
            raw.toDouble()
        } else {
            raw.toLongOrNull() ?: raw.toDouble()
        }
    }

    private fun parseLiteral(expected: String, result: Any?): Any? {
        if (!input.regionMatches(index, expected, 0, expected.length)) {
            throw IllegalStateException("Invalid literal at $index")
        }
        index += expected.length
        return result
    }

    private fun peek(): Char {
        if (index >= input.length) throw IllegalStateException("Unexpected end of JSON")
        return input[index]
    }

    private fun expect(expected: Char) {
        if (index >= input.length || input[index] != expected) {
            throw IllegalStateException("Expected '$expected' at $index")
        }
        index++
    }
}
