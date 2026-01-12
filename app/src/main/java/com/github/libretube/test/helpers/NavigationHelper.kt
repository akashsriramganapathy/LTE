package com.github.libretube.test.helpers

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Process
import androidx.core.content.getSystemService
import androidx.core.os.bundleOf
import androidx.fragment.app.replace
// import com.github.libretube.test.NavDirections
import com.github.libretube.test.R
import com.github.libretube.test.constants.IntentData
import com.github.libretube.test.constants.PreferenceKeys
import com.github.libretube.test.enums.PlaylistType
import com.github.libretube.test.extensions.toID
import com.github.libretube.test.parcelable.PlayerData
import com.github.libretube.test.ui.activities.MainActivity
import com.github.libretube.test.ui.activities.ZoomableImageActivity
import com.github.libretube.test.ui.base.BaseActivity
import androidx.lifecycle.ViewModelProvider
import com.github.libretube.test.ui.models.PlayerViewModel
import com.github.libretube.test.helpers.ContextHelper

object NavigationHelper {
    fun navigateChannel(context: Context, channelUrlOrId: String?) {
        if (channelUrlOrId == null) return

        val activity = ContextHelper.unwrapActivity<MainActivity>(context)
        activity.navController.navigate(com.github.libretube.test.ui.navigation.Routes.channel(channelId = channelUrlOrId.toID()))
    }

    /**
     * Navigate to the given video using the other provided parameters as well
     * If the audio only mode is enabled, play it in the background, else as a normal video
     */
    @SuppressLint("UnsafeOptInUsageError")
    fun navigateVideo(
        context: Context,
        videoId: String?,
        playlistId: String? = null,
        channelId: String? = null,
        keepQueue: Boolean = false,
        timestamp: Long = 0,
        alreadyStarted: Boolean = false,
        forceVideo: Boolean = false,
        audioOnlyPlayerRequested: Boolean = false,
    ) {
        if (videoId == null) return

        val activity = ContextHelper.unwrapActivity<MainActivity>(context)
        val viewModel = ViewModelProvider(activity)[PlayerViewModel::class.java]

        // Load video into ViewModel
        viewModel.loadVideo(
            videoId = videoId.toID(),
            playlistId = playlistId,
            channelId = channelId,
            timestamp = timestamp,
            playWhenReady = true
        )

        val audioOnlyMode = PreferenceHelper.getBoolean(PreferenceKeys.AUDIO_ONLY_MODE, false)
        
        if (audioOnlyPlayerRequested || (audioOnlyMode && !forceVideo)) {
            // Audio mode
             BackgroundHelper.playOnBackground(
                 context,
                 videoId.toID(),
                 timestamp,
                 playlistId,
                 channelId,
                 keepQueue
             )
             // Minimize player handled by ViewModel/UI observing state?
        } else {
             // Video mode
             android.util.Log.d("NavigationHelper", "Video mode selected, triggering player expansion")
             viewModel.triggerPlayerExpansion()
        }
    }

    fun navigatePlaylist(context: Context, playlistUrlOrId: String?, playlistType: PlaylistType) {
        if (playlistUrlOrId == null) return

        val activity = ContextHelper.unwrapActivity<MainActivity>(context)
        activity.navController.navigate(
            com.github.libretube.test.ui.navigation.Routes.playlist(
                playlistId = playlistUrlOrId.toID(),
                type = playlistType
            )
        )
    }

    /**
     * Start the audio player fragment
     * TODO: Replace with Compose PlayerScreen
     */
    /*
    fun openAudioPlayerFragment(
        context: Context,
        offlinePlayer: Boolean = false,
        minimizeByDefault: Boolean = false
    ) {
        val activity = ContextHelper.unwrapActivity<BaseActivity>(context)
        activity.supportFragmentManager.commitNow {
            val args = bundleOf(
                IntentData.minimizeByDefault to minimizeByDefault,
                IntentData.offlinePlayer to offlinePlayer
            )
            replace<AudioPlayerFragment>(R.id.container, args = args)
        }
    }
    */

    /**
     * Starts the video player fragment for an already existing media
     * TODO: Update this to work with Compose PlayerScreen
     */
    fun openVideoPlayerFragment(
        context: Context,
        videoId: String,
        playlistId: String? = null,
        channelId: String? = null,
        keepQueue: Boolean = false,
        timestamp: Long = 0,
        alreadyStarted: Boolean = false
    ) {
        // TODO: This needs to be updated to work with the new Compose-based player
        // The old fragment-based approach with R.id.container no longer exists
        /*
        val activity = ContextHelper.unwrapActivity<BaseActivity>(context)

        val playerData =
            PlayerData(videoId, playlistId, channelId, keepQueue, timestamp)
        val bundle = bundleOf(
            IntentData.playerData to playerData,
            IntentData.alreadyStarted to alreadyStarted
        )
        activity.supportFragmentManager.commitNow {
            replace<PlayerFragment>(R.id.container, args = bundle)
        }
        */
    }

    /**
     * Open a large, zoomable image preview
     */
    fun openImagePreview(context: Context, url: String) {
        val intent = Intent(context, ZoomableImageActivity::class.java)
        intent.putExtra(IntentData.bitmapUrl, url)
        context.startActivity(intent)
    }

    /**
     * Needed due to different MainActivity Aliases because of the app icons
     */
    fun restartMainActivity(context: Context) {
        // kill player notification
        context.getSystemService<NotificationManager>()!!.cancelAll()
        // start a new Intent of the app
        val pm = context.packageManager
        val intent = pm.getLaunchIntentForPackage(context.packageName)
        intent?.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK
        context.startActivity(intent)
        // kill the old application
        Process.killProcess(Process.myPid())
    }
}

