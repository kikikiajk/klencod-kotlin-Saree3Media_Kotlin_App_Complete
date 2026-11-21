package com.karrar.saree3media.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.karrar.saree3media.R
import com.karrar.saree3media.downloader.DownloadFormat
import com.karrar.saree3media.downloader.MediaDownloader
import com.karrar.saree3media.util.StoragePrefs
import kotlinx.coroutines.launch

class ShareReceiverActivity : AppCompatActivity() {

    private lateinit var downloader: MediaDownloader
    private lateinit var storagePrefs: StoragePrefs

    private lateinit var overlayProgressCard: LinearLayout
    private lateinit var overlayProgressBar: ProgressBar
    private lateinit var tvOverlayStatus: TextView

    private var pendingUrl: String? = null
    private var pendingName: String? = null
    private var pendingFormat: DownloadFormat? = null

    private val pickAudioFolderLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            storagePrefs.saveAudioTreeUri(uri)
            val url = pendingUrl
            val format = pendingFormat
            if (url != null && format != null) {
                executeDownload(url, pendingName, format, uri)
            }
        } else {
            Toast.makeText(this, "تم إلغاء اختيار مجلد الصوتيات", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_share_overlay)

        downloader = MediaDownloader(applicationContext)
        storagePrefs = StoragePrefs(applicationContext)

        overlayProgressCard = findViewById(R.id.overlayProgressCard)
        overlayProgressBar = findViewById(R.id.overlayProgressBar)
        tvOverlayStatus = findViewById(R.id.tvOverlayStatus)

        val sharedText = if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)
        } else null

        val extractedUrl = sharedText?.let { extractUrl(it) }
        if (extractedUrl == null) {
            Toast.makeText(this, "لم يتم العثور على رابط صالح", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val popup = DownloadPopupDialog(
            mediaUrl = extractedUrl,
            onConfirmDownload = { url, customName, format ->
                prepareAndDownload(url, customName, format)
            },
            onDismissed = {
                finish()
            }
        )
        popup.isCancelable = false
        popup.show(supportFragmentManager, "ShareDownloadPopup")
    }

    private fun extractUrl(text: String): String? {
        val matcher = Patterns.WEB_URL.matcher(text)
        return if (matcher.find()) matcher.group() else null
    }

    private fun prepareAndDownload(url: String, customName: String, format: DownloadFormat) {
        if (format.isAudio && storagePrefs.getAudioTreeUri() == null) {
            pendingUrl = url
            pendingName = customName
            pendingFormat = format
            Toast.makeText(this, "لأول مرة: اختر مجلد حفظ الصوتيات", Toast.LENGTH_LONG).show()
            pickAudioFolderLauncher.launch(null)
            return
        }
        executeDownload(url, customName, format, storagePrefs.getAudioTreeUri())
    }

    private fun executeDownload(url: String, customName: String?, format: DownloadFormat, audioUri: Uri?) {
        overlayProgressCard.visibility = View.VISIBLE
        overlayProgressBar.isIndeterminate = true
        tvOverlayStatus.text = "جاري تجهيز التنزيل..."

        lifecycleScope.launch {
            val result = downloader.downloadMedia(url, customName, format, audioUri) { progress, _ ->
                runOnUiThread {
                    if (progress >= 0f) {
                        overlayProgressBar.isIndeterminate = false
                        overlayProgressBar.progress = progress.toInt()
                        tvOverlayStatus.text = "جاري التنزيل... ${progress.toInt()}%"
                    }
                }
            }

            result.onSuccess { msg ->
                Toast.makeText(this@ShareReceiverActivity, msg, Toast.LENGTH_LONG).show()
                finish()
            }.onFailure { err ->
                Toast.makeText(this@ShareReceiverActivity, "فشل التنزيل: ${err.localizedMessage}", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }
}
