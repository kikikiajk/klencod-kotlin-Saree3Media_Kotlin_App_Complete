package com.karrar.saree3media.downloader

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

enum class DownloadFormat(val isAudio: Boolean) {
    VIDEO_1080P(false),
    VIDEO_720P(false),
    AUDIO_MP3(true)
}

class MediaDownloader(private val context: Context) {

    private var isInitialized = false

    suspend fun initEngine(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!isInitialized) {
                YoutubeDL.getInstance().init(context)
                FFmpeg.getInstance().init(context)
                isInitialized = true
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadMedia(
        url: String,
        customFileName: String?,
        format: DownloadFormat,
        audioFolderUri: Uri?,
        onProgress: (Float, String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            initEngine().getOrThrow()

            // Temp folder inside app cache
            val tempDir = File(context.cacheDir, "ytdl_temp_${System.currentTimeMillis()}")
            if (!tempDir.exists()) tempDir.mkdirs()

            val safeName = customFileName
                ?.trim()
                ?.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                ?.takeIf { it.isNotEmpty() }

            val outputTemplate = if (safeName != null) {
                "${tempDir.absolutePath}/$safeName.%(ext)s"
            } else {
                "${tempDir.absolutePath}/%(title).70s.%(ext)s"
            }

            val request = YoutubeDLRequest(url)
            request.addOption("-o", outputTemplate)
            request.addOption("--no-mtime")
            request.addOption("--no-playlist")

            when (format) {
                DownloadFormat.VIDEO_1080P -> {
                    request.addOption("-f", "bestvideo[height<=1080][ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best")
                    request.addOption("--merge-output-format", "mp4")
                }
                DownloadFormat.VIDEO_720P -> {
                    request.addOption("-f", "bestvideo[height<=720][ext=mp4]+bestaudio[ext=m4a]/best[height<=720]/best")
                    request.addOption("--merge-output-format", "mp4")
                }
                DownloadFormat.AUDIO_MP3 -> {
                    request.addOption("-x")
                    request.addOption("--audio-format", "mp3")
                    request.addOption("--audio-quality", "0")
                }
            }

            YoutubeDL.getInstance().execute(request, null) { progress, _, line ->
                onProgress(progress, line)
            }

            val downloadedFile = tempDir.listFiles()?.firstOrNull { it.isFile }
                ?: throw IllegalStateException("لم يتم العثور على الملف بعد التنزيل")

            val savedLocationMessage = if (format.isAudio) {
                saveAudioToSelectedFolder(downloadedFile, audioFolderUri)
            } else {
                saveVideoToGallery(downloadedFile)
            }

            tempDir.deleteRecursively()
            Result.success(savedLocationMessage)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun saveVideoToGallery(sourceFile: File): String {
        val fileName = sourceFile.name
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Saree3Media")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }

            val resolver = context.contentResolver
            val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val itemUri = resolver.insert(collection, values)
                ?: throw IllegalStateException("تعذر إنشاء ملف الفيديو في الاستوديو")

            resolver.openOutputStream(itemUri)?.use { out ->
                FileInputStream(sourceFile).use { input ->
                    input.copyTo(out)
                }
            }

            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(itemUri, values, null, null)
            return "تم حفظ الفيديو في الاستوديو (Movies/Saree3Media)"
        } else {
            val moviesDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                "Saree3Media"
            )
            if (!moviesDir.exists()) moviesDir.mkdirs()
            val destFile = File(moviesDir, fileName)
            sourceFile.copyTo(destFile, overwrite = true)

            MediaScannerConnection.scanFile(
                context,
                arrayOf(destFile.absolutePath),
                arrayOf("video/mp4"),
                null
            )
            return "تم حفظ الفيديو في الاستوديو (${destFile.name})"
        }
    }

    private fun saveAudioToSelectedFolder(sourceFile: File, treeUri: Uri?): String {
        if (treeUri != null) {
            val pickedDir = DocumentFile.fromTreeUri(context, treeUri)
            if (pickedDir != null && pickedDir.canWrite()) {
                val destDoc = pickedDir.createFile("audio/mpeg", sourceFile.name)
                    ?: throw IllegalStateException("تعذر إنشاء الملف الصوتي في المجلد المختار")
                context.contentResolver.openOutputStream(destDoc.uri)?.use { out ->
                    FileInputStream(sourceFile).use { input ->
                        input.copyTo(out)
                    }
                }
                return "تم حفظ الصوت في المجلد المخصص: ${pickedDir.name ?: "المجلد المختار"}"
            }
        }

        // Fallback to Music/Saree3Media if URI permission was revoked
        val musicDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            "Saree3Media"
        )
        if (!musicDir.exists()) musicDir.mkdirs()
        val fallbackFile = File(musicDir, sourceFile.name)
        sourceFile.copyTo(fallbackFile, overwrite = true)
        MediaScannerConnection.scanFile(context, arrayOf(fallbackFile.absolutePath), arrayOf("audio/mpeg"), null)
        return "تم حفظ الصوت في Music/Saree3Media"
    }
}
