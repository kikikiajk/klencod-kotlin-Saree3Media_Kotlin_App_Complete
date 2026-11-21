package com.karrar.saree3media.ui

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.karrar.saree3media.R
import com.karrar.saree3media.downloader.DownloadFormat

class DownloadPopupDialog(
    private val mediaUrl: String,
    private val onConfirmDownload: (url: String, customName: String, format: DownloadFormat) -> Unit,
    private val onDismissed: () -> Unit = {}
) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_download_popup, container, false)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvPlatformBadge = view.findViewById<TextView>(R.id.tvPlatformBadge)
        val tvPopupUrl = view.findViewById<TextView>(R.id.tvPopupUrl)
        val etCustomFileName = view.findViewById<EditText>(R.id.etCustomFileName)
        val rgFormatType = view.findViewById<RadioGroup>(R.id.rgFormatType)
        val rgVideoQuality = view.findViewById<RadioGroup>(R.id.rgVideoQuality)
        val btnCancelPopup = view.findViewById<Button>(R.id.btnCancelPopup)
        val btnStartPopupDownload = view.findViewById<Button>(R.id.btnStartPopupDownload)

        val isTikTok = mediaUrl.contains("tiktok.com", ignoreCase = true)
        val isYouTube = mediaUrl.contains("youtube.com", ignoreCase = true) || mediaUrl.contains("youtu.be", ignoreCase = true)

        when {
            isTikTok -> {
                tvPlatformBadge.text = "تيك توك • TikTok"
                tvPlatformBadge.setBackgroundResource(R.drawable.bg_badge_tiktok)
            }
            isYouTube -> {
                tvPlatformBadge.text = "يوتيوب • YouTube"
                tvPlatformBadge.setBackgroundResource(R.drawable.bg_badge_youtube)
            }
            else -> {
                tvPlatformBadge.text = "رابط وسائط"
                tvPlatformBadge.setBackgroundResource(R.drawable.bg_badge_default)
            }
        }

        tvPopupUrl.text = mediaUrl

        rgFormatType.setOnCheckedChangeListener { _, checkedId ->
            rgVideoQuality.visibility = if (checkedId == R.id.rbTypeVideo) View.VISIBLE else View.GONE
        }

        btnCancelPopup.setOnClickListener {
            dismiss()
            onDismissed()
        }

        btnStartPopupDownload.setOnClickListener {
            val selectedFormat = if (rgFormatType.checkedRadioButtonId == R.id.rbTypeAudio) {
                DownloadFormat.AUDIO_MP3
            } else {
                if (rgVideoQuality.checkedRadioButtonId == R.id.rbQuality1080) {
                    DownloadFormat.VIDEO_1080P
                } else {
                    DownloadFormat.VIDEO_720P
                }
            }
            val customName = etCustomFileName.text.toString().trim()
            dismiss()
            onConfirmDownload(mediaUrl, customName, selectedFormat)
        }
    }
}
