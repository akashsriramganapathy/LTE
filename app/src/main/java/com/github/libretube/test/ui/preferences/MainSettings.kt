package com.github.libretube.test.ui.preferences

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import com.github.libretube.test.BuildConfig
import androidx.compose.runtime.Composable
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.github.libretube.test.R
import com.github.libretube.test.ui.activities.SettingsActivity
import com.github.libretube.test.ui.screens.SettingsScreen
import com.github.libretube.test.ui.preferences.GeneralSettings
import com.github.libretube.test.ui.preferences.DownloadSettings
import com.github.libretube.test.ui.preferences.AppearanceSettings
import com.github.libretube.test.ui.preferences.PlayerSettings
import com.github.libretube.test.ui.preferences.ContentSettings
import com.github.libretube.test.ui.preferences.DataSettings
import com.github.libretube.test.ui.preferences.NotificationSettings
import com.github.libretube.test.ui.theme.LibreTubeTheme
import com.github.libretube.test.util.UpdateChecker
import kotlinx.coroutines.launch
import com.github.libretube.test.ui.dialogs.LogViewerDialog
import kotlinx.coroutines.Dispatchers

class MainSettings : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                LibreTubeTheme {
                    SettingsScreen(
                        onItemClick = { key -> handleItemClick(key) },
                        versionName = BuildConfig.VERSION_NAME
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as? SettingsActivity)?.changeTopBarText(getString(R.string.settings))
    }

    private fun handleItemClick(key: String) {
        val settingsActivity = activity as? SettingsActivity ?: return
        
        when (key) {
            "general" -> settingsActivity.redirectTo<GeneralSettings>()
            "downloads" -> settingsActivity.redirectTo<DownloadSettings>()
            "appearance" -> settingsActivity.redirectTo<AppearanceSettings>()
            "player" -> settingsActivity.redirectTo<PlayerSettings>()
            "content" -> settingsActivity.redirectTo<ContentSettings>()
            "data_backup" -> settingsActivity.redirectTo<DataSettings>()
            "notifications" -> settingsActivity.redirectTo<NotificationSettings>()
            "update" -> {
                settingsActivity.lifecycleScope.launch {
                    UpdateChecker(requireContext()).checkUpdate(true)
                }
            }
            "view_logs" -> {
                 LogViewerDialog().show(childFragmentManager, null)
            }
        }
    }
}

