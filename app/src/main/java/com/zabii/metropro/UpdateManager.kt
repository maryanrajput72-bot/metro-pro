package com.zabii.metropro

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class LatestInfo(val versionCode: Int, val versionName: String, val apkUrl: String)

object UpdateManager {

    suspend fun fetchLatest(versionUrl: String): LatestInfo? = withContext(Dispatchers.IO) {
        try {
            val conn = URL(versionUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            val json = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            val obj = JSONObject(json)
            LatestInfo(
                versionCode = obj.getInt("versionCode"),
                versionName = obj.optString("versionName", ""),
                apkUrl = obj.optString("apkUrl", "")
            )
        } catch (e: Exception) {
            null
        }
    }

    suspend fun downloadApk(context: Context, apkUrl: String, onProgress: (Int) -> Unit): File? =
        withContext(Dispatchers.IO) {
            try {
                val conn = URL(apkUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                val total = conn.contentLengthLong
                val outFile = File(context.cacheDir, "update.apk")
                conn.inputStream.use { input ->
                    outFile.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var downloaded = 0L
                        var lastPct = -1
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (total > 0) {
                                val pct = (downloaded * 100 / total).toInt()
                                if (pct != lastPct) {
                                    lastPct = pct
                                    withContext(Dispatchers.Main) { onProgress(pct) }
                                }
                            }
                        }
                    }
                }
                conn.disconnect()
                if (outFile.length() > 0) outFile else null
            } catch (e: Exception) {
                null
            }
        }

    fun canInstall(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /** Metro+ style: PackageInstaller session. Android 12+ pe silent (koi tap nahi),
     *  purane pe system ka apna install dialog (fallback receiver se). */
    fun installWithPackageInstaller(context: Context, apkFile: File): Boolean {
        return try {
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            if (Build.VERSION.SDK_INT >= 31) {
                params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            params.setSize(apkFile.length())
            val sessionId = installer.createSession(params)
            val session = installer.openSession(sessionId)
            session.openWrite("metropro-update", 0, apkFile.length()).use { out ->
                apkFile.inputStream().use { input -> input.copyTo(out) }
                session.fsync(out)
            }
            val intent = Intent(context, InstallStatusReceiver::class.java).apply {
                action = "com.zabii.metropro.INSTALL_STATUS"
            }
            var flags = PendingIntent.FLAG_UPDATE_CURRENT
            if (Build.VERSION.SDK_INT >= 31) flags = flags or PendingIntent.FLAG_MUTABLE
            val pendingIntent = PendingIntent.getBroadcast(context, sessionId, intent, flags)
            session.commit(pendingIntent.intentSender)
            session.close()
            true
        } catch (e: Exception) {
            false
        }
    }
}
