package com.github.libretube.test.ui.extensions

import androidx.core.view.isGone
import com.github.libretube.test.R
import com.github.libretube.test.constants.PreferenceKeys
import com.github.libretube.test.helpers.PreferenceHelper
import com.google.android.material.button.MaterialButton

fun MaterialButton.setupNotificationBell(channelId: String) {
    if (!PreferenceHelper.getBoolean(PreferenceKeys.NOTIFICATION_ENABLED, true)) {
        isGone = true
        return
    }

    var isIgnorable = PreferenceHelper.isChannelNotificationIgnorable(channelId)
    setIconResource(iconResource(isIgnorable))

    setOnClickListener {
        isIgnorable = !isIgnorable
        PreferenceHelper.toggleIgnorableNotificationChannel(channelId)
        setIconResource(iconResource(isIgnorable))
    }
}

private fun iconResource(isIgnorable: Boolean) =
    if (isIgnorable) R.drawable.ic_bell else R.drawable.ic_notification
