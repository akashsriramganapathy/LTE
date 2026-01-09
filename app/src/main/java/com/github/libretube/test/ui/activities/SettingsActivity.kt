package com.github.libretube.test.ui.activities

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.replace
import androidx.lifecycle.lifecycleScope
import com.github.libretube.test.R
import com.github.libretube.test.databinding.ActivitySettingsBinding
import com.github.libretube.test.ui.base.BaseActivity

import com.github.libretube.test.ui.preferences.MainSettings
import com.github.libretube.test.ui.preferences.SettingsSearchFragment
import com.github.libretube.test.util.UpdateChecker
import kotlinx.coroutines.launch

class SettingsActivity : BaseActivity() {
    lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        binding.toolbar.inflateMenu(R.menu.settings_menu)
        binding.toolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.action_search -> {
                    // Navigate to search
                    redirectTo<SettingsSearchFragment>()
                    true
                }
                else -> false
            }
        }

        if (savedInstanceState == null) {
            goToMainSettings()
        }

        handleRedirect()
    }

    fun goToMainSettings() {
        redirectTo<MainSettings>()
        changeTopBarText(getString(R.string.settings))
    }

    private fun handleRedirect() {
        // No-op
    }

    fun changeTopBarText(text: String) {
        if (this::binding.isInitialized) binding.toolbar.title = text
    }

    inline fun <reified T : Fragment> redirectTo() {
        supportFragmentManager.commit {
            replace<T>(R.id.settings)
        }
    }

    companion object {
        const val REDIRECT_KEY = "redirect"
        const val REDIRECT_TO_INTENT_SETTINGS = "intent_settings"
    }
}

