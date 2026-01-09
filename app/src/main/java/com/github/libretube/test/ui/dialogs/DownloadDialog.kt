package com.github.libretube.test.ui.dialogs

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.text.InputFilter
import android.text.format.Formatter
import android.util.Log
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import com.github.libretube.test.R
import com.github.libretube.test.api.MediaServiceRepository
import com.github.libretube.test.api.obj.StreamFormat
import com.github.libretube.test.api.obj.Streams
import com.github.libretube.test.api.obj.Subtitle
import com.github.libretube.test.constants.IntentData
import com.github.libretube.test.databinding.DialogDownloadBinding
import com.github.libretube.test.extensions.TAG
import com.github.libretube.test.extensions.getWhileDigit
import com.github.libretube.test.extensions.sha256Sum
import com.github.libretube.test.extensions.toastFromMainDispatcher
import com.github.libretube.test.helpers.DownloadHelper
import com.github.libretube.test.helpers.PlayerHelper
import com.github.libretube.test.helpers.PreferenceHelper
import com.github.libretube.test.parcelable.DownloadData
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DownloadDialog : DialogFragment() {
    private lateinit var videoId: String
    private var onDownloadConfirm = {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        videoId = arguments?.getString(IntentData.videoId)!!
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogDownloadBinding.inflate(layoutInflater)

        fetchAvailableSources(binding)

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.download)
            .setView(binding.root)
            .setPositiveButton(R.string.download, null)
            .setNegativeButton(R.string.cancel, null)
            .show()
            .apply {
                getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                    onDownloadConfirm.invoke()
                }
            }
    }

    private fun fetchAvailableSources(binding: DialogDownloadBinding) {
        lifecycleScope.launch {
            val response = try {
                withContext(Dispatchers.IO) {
                    MediaServiceRepository.instance.getStreams(videoId)
                }
            }  catch (e: Exception) {
                Log.e(TAG(), e.stackTraceToString())
                context?.toastFromMainDispatcher(e.localizedMessage.orEmpty())
                return@launch
            }
            initDownloadOptions(binding, response)
        }
    }



    private fun initDownloadOptions(binding: DialogDownloadBinding, streams: Streams) {
        binding.videoTitle.text = streams.title

        val videoStreams = streams.videoStreams.filter {
            !it.url.isNullOrEmpty()
        }.filter { !it.format.orEmpty().contains("HLS") }
        .sortedWith(
            compareBy<StreamFormat> { it.videoOnly } // false (0) comes before true (1)
            .thenByDescending { it.quality.getWhileDigit() }
        )

        val audioStreams = streams.audioStreams.filter {
            !it.url.isNullOrEmpty()
        }
            .sortedBy {
                // prioritize main audio track types (lower role flag) over secondary/subbed ones
                PlayerHelper.getFullAudioRoleFlags(0, it.audioTrackType.orEmpty())
            }
            .sortedByDescending {
                it.quality.getWhileDigit()
            }

        val subtitles = streams.subtitles
            .filter { !it.url.isNullOrEmpty() && !it.name.isNullOrEmpty() }
            .sortedBy { it.name }

        if (subtitles.isEmpty()) {
            binding.subtitleSpinner.isGone = true
            binding.subtitleLabel.isGone = true
        } else {
            binding.subtitleSpinner.isVisible = true
            binding.subtitleLabel.isVisible = true
        }

        com.github.libretube.logger.FileLogger.d(TAG(), "Video options: ${videoStreams.size}, Audio options: ${audioStreams.size}, Subtitles: ${subtitles.size}")

        binding.videoSpinner.items = videoStreams.map {
            val fileSize = Formatter.formatShortFileSize(context, it.contentLength)
            val audioTag = if (it.videoOnly) "(No Audio)" else ""
            "${it.quality} ${it.codec} $audioTag ($fileSize)"
        }.toMutableList().also {
            it.add(0, getString(R.string.no_video))
        }

        binding.audioSpinner.items = audioStreams.map {
            val fileSize = it.contentLength
                .takeIf { l -> l > 0 }
                ?.let { cl -> Formatter.formatShortFileSize(context, cl) }
            val infoStr = listOfNotNull(it.audioTrackLocale, fileSize)
                .joinToString(", ")
            "${it.quality} ${it.format} ($infoStr)"
        }.toMutableList().also {
            it.add(0, getString(R.string.no_audio))
        }

        binding.subtitleSpinner.items = subtitles.map { it.name.orEmpty() }.toMutableList().also {
            it.add(0, getString(R.string.no_subtitle))
        }

        restorePreviousSelections(binding, videoStreams, audioStreams, subtitles)

        onDownloadConfirm = onDownloadConfirm@{
            val videoPosition = binding.videoSpinner.selectedItemPosition - 1
            val audioPosition = binding.audioSpinner.selectedItemPosition - 1
            val subtitlePosition = binding.subtitleSpinner.selectedItemPosition - 1

            if (listOf(videoPosition, audioPosition, subtitlePosition).all { it == -1 }) {
                Toast.makeText(context, R.string.nothing_selected, Toast.LENGTH_SHORT).show()
                return@onDownloadConfirm
            }

            val videoStream = videoStreams.getOrNull(videoPosition)
            val audioStream = audioStreams.getOrNull(audioPosition)
            val subtitle = subtitles.getOrNull(subtitlePosition)

            com.github.libretube.logger.FileLogger.i(TAG(), "Selected - Video: ${videoStream?.quality} ${videoStream?.format}, Audio: ${audioStream?.quality} ${audioStream?.format}, Subtitle: ${subtitle?.code}")

            saveSelections(videoStream, audioStream, subtitle)

            val downloadData = DownloadData(
                videoId = videoId,
                videoFormat = videoStream?.format,
                videoQuality = videoStream?.quality,
                audioFormat = audioStream?.format,
                audioQuality = audioStream?.quality,
                audioLanguage = audioStream?.audioTrackLocale,
                subtitleCode = subtitle?.code
            )
            DownloadHelper.startDownloadService(requireContext(), downloadData)

            dismiss()
        }
    }

    /**
     * Save the download selection to the preferences
     */
    private fun saveSelections(
        videoStream: StreamFormat?,
        audioStream: StreamFormat?,
        subtitle: Subtitle?
    ) {
        PreferenceHelper.putString(SUBTITLE_LANGUAGE, subtitle?.code.orEmpty())
        PreferenceHelper.putString(VIDEO_DOWNLOAD_FORMAT, videoStream?.format.orEmpty())
        PreferenceHelper.putString(VIDEO_DOWNLOAD_QUALITY, videoStream?.quality.orEmpty())
        PreferenceHelper.putString(AUDIO_DOWNLOAD_FORMAT, audioStream?.format.orEmpty())
        PreferenceHelper.putString(AUDIO_DOWNLOAD_QUALITY, audioStream?.quality.orEmpty())
    }

    private fun getSel(key: String) = PreferenceHelper.getString(key, "")

    /**
     * Restore the download selections from a previous session
     */
    private fun restorePreviousSelections(
        binding: DialogDownloadBinding,
        videoStreams: List<StreamFormat>,
        audioStreams: List<StreamFormat>,
        subtitles: List<Subtitle>
    ) {
        getStreamSelection(
            videoStreams,
            getSel(VIDEO_DOWNLOAD_QUALITY),
            getSel(VIDEO_DOWNLOAD_FORMAT)
        )?.let {
            binding.videoSpinner.selectedItemPosition = it + 1
        }
        getStreamSelection(
            audioStreams,
            getSel(AUDIO_DOWNLOAD_QUALITY),
            getSel(AUDIO_DOWNLOAD_FORMAT)
        )?.let {
            binding.audioSpinner.selectedItemPosition = it + 1
        }

        subtitles.indexOfFirst { it.code == getSel(SUBTITLE_LANGUAGE) }.takeIf { it != -1 }?.let {
            binding.subtitleSpinner.selectedItemPosition = it + 1
        }
    }

    private fun getStreamSelection(
        streams: List<StreamFormat>,
        quality: String,
        format: String
    ): Int? {
        if (quality.isBlank()) return null

        streams.forEachIndexed { index, streamFormat ->
            if (quality == streamFormat.quality && format == streamFormat.format) return index
        }

        streams.forEachIndexed { index, streamFormat ->
            if (quality == streamFormat.quality) return index
        }

        val qualityInt = quality.getWhileDigit() ?: return null

        streams.forEachIndexed { index, streamFormat ->
            if ((streamFormat.quality.getWhileDigit() ?: Int.MAX_VALUE) < qualityInt) return index
        }

        return null
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)

        setFragmentResult(DOWNLOAD_DIALOG_DISMISSED_KEY, bundleOf())
    }

    companion object {
        private const val VIDEO_DOWNLOAD_QUALITY = "video_download_quality"
        private const val VIDEO_DOWNLOAD_FORMAT = "video_download_format"
        private const val AUDIO_DOWNLOAD_QUALITY = "audio_download_quality"
        private const val AUDIO_DOWNLOAD_FORMAT = "audio_download_format"
        private const val SUBTITLE_LANGUAGE = "subtitle_download_language"

        const val DOWNLOAD_DIALOG_DISMISSED_KEY = "download_dialog_dismissed_key"
    }
}

