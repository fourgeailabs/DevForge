package com.example.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import okhttp3.ResponseBody
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

object ApkInstaller {
    private const val TAG = "ApkInstaller"

    /**
     * Extracts an APK file from a downloaded Zip ResponseBody, saves it to internal cache,
     * and triggers the Android Package Installer.
     */
    fun extractAndInstallApk(context: Context, responseBody: ResponseBody): File? {
        try {
            val downloadDir = File(context.cacheDir, "downloaded_apks").apply { mkdirs() }
            val inputStream: InputStream = responseBody.byteStream()
            val zipInputStream = ZipInputStream(inputStream)

            var entry = zipInputStream.nextEntry
            var extractedApkFile: File? = null

            while (entry != null) {
                if (entry.name.endsWith(".apk", ignoreCase = true)) {
                    val apkFile = File(downloadDir, entry.name.substringAfterLast("/"))
                    val outputStream = FileOutputStream(apkFile)
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (zipInputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                    }
                    outputStream.flush()
                    outputStream.close()
                    extractedApkFile = apkFile
                    break
                }
                zipInputStream.closeEntry()
                entry = zipInputStream.nextEntry
            }

            zipInputStream.close()

            if (extractedApkFile != null && extractedApkFile.exists()) {
                installApk(context, extractedApkFile)
                return extractedApkFile
            } else {
                Log.e(TAG, "No .apk file found inside artifact zip.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unzip and install APK: ${e.message}", e)
        }
        return null
    }

    /**
     * Prompts Android system package installer to sideload the specified APK file.
     */
    fun installApk(context: Context, apkFile: File) {
        val authority = "${context.packageName}.provider"
        val apkUri: Uri = FileProvider.getUriForFile(context, authority, apkFile)

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting APK installer intent: ${e.message}", e)
        }
    }
}
