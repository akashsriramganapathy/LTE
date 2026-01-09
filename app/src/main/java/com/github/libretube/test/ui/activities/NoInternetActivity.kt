package com.github.libretube.test.ui.activities

import android.content.Intent
import android.os.Bundle
import com.github.libretube.test.constants.IntentData
import com.github.libretube.test.databinding.ActivityNointernetBinding
import com.github.libretube.test.helpers.NavigationHelper
import com.github.libretube.test.ui.base.BaseActivity

class NoInternetActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = ActivityNointernetBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        if (intent.getBooleanExtra(IntentData.maximizePlayer, false)) {
            NavigationHelper.openAudioPlayerFragment(this, offlinePlayer = true)
        }
    }
}

