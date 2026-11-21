package com.karrar.saree3media.ui

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import com.karrar.saree3media.R
import com.karrar.saree3media.downloader.DownloadFormat
import com.karrar.saree3media.downloader.MediaDownloader
import com.karrar.saree3media.util.StoragePrefs
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var cardTikTok: LinearLayout
    private lateinit var cardYouTube: LinearLayout
    private lateinit var etMainLinkInput: EditText
    private lateinit var etMainCustomName: EditText
    private lateinit var btnPasteLink: Button
    private lateinit var rgMainFormatType: RadioGroup
    private lateinit var rgMainVideoQuality: RadioGroup
    private lateinit var btnMainDownload: Button
    private lateinit var tvAudioFolderStatus: TextView
    private lateinit var btnChangeAudioFolder: Button

    private lateinit var cardMainProgress: LinearLayout
    private lateinit var mainProgressBar: ProgressBar
    private lateinit var tvMainProgressStatus: TextView

    private lateinit var downloader: MediaDownloader
    private lateinit var storagePrefs: StoragePrefs

    private var selectedPlatform = "ALL"
    private var pendingUrl: String? = null
    private var pendingName: String? = null
    private var pendingFormat: DownloadFormat? = null

    private val requestLegacyStoragePermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    private val pickAudioFolderLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            storagePrefs.saveAudioTreeUri(uri)
            updateAudioFolderLabel()

            val url = pendingUrl
            val format = pendingFormat
            if (url != null && format != null) {
                pendingUrl = null
                pendingFormat = null
                startDownload(url, pendingName, format, uri)
            } else {
                Toast.makeText(this, "تم حفظ مجلد الصوتيات بنجاح", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        downloader = MediaDownloader(applicationContext)
        storagePrefs = StoragePrefs(applicationContext)

        checkLegacyPermissions()
        initViews()
        setupListeners()
        updateAudioFolderLabel()
    }

    private fun checkLegacyPermissions() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestLegacyStoragePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    private fun initViews() {
        cardTikTok = findViewById(R.id.cardTikTok)
        cardYouTube = findViewById(R.id.cardYouTube)
        etMainLinkInput = findViewById(R.id.etMainLinkInput)
        etMainCustomName = findViewById(R.id.etMainCustomName)
        btnPasteLink = findViewById(R.id.btnPasteLink)
        rgMainFormatType = findViewById(R.id.rgMainFormatType)
        rgMainVideoQuality = findViewById(R.id.rgMainVideoQuality)
        btnMainDownload = findViewById(R.id.btnMainDownload)
        tvAudioFolderStatus = findViewById(R.id.tvAudioFolderStatus)
        btnChangeAudioFolder = findViewById(R.id.btnChangeAudioFolder)
        cardMainProgress = findViewById(R.id.cardMainProgress)
        mainProgressBar = findViewById(R.id.mainProgressBar)
        tvMainProgressStatus = findViewById(R.id.tvMainProgressStatus)
    }

    private fun setupListeners() {
        cardTikTok.setOnClickListener {
            selectedPlatform = "TIKTOK"
            cardTikTok.setBackgroundResource(R.drawable.bg_platform_selected)
            cardYouTube.setBackgroundResource(R.drawable.bg_platform_unselected)
            etMainLinkInput.hint = "الصق رابط فيديو تيك توك هنا..."
        }

        cardYouTube.setOnClickListener {
            selectedPlatform = "YOUTUBE"
            cardYouTube.setBackgroundResource(R.drawable.bg_platform_selected)
            cardTikTok.setBackgroundResource(R.drawable.bg_platform_unselected)
            etMainLinkInput.hint = "الصق رابط فيديو يوتيوب هنا..."
        }

        btnPasteLink.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipText = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
            if (!clipText.isNullOrBlank()) {
                val url = extractUrl(clipText) ?: clipText
                etMainLinkInput.setText(url)
                autoSelectPlatformFromUrl(url)
            } else {
                Toast.makeText(this, "الحافظة فارغة", Toast.LENGTH_SHORT).show()
            }
        }

        rgMainFormatType.setOnCheckedChangeListener { _, checkedId ->
            rgMainVideoQuality.visibility = if (checkedId == R.id.rbMainVideo) View.VISIBLE else View.GONE
        }

        btnChangeAudioFolder.setOnClickListener {
            pickAudioFolderLauncher.launch(null)
        }

        btnMainDownload.setOnClickListener {
            val rawInput = etMainLinkInput.text.toString().trim()
            val url = extractUrl(rawInput)
            if (url == null) {
                Toast.makeText(this, "يرجى إدخال رابط صحيح أولاً", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val format = if (rgMainFormatType.checkedRadioButtonId == R.id.rbMainAudio) {
                DownloadFormat.AUDIO_MP3
            } else {
                if (rgMainVideoQuality.checkedRadioButtonId == R.id.rbMainQuality1080) {
                    DownloadFormat.VIDEO_1080P
                } else {
                    DownloadFormat.VIDEO_720P
                }
            }

            val customName = etMainCustomName.text.toString().trim()

            // First time audio download: ask user to pick folder
            if (format.isAudio && storagePrefs.getAudioTreeUri() == null) {
                pendingUrl = url
                pendingName = customName
                pendingFormat = format
                Toast.makeText(this, "لأول مرة: اختر المجلد الذي تريد حفظ الصوتيات فيه", Toast.LENGTH_LONG).show()
                pickAudioFolderLauncher.launch(null)
                return@setOnClickListener
            }

            startDownload(url, customName, format, storagePrefs.getAudioTreeUri())
        }
    }

    private fun autoSelectPlatformFromUrl(url: String) {
        when {
            url.contains("tiktok.com", ignoreCase = true) -> cardTikTok.performClick()
            url.contains("youtube.com", ignoreCase = true) || url.contains("youtu.be", ignoreCase = true) -> cardYouTube.performClick()
        }
    }

    private fun updateAudioFolderLabel() {
        val uri = storagePrefs.getAudioTreeUri()
        if (uri == null) {
            tvAudioFolderStatus.text = "مجلد الصوتيات: لم يتم التحديد بعد (سيُطلب عند أول تنزيل صوت)"
        } else {
            val doc = DocumentFile.fromTreeUri(this, uri)
            val folderName = doc?.name ?: "مجلد مخصص"
            tvAudioFolderStatus.text = "مجلد الصوتيات المحفوظ: $folderName"
        }
    }

    private fun extractUrl(text: String): String? {
        val matcher = Patterns.WEB_URL.matcher(text)
        return if (matcher.find()) matcher.group() else null
    }

    private fun startDownload(url: String, customName: String?, format: DownloadFormat, audioUri: Uri?) {
        cardMainProgress.visibility = View.VISIBLE
        mainProgressBar.isIndeterminate = true
        tvMainProgressStatus.text = "جاري تجهيز المحرك وجلب المعلومات..."
        btnMainDownload.isEnabled = false

        lifecycleScope.launch {
            val result = downloader.downloadMedia(url, customName, format, audioUri) { progress, _ ->
                runOnUiThread {
                    if (progress >= 0f) {
                        mainProgressBar.isIndeterminate = false
                        mainProgressBar.progress = progress.toInt()
                        tvMainProgressStatus.text = "جاري التنزيل... ${progress.toInt()}%"
                    }
                }
            }

            btnMainDownload.isEnabled = true
            result.onSuccess { message ->
                mainProgressBar.progress = 100
                tvMainProgressStatus.text = message
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
            }.onFailure { error ->
                mainProgressBar.isIndeterminate = false
                tvMainProgressStatus.text = "خطأ: ${error.localizedMessage}"
                Toast.makeText(this@MainActivity, "فشل التنزيل، تأكد من الرابط", Toast.LENGTH_LONG).show()
            }
        }
    }
}