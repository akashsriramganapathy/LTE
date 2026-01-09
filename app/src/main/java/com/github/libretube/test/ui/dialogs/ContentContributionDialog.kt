package com.github.libretube.test.ui.dialogs

import android.app.Dialog
import android.os.Bundle
import android.text.format.DateUtils
import android.util.Log
import android.widget.Toast
import androidx.core.view.isGone
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.github.libretube.test.R
import com.github.libretube.test.api.MediaServiceRepository
import com.github.libretube.test.api.RetrofitInstance
import com.github.libretube.test.api.obj.DeArrowSubmission
import com.github.libretube.test.api.obj.DeArrowThumbnailSubmission
import com.github.libretube.test.api.obj.DeArrowTitleSubmission
import com.github.libretube.test.api.obj.Segment
import com.github.libretube.test.constants.IntentData
import com.github.libretube.test.constants.PreferenceKeys
import androidx.core.view.isVisible
import com.github.libretube.test.databinding.DialogContentContributionBinding
import com.github.libretube.test.extensions.TAG
import com.github.libretube.test.extensions.toastFromMainDispatcher
import com.github.libretube.test.helpers.PreferenceHelper
import com.github.libretube.test.util.TextUtils
import com.github.libretube.test.util.TextUtils.parseDurationString
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ContentContributionDialog : DialogFragment() {
    private var videoId: String = ""
    private var currentPosition: Long = 0
    private var duration: Long? = null
    private var segments: List<Segment> = emptyList()
    private var videoTitle: String = ""

    private var _binding: DialogContentContributionBinding? = null
    private val binding: DialogContentContributionBinding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            videoId = it.getString(IntentData.videoId)!!
            currentPosition = it.getLong(IntentData.currentPosition)
            duration = it.getLong(IntentData.duration)
            // Retrieve title if passed, or default to empty
            videoTitle = it.getString(IntentData.videoTitle) ?: ""
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogContentContributionBinding.inflate(layoutInflater)

        setupTabs()
        setupSponsorBlock()
        setupDeArrow()

        binding.dialogCancel.setOnClickListener {
            dismiss()
        }

        return MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .show()
    }

    private fun setupTabs() {
        val sbEnabled = PreferenceHelper.getBoolean(PreferenceKeys.CONTRIBUTE_TO_SB, false)
        val daEnabled = PreferenceHelper.getBoolean(PreferenceKeys.CONTRIBUTE_TO_DEARROW, false)

        if (sbEnabled && daEnabled) {
            // Main Contribution Type Tabs
            binding.contributionTypeTabs.addTab(binding.contributionTypeTabs.newTab().setText("SponsorBlock"))
            binding.contributionTypeTabs.addTab(binding.contributionTypeTabs.newTab().setText("DeArrow"))
            binding.contributionTypeTabs.isVisible = true

            binding.contributionTypeTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab?) {
                    binding.contributionFlipper.displayedChild = tab?.position ?: 0
                }
                override fun onTabUnselected(tab: TabLayout.Tab?) {}
                override fun onTabReselected(tab: TabLayout.Tab?) {}
            })
        } else if (sbEnabled) {
            binding.contributionTypeTabs.isGone = true
            binding.contributionFlipper.displayedChild = 0
        } else if (daEnabled) {
            binding.contributionTypeTabs.isGone = true
            binding.contributionFlipper.displayedChild = 1
        } else {
            // Should theoretically not happen as button is hidden
            dismiss()
        }
    }

    private fun setupSponsorBlock() {
        binding.sbSubmit.setOnClickListener {
            lifecycleScope.launch { createSegment() }
        }
        binding.sbVoteSubmit.setOnClickListener {
            lifecycleScope.launch { voteForSegment() }
        }

        binding.sbStartTime.setText(DateUtils.formatElapsedTime(((currentPosition.toFloat() / 1000).toLong())))
        binding.sbCategory.items = resources.getStringArray(R.array.sponsorBlockSegmentNames).toList()

        binding.sbSwapTimestamps.setOnClickListener {
            val temp = binding.sbStartTime.text
            binding.sbStartTime.text = binding.sbEndTime.text
            binding.sbEndTime.text = temp
        }

        lifecycleScope.launch(Dispatchers.IO) {
            fetchSegments()
        }
    }

    private fun setupDeArrow() {
        // Pre-fill title
        if (videoTitle.isNotEmpty()) {
            binding.daTitleInput.setText(videoTitle)
        }

        binding.daTitleSubmit.setOnClickListener {
            lifecycleScope.launch { submitDeArrowTitle() }
        }

        binding.daThumbTimestamp.setText((currentPosition.toFloat() / 1000).toString())
        binding.daThumbCaptureTime.setOnClickListener {
            binding.daThumbTimestamp.setText((currentPosition.toFloat() / 1000).toString())
        }

        binding.daThumbSubmit.setOnClickListener {
            lifecycleScope.launch { submitDeArrowThumbnail() }
        }
    }

    private suspend fun createSegment() {
        val context = requireContext().applicationContext
        val binding = _binding ?: return

        requireDialog().hide()

        var startTime = binding.sbStartTime.text.toString().parseDurationString()
        var endTime = binding.sbEndTime.text.toString().parseDurationString()

        if (endTime == null || startTime == null || startTime > endTime) {
            context.toastFromMainDispatcher(R.string.sb_invalid_segment)
            return
        }

        startTime = maxOf(startTime, 0f)
        if (duration != null) {
            endTime = minOf(endTime, duration!!.toFloat())
        }

        val categories = resources.getStringArray(R.array.sponsorBlockSegments)
        val category = categories[binding.sbCategory.selectedItemPosition]
        val userAgent = TextUtils.getUserAgent(context)
        val uuid = PreferenceHelper.getSponsorBlockUserID()
        val duration = duration?.let { it.toFloat() / 1000 }

        try {
            withContext(Dispatchers.IO) {
                RetrofitInstance.externalApi
                    .submitSegment(videoId, uuid, userAgent, startTime, endTime, category, duration)
            }
            context.toastFromMainDispatcher(R.string.segment_submitted)
        } catch (e: Exception) {
            Log.e(TAG(), e.toString())
            context.toastFromMainDispatcher(e.localizedMessage.orEmpty())
        }

        requireDialog().dismiss()
    }

    private suspend fun voteForSegment() {
        val binding = _binding ?: return
        val context = requireContext().applicationContext

        val segmentID = segments.getOrNull(binding.sbVoteSegmentDropdown.selectedItemPosition)
            ?.uuid ?: return

        val score = when {
            binding.sbVoteUp.isChecked -> 1
            binding.sbVoteDown.isChecked -> 0
            else -> 20
        }

        dialog?.hide()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                RetrofitInstance.externalApi.voteOnSponsorTime(
                    uuid = segmentID,
                    userID = PreferenceHelper.getSponsorBlockUserID(),
                    score = score
                )
                context.toastFromMainDispatcher(R.string.success)
            } catch (e: Exception) {
                context.toastFromMainDispatcher(e.localizedMessage.orEmpty())
            }
            withContext(Dispatchers.Main) { dialog?.dismiss() }
        }
    }

    private suspend fun fetchSegments() {
        val categories = resources.getStringArray(R.array.sponsorBlockSegments).toList()
        segments = try {
            MediaServiceRepository.instance.getSegments(videoId, categories).segments
        } catch (e: Exception) {
            Log.e(TAG(), e.toString())
            return
        }

        withContext(Dispatchers.Main) {
            val binding = _binding ?: return@withContext

            if (segments.isEmpty()) {
                binding.sbVoteSubmit.isEnabled = false // Disable vote button if no segments
                // binding.voteSegmentContainer.isGone = true // Optional: hide container
                return@withContext
            }
             binding.sbVoteSubmit.isEnabled = true

            binding.sbVoteSegmentDropdown.items = segments.map {
                val (start, end) = it.segmentStartAndEnd
                val (startStr, endStr) = DateUtils.formatElapsedTime(start.toLong()) to
                        DateUtils.formatElapsedTime(end.toLong())
                "${it.category} ($startStr - $endStr)"
            }
        }
    }
    
    private suspend fun submitDeArrowTitle() {
        val context = requireContext().applicationContext
        val title = binding.daTitleInput.text.toString()
        
        if (title.isBlank()) {
            context.toastFromMainDispatcher("Title cannot be empty")
            return
        }
        
        val submission = DeArrowSubmission(
            videoID = videoId,
            userID = PreferenceHelper.getSponsorBlockUserID(),
            userAgent = TextUtils.getUserAgent(context),
            title = DeArrowTitleSubmission(title)
        )
        
        submitDeArrow(submission)
    }
    
    private suspend fun submitDeArrowThumbnail() {
       val context = requireContext().applicationContext
       val timestampStr = binding.daThumbTimestamp.text.toString()
       val timestamp = timestampStr.toDoubleOrNull()
       
       if (timestamp == null) {
           context.toastFromMainDispatcher("Invalid timestamp")
           return
       }
       
       val submission = DeArrowSubmission(
            videoID = videoId,
            userID = PreferenceHelper.getSponsorBlockUserID(),
            userAgent = TextUtils.getUserAgent(context),
            thumbnail = DeArrowThumbnailSubmission(timestamp)
        )
        
        submitDeArrow(submission)
    }
    
    private suspend fun submitDeArrow(submission: DeArrowSubmission) {
        val context = requireContext().applicationContext
        requireDialog().hide()
        
        try {
            withContext(Dispatchers.IO) {
                MediaServiceRepository.instance.submitDeArrow(submission)
            }
            context.toastFromMainDispatcher(R.string.success) // Reuse success string or add new one
        } catch (e: Exception) {
            Log.e(TAG(), "DeArrow Submission Error", e)
            context.toastFromMainDispatcher(e.localizedMessage.orEmpty())
        }
        
        requireDialog().dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
