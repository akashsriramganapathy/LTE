package com.github.libretube.test.ui.sheets

import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import com.github.libretube.test.R
import com.github.libretube.test.api.obj.StreamItem
import com.github.libretube.test.constants.IntentData
import com.github.libretube.test.enums.ShareObjectType
import com.github.libretube.test.extensions.parcelable
import com.github.libretube.test.extensions.serializable
import com.github.libretube.test.extensions.toID
import com.github.libretube.test.helpers.BackgroundHelper
import com.github.libretube.test.helpers.ContextHelper
import com.github.libretube.test.helpers.NavigationHelper
import com.github.libretube.test.obj.ShareData
import com.github.libretube.test.ui.activities.NoInternetActivity
import com.github.libretube.test.ui.dialogs.ShareDialog
import com.github.libretube.test.ui.fragments.DownloadTab
import com.github.libretube.test.util.PlayingQueue
import com.github.libretube.test.util.PlayingQueueMode

class DownloadOptionsBottomSheet : BaseBottomSheet() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val streamItem = arguments?.parcelable<StreamItem>(IntentData.streamItem)!!
        val videoId = streamItem.url!!.toID()
        val downloadTab = arguments?.serializable<DownloadTab>(IntentData.downloadTab)!!

        val options = mutableListOf(
            R.string.playOnBackground,
            R.string.share,
            R.string.delete
        )

        // can't navigate to video while in offline activity
        if (ContextHelper.tryUnwrapActivity<NoInternetActivity>(requireContext()) == null) {
            options += R.string.go_to_video
        }

        val isSelectedVideoCurrentlyPlaying = PlayingQueue.getCurrent()?.url?.toID() == videoId
        if (!isSelectedVideoCurrentlyPlaying && PlayingQueue.isNotEmpty() && PlayingQueue.queueMode == PlayingQueueMode.OFFLINE) {
            options += R.string.play_next
            options += R.string.add_to_queue
        }

        setSimpleItems(options.map { getString(it) }) { which ->
            when (options[which]) {
                R.string.playOnBackground -> {
                    BackgroundHelper.playOnBackgroundOffline(requireContext(), videoId, downloadTab)
                }

                R.string.go_to_video -> {
                    NavigationHelper.navigateVideo(requireContext(), videoId = videoId)
                }

                R.string.share -> {
                    val shareData = ShareData(currentVideo = videoId)
                    val bundle = bundleOf(
                        IntentData.id to videoId,
                        IntentData.shareObjectType to ShareObjectType.VIDEO,
                        IntentData.shareData to shareData
                    )
                    val newShareDialog = ShareDialog()
                    newShareDialog.arguments = bundle
                    newShareDialog.show(parentFragmentManager, null)
                }

                R.string.delete -> {
                    setFragmentResult(DELETE_DOWNLOAD_REQUEST_KEY, bundleOf())
                    dialog?.dismiss()
                }

                R.string.play_next -> {
                    PlayingQueue.addAsNext(streamItem)
                }

                R.string.add_to_queue -> {
                    PlayingQueue.add(streamItem)
                }
            }
        }

        super.onCreate(savedInstanceState)
    }

    companion object {
        const val DELETE_DOWNLOAD_REQUEST_KEY = "delete_download_request_key"
    }
}

