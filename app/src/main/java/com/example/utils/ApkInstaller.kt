package com.example.utils

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import okhttp3.ResponseBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

data class ApkExtractionResult(
    val isSuccess: Boolean,
    val apkFile: File? = null,
    val errorMessage: String? = null
)

object ApkInstaller {
    private const val TAG = "ApkInstaller"

    /**
     * Checks if a file is a valid, parseable Android APK package using PackageManager.
     */
    fun isValidApk(context: Context, file: File): Boolean {
        if (!file.exists() || file.length() < 100) return false
        return try {
            val pm = context.packageManager
            val info = pm.getPackageArchiveInfo(file.absolutePath, 0)
            info != null && !info.packageName.isNullOrBlank()
        } catch (e: Exception) {
            Log.w(TAG, "APK package validation failed for ${file.name}: ${e.message}")
            false
        }
    }

    /**
     * Extracts an APK file from a downloaded Zip ResponseBody, saves it to internal cache,
     * verifies package integrity, and triggers the Android Package Installer.
     */
    suspend fun extractAndInstallApk(context: Context, responseBody: ResponseBody): ApkExtractionResult = withContext(Dispatchers.IO) {
        var tempZipFile: File? = null
        try {
            val downloadDir = File(context.cacheDir, "downloaded_apks").apply { mkdirs() }
            tempZipFile = File(downloadDir, "temp_artifact_${System.currentTimeMillis()}.zip")

            // 1. Stream response body directly to a local temp file on Dispatchers.IO
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

            val fileSizeKb = tempZipFile.length() / 1024
            Log.d(TAG, "Downloaded artifact file size: $fileSizeKb KB (${tempZipFile.length()} bytes)")

            if (!tempZipFile.exists() || tempZipFile.length() == 0L) {
                return@withContext ApkExtractionResult(false, null, "Downloaded artifact file is empty (0 bytes).")
            }

            // Quick check: If the file starts with XML/HTML tags (API error response)
            if (isTextOrHtmlError(tempZipFile)) {
                val snippet = tempZipFile.readText().take(300)
                Log.e(TAG, "API Error snippet: $snippet")
                return@withContext ApkExtractionResult(
                    false,
                    null,
                    "Downloaded file is an API error response, not a ZIP or APK archive. Response: ${snippet.take(120)}"
                )
            }

            // Phase 1: Check if the downloaded file is ALREADY a valid raw APK
            if (isValidApk(context, tempZipFile)) {
                val directApkTarget = File(downloadDir, "app-release.apk")
                if (directApkTarget.exists()) directApkTarget.delete()
                tempZipFile.copyTo(directApkTarget, overwrite = true)
                withContext(Dispatchers.Main) {
                    installApk(context, directApkTarget)
                }
                return@withContext ApkExtractionResult(true, directApkTarget, null)
            }

            var extractedApkFile: File? = null

            // Phase 2: Open as standard ZipFile and search for any .apk entry
            try {
                ZipFile(tempZipFile).use { zip ->
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        Log.d(TAG, "Zip Entry: ${entry.name} (size=${entry.size})")
                        if (!entry.isDirectory && entry.name.endsWith(".apk", ignoreCase = true)) {
                            val apkName = entry.name.substringAfterLast("/").trim()
                            val targetFile = File(downloadDir, if (apkName.isNotBlank()) apkName else "extracted_app.apk")
                            if (targetFile.exists()) targetFile.delete()

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

                            if (isValidApk(context, targetFile)) {
                                extractedApkFile = targetFile
                                Log.d(TAG, "Successfully extracted valid APK: ${targetFile.name} (${targetFile.length()} bytes)")
                                break
                            } else {
                                Log.w(TAG, "Extracted file '${targetFile.name}' failed APK package validation.")
                            }
                        }
                    }
                }
            } catch (ze: Exception) {
                Log.w(TAG, "ZipFile extraction notice: ${ze.localizedMessage ?: ze.message}")
            }

            // Phase 3: Fallback using ZipInputStream (for streamed / alignment-different zip files)
            if (extractedApkFile == null) {
                try {
                    ZipInputStream(FileInputStream(tempZipFile)).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            if (!entry.isDirectory && entry.name.endsWith(".apk", ignoreCase = true)) {
                                val apkName = entry.name.substringAfterLast("/").trim()
                                val targetFile = File(downloadDir, if (apkName.isNotBlank()) apkName else "stream_extracted_app.apk")
                                if (targetFile.exists()) targetFile.delete()

                                FileOutputStream(targetFile).use { fos ->
                                    val buffer = ByteArray(32768)
                                    var bytesRead: Int
                                    while (zis.read(buffer).also { bytesRead = it } != -1) {
                                        fos.write(buffer, 0, bytesRead)
                                    }
                                    fos.flush()
                                }

                                if (isValidApk(context, targetFile)) {
                                    extractedApkFile = targetFile
                                    Log.d(TAG, "ZipInputStream extracted valid APK: ${targetFile.name}")
                                    break
                                }
                            }
                            zis.closeEntry()
                            entry = zis.nextEntry
                        }
                    }
                } catch (zise: Exception) {
                    Log.w(TAG, "ZipInputStream fallback notice: ${zise.localizedMessage ?: zise.message}")
                }
            }

            // Phase 4: Check if there's a nested .zip file inside the zip archive
            if (extractedApkFile == null) {
                try {
                    ZipFile(tempZipFile).use { zip ->
                        val entries = zip.entries()
                        while (entries.hasMoreElements()) {
                            val entry = entries.nextElement()
                            if (!entry.isDirectory && entry.name.endsWith(".zip", ignoreCase = true)) {
                                val nestedZip = File(downloadDir, "nested_temp_${System.currentTimeMillis()}.zip")
                                zip.getInputStream(entry).use { inStream ->
                                    FileOutputStream(nestedZip).use { outStream ->
                                        inStream.copyTo(outStream)
                                    }
                                }

                                // Search inside nested zip
                                ZipFile(nestedZip).use { innerZip ->
                                    val innerEntries = innerZip.entries()
                                    while (innerEntries.hasMoreElements()) {
                                        val innerEntry = innerEntries.nextElement()
                                        if (!innerEntry.isDirectory && innerEntry.name.endsWith(".apk", ignoreCase = true)) {
                                            val apkName = innerEntry.name.substringAfterLast("/")
                                            val targetFile = File(downloadDir, if (apkName.isNotBlank()) apkName else "nested_extracted_app.apk")
                                            innerZip.getInputStream(innerEntry).use { inApk ->
                                                FileOutputStream(targetFile).use { outApk ->
                                                    inApk.copyTo(outApk)
                                                }
                                            }
                                            if (isValidApk(context, targetFile)) {
                                                extractedApkFile = targetFile
                                                break
                                            }
                                        }
                                    }
                                }
                                nestedZip.delete()
                                if (extractedApkFile != null) break
                            }
                        }
                    }
                } catch (ne: Exception) {
                    Log.w(TAG, "Nested zip extraction notice: ${ne.localizedMessage ?: ne.message}")
                }
            }

            val finalApk = extractedApkFile
            if (finalApk != null && isValidApk(context, finalApk)) {
                withContext(Dispatchers.Main) {
                    installApk(context, finalApk)
                }
                return@withContext ApkExtractionResult(true, finalApk, null)
            } else {
                return@withContext ApkExtractionResult(
                    false,
                    null,
                    "No valid .apk file could be extracted from the artifact ($fileSizeKb KB). Ensure your GitHub Workflow builds an APK and uploads it via actions/upload-artifact."
                )
            }
        } catch (e: Exception) {
            val errMessage = e.localizedMessage ?: e.message ?: e::class.java.simpleName
            Log.e(TAG, "Failed to unzip and install APK: $errMessage", e)
            return@withContext ApkExtractionResult(false, null, "APK extraction error ($errMessage)")
        } finally {
            try {
                if (tempZipFile?.exists() == true) {
                    tempZipFile.delete()
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * Saves an unzipped raw APK file directly to cache, verifies validity, and launches the package installer.
     */
    suspend fun saveAndInstallDirectApk(
        context: Context,
        responseBody: ResponseBody,
        fileName: String = "app-release.apk"
    ): ApkExtractionResult = withContext(Dispatchers.IO) {
        try {
            val downloadDir = File(context.cacheDir, "downloaded_apks").apply { mkdirs() }
            val cleanName = if (fileName.endsWith(".apk", ignoreCase = true)) fileName else "$fileName.apk"
            val targetFile = File(downloadDir, cleanName)
            if (targetFile.exists()) targetFile.delete()

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

            val sizeKb = targetFile.length() / 1024
            Log.d(TAG, "Saved direct APK size: $sizeKb KB")

            if (isTextOrHtmlError(targetFile)) {
                val snippet = targetFile.readText().take(200)
                return@withContext ApkExtractionResult(false, null, "Direct download returned an error page: $snippet")
            }

            if (isValidApk(context, targetFile)) {
                withContext(Dispatchers.Main) {
                    installApk(context, targetFile)
                }
                return@withContext ApkExtractionResult(true, targetFile, null)
            } else {
                return@withContext ApkExtractionResult(false, null, "Downloaded file '$fileName' ($sizeKb KB) is corrupted or not a valid Android package.")
            }
        } catch (e: Exception) {
            val errMessage = e.localizedMessage ?: e.message ?: e::class.java.simpleName
            Log.e(TAG, "Failed to save and install direct APK: $errMessage", e)
            return@withContext ApkExtractionResult(false, null, "Download error ($errMessage)")
        }
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

    private fun isTextOrHtmlError(file: File): Boolean {
        if (!file.exists() || file.length() > 5000) return false
        return try {
            val text = file.readText().trim()
            text.startsWith("<") || text.startsWith("{\"") || text.contains("Error")
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Saves any downloaded file (Windows, Mac, Linux, iOS, Android, Zip, etc.) directly into
     * the device's public Downloads directory and registers it with Android's DownloadManager.
     */
    suspend fun saveToPublicDownloadsFolder(
        context: Context,
        responseBody: ResponseBody,
        fileName: String
    ): File = withContext(Dispatchers.IO) {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs()
        }
        val targetFile = File(downloadsDir, fileName)
        if (targetFile.exists()) {
            targetFile.delete()
        }

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

        try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            downloadManager?.addCompletedDownload(
                fileName,
                "Downloaded from GitHub via DevForge Pro",
                true,
                getMimeTypeForFile(fileName),
                targetFile.absolutePath,
                targetFile.length(),
                true
            )
        } catch (e: Exception) {
            Log.w(TAG, "DownloadManager notification notice: ${e.message}")
        }

        return@withContext targetFile
    }

    private fun getMimeTypeForFile(fileName: String): String {
        val lower = fileName.lowercase()
        return when {
            lower.endsWith(".apk") -> "application/vnd.android.package-archive"
            lower.endsWith(".exe") -> "application/x-msdownload"
            lower.endsWith(".msi") -> "application/x-msi"
            lower.endsWith(".dmg") -> "application/x-apple-diskimage"
            lower.endsWith(".pkg") -> "application/x-newton-compatible-pkg"
            lower.endsWith(".ipa") -> "application/octet-stream"
            lower.endsWith(".deb") -> "application/vnd.debian.binary-package"
            lower.endsWith(".rpm") -> "application/x-rpm"
            lower.endsWith(".appimage") -> "application/x-executable"
            lower.endsWith(".zip") -> "application/zip"
            lower.endsWith(".tar.gz") || lower.endsWith(".tgz") -> "application/gzip"
            else -> "application/octet-stream"
        }
    }
}
