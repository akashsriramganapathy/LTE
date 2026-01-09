package com.github.libretube.test.ui.activities

import android.content.Intent
import android.os.Bundle
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import com.github.libretube.test.R
import com.github.libretube.test.api.MediaServiceRepository
import com.github.libretube.test.api.obj.StreamItem
import com.github.libretube.test.constants.IntentData
import com.github.libretube.test.extensions.toastFromMainDispatcher
import com.github.libretube.test.helpers.IntentHelper
import com.github.libretube.test.helpers.PreferenceHelper
import com.github.libretube.test.ui.base.BaseActivity
import com.github.libretube.test.ui.dialogs.AddToPlaylistDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AddToPlaylistActivity : BaseActivity() {
    override val isDialogActivity: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val videoId = intent.getStringExtra(Intent.EXTRA_TEXT)?.let {
            IntentHelper.resolveType(it.toUri())
        }?.getStringExtra(IntentData.videoId)

        if (videoId == null) {
            finish()
            return
        }

        supportFragmentManager.setFragmentResultListener(
            AddToPlaylistDialog.ADD_TO_PLAYLIST_DIALOG_DISMISSED_KEY,
            this
        ) { _, _ -> finish() }

        lifecycleScope.launch(Dispatchers.IO) {
            val videoInfo = try {
                MediaServiceRepository.instance.getStreams(videoId).toStreamItem(videoId)
            } catch (e: Exception) {
                toastFromMainDispatcher(R.string.unknown_error)
                withContext(Dispatchers.Main) {
                    finish()
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                AddToPlaylistDialog().apply {
                    arguments = bundleOf(IntentData.videoInfo to videoInfo)
                }.show(supportFragmentManager, null)
            }
        }
    }
}
