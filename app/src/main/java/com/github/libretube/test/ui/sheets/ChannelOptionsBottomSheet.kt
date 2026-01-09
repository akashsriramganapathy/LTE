package com.github.libretube.test.ui.sheets

import android.os.Bundle
import android.util.Log
import androidx.core.os.bundleOf
import com.github.libretube.test.R
import com.github.libretube.test.api.MediaServiceRepository
import com.github.libretube.test.constants.IntentData
import com.github.libretube.test.enums.ShareObjectType
import com.github.libretube.test.extensions.TAG
import com.github.libretube.test.extensions.toID
import com.github.libretube.test.helpers.BackgroundHelper
import com.github.libretube.test.helpers.NavigationHelper
import com.github.libretube.test.obj.ShareData
import com.github.libretube.test.ui.dialogs.ShareDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Dialog with different options for a selected video.
 *
 * Needs the [channelId] to load the content from the right video.
 */
class ChannelOptionsBottomSheet : BaseBottomSheet() {
    private lateinit var channelId: String
    private var channelName: String? = null
    private var subscribed: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        channelId = arguments?.getString(IntentData.channelId)!!
        channelName = arguments?.getString(IntentData.channelName)
        subscribed = arguments?.getBoolean(IntentData.isSubscribed, false) ?: false

        setTitle(channelName)

        // List that stores the different menu options. In the future could be add more options here.
        val optionsList = mutableListOf(
            R.string.share,
            R.string.play_latest_videos,
            R.string.playOnBackground
        )
        if (subscribed) optionsList.add(R.string.add_to_group)

        setSimpleItems(optionsList.map { getString(it) }) { which ->
            when (optionsList[which]) {
                R.string.share -> {
                    val bundle = bundleOf(
                        IntentData.id to channelId,
                        IntentData.shareObjectType to ShareObjectType.CHANNEL,
                        IntentData.shareData to ShareData(currentChannel = channelName)
                    )
                    val newShareDialog = ShareDialog()
                    newShareDialog.arguments = bundle
                    newShareDialog.show(parentFragmentManager, null)
                }

                R.string.add_to_group -> {
                    val sheet = AddChannelToGroupSheet().apply {
                        arguments = bundleOf(IntentData.channelId to channelId)
                    }
                    sheet.show(parentFragmentManager, null)
                }

                R.string.play_latest_videos -> {
                    try {
                        val channel = withContext(Dispatchers.IO) {
                            MediaServiceRepository.instance.getChannel(channelId)
                        }
                        channel.relatedStreams.firstOrNull()?.url?.toID()?.let {
                            NavigationHelper.navigateVideo(
                                requireContext(),
                                it,
                                channelId = channelId
                            )
                        }
                    } catch (e: Exception) {
                        Log.e(TAG(), e.toString())
                    }
                }

                R.string.playOnBackground -> {
                    try {
                        val channel = withContext(Dispatchers.IO) {
                            MediaServiceRepository.instance.getChannel(channelId)
                        }
                        channel.relatedStreams.firstOrNull()?.url?.toID()?.let {
                            BackgroundHelper.playOnBackground(
                                requireContext(),
                                videoId = it,
                                channelId = channelId
                            )
                        }
                    } catch (e: Exception) {
                        Log.e(TAG(), e.toString())
                    }
                }
            }
        }
    }
}

