package com.github.libretube.test.ui.sheets

import android.os.Bundle
import android.view.View
import com.github.libretube.test.R
import com.github.libretube.test.constants.IntentData
import com.github.libretube.test.databinding.DialogStatsBinding
import com.github.libretube.test.extensions.parcelable
import com.github.libretube.test.helpers.ClipboardHelper
import com.github.libretube.test.obj.VideoStats

class StatsSheet : ExpandedBottomSheet(R.layout.dialog_stats) {
    private lateinit var stats: VideoStats

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        stats = arguments?.parcelable(IntentData.videoStats)!!
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = DialogStatsBinding.bind(view)
        binding.videoId.setText(stats.videoId)
        binding.videoIdCopy.setEndIconOnClickListener {
            ClipboardHelper.save(requireContext(), "text", stats.videoId)
        }
        binding.videoInfo.setText(stats.videoInfo)
        binding.audioInfo.setText(stats.audioInfo)
        binding.videoQuality.setText(stats.videoQuality)
    }
}

