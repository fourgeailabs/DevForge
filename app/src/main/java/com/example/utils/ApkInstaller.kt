package com.example.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import okhttp3.ResponseBody
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipFile

object ApkInstaller {
    private const val TAG = "ApkInstaller"

    /**
     * Extracts an APK file from a downloaded Zip ResponseBody, saves it to internal cache,
     * and triggers the Android Package Installer.
     */
    fun extractAndInstallApk(context: Context, responseBody: ResponseBody): File? {
        var tempZipFile: File? = null
        try {
            val downloadDir = File(context.cacheDir, "downloaded_apks").apply { mkdirs() }
            tempZipFile = File(downloadDir, "temp_artifact_${System.currentTimeMillis()}.zip")

            // 1. Stream response body directly to a local temp ZIP file
            responseBody.byteStream().use { inputStream ->
                FileOutputStream(tempZipFile).use { outputStream ->
                    val buffer = ByteArray(32768)
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                    }
                    outputStream.flush()
                }
            }

            Log.d(TAG, "Downloaded artifact zip size: ${tempZipFile.length()} bytes")

            if (!tempZipFile.exists() || tempZipFile.length() == 0L) {
                Log.e(TAG, "Downloaded artifact zip file is empty.")
                return null
            }

            var extractedApkFile: File? = null

            // 2. Open zip file and search for any .apk entry
            try {
                ZipFile(tempZipFile).use { zip ->
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        Log.d(TAG, "Zip Entry found: ${entry.name}")
                        if (!entry.isDirectory && entry.name.endsWith(".apk", ignoreCase = true)) {
                            val apkName = entry.name.substringAfterLast("/")
                            val targetFile = File(downloadDir, if (apkName.isNotBlank()) apkName else "app-release.apk")
                            
                            zip.getInputStream(entry).use { entryInput ->
                                FileOutputStream(targetFile).use { entryOutput ->
                                    val buffer = ByteArray(32768)
                                    var bytesRead: Int
                                    while (entryInput.read(buffer).also { bytesRead = it } != -1) {
                                        entryOutput.write(buffer, 0, bytesRead)
                                    }
                                    entryOutput.flush()
                                }
                            }
                            extractedApkFile = targetFile
                            Log.d(TAG, "Successfully extracted APK: ${targetFile.absolutePath} (${targetFile.length()} bytes)")
                            break
                        }
                    }
                }
            } catch (ze: Exception) {
                Log.w(TAG, "Not a standard ZIP archive or zip extraction failed: ${ze.message}")
            }

            // 3. Fallback: Check if downloaded file is actually an APK directly
            if (extractedApkFile == null || !extractedApkFile.exists()) {
                val directApk = File(downloadDir, "downloaded_app_${System.currentTimeMillis()}.apk")
                if (tempZipFile.renameTo(directApk)) {
                    extractedApkFile = directApk
                    Log.d(TAG, "Renamed direct download to APK: ${directApk.absolutePath}")
                }
            } else {
                // Cleanup temp zip
                tempZipFile.delete()
            }

            if (extractedApkFile != null && extractedApkFile.exists() && extractedApkFile.length() > 0) {
                installApk(context, extractedApkFile)
                return extractedApkFile
            } else {
                Log.e(TAG, "No valid .apk file found inside artifact.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unzip and install APK: ${e.message}", e)
        } finally {
            try {
                if (tempZipFile?.exists() == true) {
                    tempZipFile.delete()
                }
            } catch (_: Exception) {}
        }
        return null
    }

    /**
     * Streams and saves an unzipped raw APK file directly to cache and launches the package installer.
     */
    fun saveAndInstallDirectApk(context: Context, responseBody: ResponseBody, fileName: String = "app-release.apk"): File? {
        try {
            val downloadDir = File(context.cacheDir, "downloaded_apks").apply { mkdirs() }
            val cleanName = if (fileName.endsWith(".apk", ignoreCase = true)) fileName else "$fileName.apk"
            val targetFile = File(downloadDir, cleanName)

            responseBody.byteStream().use { inputStream ->
                FileOutputStream(targetFile).use { outputStream ->
                    val buffer = ByteArray(32768)
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                    }
                    outputStream.flush()
                }
            }

            Log.d(TAG, "Saved direct APK size: ${targetFile.length()} bytes")

            if (targetFile.exists() && targetFile.length() > 0) {
                installApk(context, targetFile)
                return targetFile
            } else {
                Log.e(TAG, "Saved direct APK file is missing or empty.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save and install direct APK: ${e.message}", e)
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

