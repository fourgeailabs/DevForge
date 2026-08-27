package com.example.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import okhttp3.ResponseBody
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
    fun extractAndInstallApk(context: Context, responseBody: ResponseBody): ApkExtractionResult {
        var tempZipFile: File? = null
        try {
            val downloadDir = File(context.cacheDir, "downloaded_apks").apply { mkdirs() }
            tempZipFile = File(downloadDir, "temp_artifact_${System.currentTimeMillis()}.zip")

            // 1. Stream response body directly to a local temp file
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
                return ApkExtractionResult(false, null, "Downloaded artifact file is empty (0 bytes).")
            }

            // Quick check: If the file starts with XML/HTML tags (API error response)
            if (isTextOrHtmlError(tempZipFile)) {
                val snippet = tempZipFile.readText().take(300)
                Log.e(TAG, "API Error snippet: $snippet")
                return ApkExtractionResult(
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
                installApk(context, directApkTarget)
                return ApkExtractionResult(true, directApkTarget, null)
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
                Log.w(TAG, "ZipFile extraction notice: ${ze.message}")
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
                    Log.w(TAG, "ZipInputStream fallback notice: ${zise.message}")
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
                    Log.w(TAG, "Nested zip extraction notice: ${ne.message}")
                }
            }

            if (extractedApkFile != null && isValidApk(context, extractedApkFile)) {
                installApk(context, extractedApkFile)
                return ApkExtractionResult(true, extractedApkFile, null)
            } else {
                return ApkExtractionResult(
                    false,
                    null,
                    "No valid .apk file could be extracted from the artifact ($fileSizeKb KB). Ensure your GitHub Workflow builds an APK and uploads it via actions/upload-artifact."
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unzip and install APK: ${e.message}", e)
            return ApkExtractionResult(false, null, "APK extraction exception: ${e.message}")
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
    fun saveAndInstallDirectApk(
        context: Context,
        responseBody: ResponseBody,
        fileName: String = "app-release.apk"
    ): ApkExtractionResult {
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
                return ApkExtractionResult(false, null, "Direct download returned an error page: $snippet")
            }

            if (isValidApk(context, targetFile)) {
                installApk(context, targetFile)
                return ApkExtractionResult(true, targetFile, null)
            } else {
                return ApkExtractionResult(false, null, "Downloaded file '$fileName' ($sizeKb KB) is corrupted or not a valid Android package.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save and install direct APK: ${e.message}", e)
            return ApkExtractionResult(false, null, "Download exception: ${e.message}")
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
}
