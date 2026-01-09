package com.github.libretube.test.ui.sheets

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.os.bundleOf
import androidx.core.view.isGone
import androidx.media3.common.PlaybackParameters
import androidx.media3.session.MediaController
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.libretube.test.R
import com.github.libretube.test.constants.PreferenceKeys
import com.github.libretube.test.databinding.PlaybackBottomSheetBinding
import com.github.libretube.test.enums.PlayerCommand
import com.github.libretube.test.extensions.round
import com.github.libretube.test.helpers.PreferenceHelper
import com.github.libretube.test.services.AbstractPlayerService
import com.github.libretube.test.ui.adapters.SliderLabelsAdapter
import kotlin.math.absoluteValue
import kotlin.math.log
import kotlin.math.pow
import com.github.libretube.test.obj.VideoResolution
import androidx.core.view.isVisible
import androidx.media3.common.C
import androidx.media3.common.Tracks
import com.github.libretube.test.extensions.updateParameters
import com.github.libretube.test.helpers.PlayerHelper
import androidx.media3.common.Format

class PlaybackOptionsSheet(
    private val player: MediaController
) : ExpandedBottomSheet(R.layout.playback_bottom_sheet) {
    private var _binding: PlaybackBottomSheetBinding? = null
    private val binding get() = _binding!!

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = PlaybackBottomSheetBinding.bind(view)

        binding.speedShortcuts.isVisible = false
        binding.speed.isVisible = false
        binding.qualityButton.isVisible = false
        
        // Pitch Logic
        binding.pitch.value = playbackPitchToSemitone(player.playbackParameters.pitch)

        val currentSemitone = binding.pitch.value.round(2)
        binding.semitoneEditText.setText(currentSemitone.toString())
        binding.pitchResetButton.isGone = currentSemitone == 0f

        binding.semitoneEditText.setOnEditorActionListener { editText, actionId, _ ->
            if (actionId != EditorInfo.IME_ACTION_DONE) {
                return@setOnEditorActionListener false
            }

            if (editText.text.isEmpty()) {
                // reset to previous value
                binding.semitoneEditText.setText(binding.pitch.value.round(2).toString())
                clearEditTextFocusAndHideKeyboard()
                return@setOnEditorActionListener false
            }

            val enteredSemitoneValue = editText.text.toString().toFloat()
            if (enteredSemitoneValue.absoluteValue > SEMITONES_IN_ONE_OCTAVE) {
                editText.error = context?.getString(
                    R.string.playback_pitch_semitone_error_input,
                    SEMITONES_IN_ONE_OCTAVE
                )

                // return true here to prevent the keyboard from closing when
                // the user entered a wrong value
                return@setOnEditorActionListener true
            }
            changePlaybackPitchInSemitone(enteredSemitoneValue)
            clearEditTextFocusAndHideKeyboard()

            true
        }

        binding.pitchResetButton.setOnClickListener {
            changePlaybackPitchInSemitone(0f)
            clearEditTextFocusAndHideKeyboard()
        }

        binding.semitoneDecrementButton.setOnClickListener {
            incrementDecrementSemitoneBy(-1.0f)
            clearEditTextFocusAndHideKeyboard()
        }

        binding.semitoneIncrementButton.setOnClickListener {
            incrementDecrementSemitoneBy(1.0f)
            clearEditTextFocusAndHideKeyboard()
        }

        PreferenceHelper.getBoolean(PreferenceKeys.SKIP_SILENCE, false).let {
            binding.skipSilence.isChecked = it
        }

        // Speed listener removed (hidden)

        binding.pitch.addOnChangeListener { _, _, _ ->
            onChange()
        }
        binding.pitch.setLabelFormatter { value ->
            return@setLabelFormatter "${value.round(2)}"
        }

        binding.skipSilence.setOnCheckedChangeListener { _, isChecked ->
            player.sendCustomCommand(
                AbstractPlayerService.runPlayerActionCommand,
                bundleOf(PlayerCommand.SKIP_SILENCE.name to isChecked)
            )
            PreferenceHelper.putBoolean(PreferenceKeys.SKIP_SILENCE, isChecked)
        }
    }

    private fun updateQualityButtonText() {
        val currentFormat = getCurrentVideoFormat()
        val qualityText = currentFormat?.height?.let { "${it}p" } ?: getString(R.string.auto_quality)
        binding.qualityButton.text = qualityText
    }

    private fun getCurrentVideoFormat(): Format? {
        for (trackGroup in player.currentTracks.groups) {
            if (trackGroup.type != C.TRACK_TYPE_VIDEO) continue

            for (i in 0 until trackGroup.length) {
                if (trackGroup.isTrackSelected(i)) return trackGroup.getTrackFormat(i)
            }
        }
        return null
    }

    private fun getAvailableResolutions(): List<VideoResolution> {
        val resolutions = player.currentTracks.groups.asSequence()
            .filter { it.type == C.TRACK_TYPE_VIDEO }
            .flatMap { group ->
                (0 until group.length).map {
                    group.getTrackFormat(it).height
                }
            }
            .filter { it > 0 }
            .map { VideoResolution("${it}p", it) }
            .toSortedSet(compareByDescending { it.resolution })

        resolutions.add(VideoResolution(getString(R.string.auto_quality), Int.MAX_VALUE))
        return resolutions.toList()
    }

    private fun onQualityClicked() {
        val resolutions = getAvailableResolutions()
        val currentFormat = getCurrentVideoFormat()
        val currentResolution = currentFormat?.height ?: Int.MAX_VALUE

        BaseBottomSheet()
            .setSimpleItems(
                resolutions.map(VideoResolution::name),
                preselectedItem = resolutions.firstOrNull {
                    it.resolution == currentResolution
                }?.name ?: getString(R.string.auto_quality)
            ) { which ->
                val newResolution = resolutions[which].resolution
                player.sendCustomCommand(
                    AbstractPlayerService.runPlayerActionCommand, bundleOf(
                        PlayerCommand.SET_RESOLUTION.name to newResolution
                    )
                )
                updateQualityButtonText()
            }
            .show(childFragmentManager)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun onChange() {
        player.playbackParameters = PlaybackParameters(
            binding.speed.value.round(2),
            semitoneToPlaybackPitch(binding.pitch.value)
        )
        binding.semitoneEditText.setText((binding.pitch.value).round(2).toString())
        binding.pitchResetButton.isGone = binding.pitch.value == 0f

        val currentSpeed = player.playbackParameters.speed.toString()
        PreferenceHelper.putString(PreferenceKeys.PLAYBACK_SPEED, currentSpeed)
    }

    private fun clearEditTextFocusAndHideKeyboard() {
        val inputMethodManager =
            context?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(binding.semitoneEditText.windowToken, 0)
        binding.semitoneEditText.clearFocus()
        binding.semitoneEditText.error = null
    }

    private fun changePlaybackPitchInSemitone(semitone: Float) {
        binding.pitch.value = semitone
        onChange()
    }

    // Reference: https://en.wikipedia.org/wiki/Twelfth_root_of_two
    // From the wiki, "Each note has a frequency that is 2^(1/12) times that of the one below it.", so
    // to get the desired frequency (or in this case playback pitch), we multiply the original frequency
    // with the ratio: 2^(1/12). And for n semitone, we raise the ratio to the power of n and multiply
    // that to the original frequency. So, the final equation:
    //
    // desiredPlaybackPitch = defaultPlaybackPitch * (ratio^n)
    //
    // And the defaultPlaybackPitch value is 1.0f, so we can omit that
    private fun semitoneToPlaybackPitch(semitone: Float): Float {
        return SEMITONE_RATIO.pow(semitone).toFloat()
    }

    // Get the exponent(or in this case semitone) value from a known base (the semitone's ratio)
    private fun playbackPitchToSemitone(playbackPitch: Float): Float {
        return log(playbackPitch, SEMITONE_RATIO)
    }

    private fun incrementDecrementSemitoneBy(value: Float) {
        var currentSemitone = binding.pitch.value
        currentSemitone = currentSemitone + value
        currentSemitone =
            currentSemitone.coerceIn(-SEMITONES_IN_ONE_OCTAVE, SEMITONES_IN_ONE_OCTAVE)
        changePlaybackPitchInSemitone(currentSemitone)
    }

    companion object {
        private val SUGGESTED_SPEEDS = listOf(0.5f, 1f, 1.25f, 1.5f, 1.75f, 2f)

        // Semitone's ratio in equal temperament is the 12th root of 2, or 2^(1/12)
        // References:
        // -https://en.wikipedia.org/wiki/12_equal_temperament
        // -https://en.wikipedia.org/wiki/Twelfth_root_of_two
        private const val SEMITONE_RATIO = 1.059463f

        private const val SEMITONES_IN_ONE_OCTAVE = 12.0f
    }
}

