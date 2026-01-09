package com.github.libretube.test.ui.activities

import android.os.Bundle
import androidx.activity.compose.setContent
import com.github.libretube.test.helpers.IntentHelper
import com.github.libretube.test.ui.base.BaseActivity
import com.github.libretube.test.ui.screens.HelpScreen
import com.github.libretube.test.ui.theme.LibreTubeTheme
import com.google.android.material.card.MaterialCardView

class HelpActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            LibreTubeTheme {
                HelpScreen(
                    onFaqClick = { IntentHelper.openLinkFromHref(this, supportFragmentManager, FAQ_URL) },
                    onMatrixClick = { IntentHelper.openLinkFromHref(this, supportFragmentManager, MATRIX_URL) },
                    onMastodonClick = { IntentHelper.openLinkFromHref(this, supportFragmentManager, MASTODON_URL) },
                    onLemmyClick = { IntentHelper.openLinkFromHref(this, supportFragmentManager, LEMMY_URL) }
                )
            }
        }
    }

    private fun setupCard(card: MaterialCardView, link: String) {
        card.setOnClickListener {
            IntentHelper.openLinkFromHref(this, supportFragmentManager, link)
        }
    }

    companion object {
        private const val FAQ_URL = "https://libretube.dev/#faq"
        private const val MATRIX_URL = "https://matrix.to/#/#LibreTube:matrix.org"
        private const val MASTODON_URL = "https://fosstodon.org/@libretube"
        private const val LEMMY_URL = "https://feddit.rocks/c/libretube"
    }
}

