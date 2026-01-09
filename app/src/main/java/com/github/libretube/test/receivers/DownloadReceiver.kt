package com.github.libretube.test.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.github.libretube.test.constants.IntentData
import com.github.libretube.test.services.DownloadService
import com.github.libretube.test.ui.activities.MainActivity

class DownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val activityIntent = Intent(context, MainActivity::class.java)

        when (intent?.action) {
            DownloadService.ACTION_SERVICE_STARTED -> {
                activityIntent.putExtra(IntentData.downloading, true)
            }

            DownloadService.ACTION_SERVICE_STOPPED -> {
                activityIntent.putExtra(IntentData.downloading, false)
            }
        }
        context?.startActivity(activityIntent)
    }
}

