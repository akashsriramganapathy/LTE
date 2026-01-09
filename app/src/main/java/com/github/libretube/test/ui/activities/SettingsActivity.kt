package com.github.libretube.test.ui.activities

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.github.libretube.test.BuildConfig
import com.github.libretube.test.R
import com.github.libretube.test.ui.base.BaseActivity
import com.github.libretube.test.ui.screens.SettingsGroupScreen
import com.github.libretube.test.ui.screens.SettingsRegistry
import com.github.libretube.test.ui.screens.SettingsScreen
import com.github.libretube.test.ui.theme.LibreTubeTheme

class SettingsActivity : BaseActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            LibreTubeTheme {
                val navController = rememberNavController()
                val currentBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = currentBackStackEntry?.destination?.route ?: "main"

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    when (currentRoute) {
                                        "main" -> stringResource(R.string.settings)
                                        "appearance" -> stringResource(R.string.appearance)
                                        "general" -> stringResource(R.string.general)
                                        "player" -> stringResource(R.string.player)
                                        "content" -> stringResource(R.string.content_settings)
                                        "notifications" -> stringResource(R.string.notifications)
                                        "downloads" -> stringResource(R.string.downloads)
                                        "backup" -> stringResource(R.string.data_backup)
                                        else -> stringResource(R.string.settings)
                                    }
                                )
                            },
                            navigationIcon = {
                                if (currentRoute != "main") {
                                    IconButton(onClick = { navController.popBackStack() }) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                    }
                                } else {
                                    IconButton(onClick = { finish() }) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                    }
                                }
                            }
                        )
                    }
                ) { padding ->
                    NavHost(
                        navController = navController,
                        startDestination = "main",
                        modifier = Modifier.padding(padding)
                    ) {
                        composable("main") {
                            SettingsScreen(
                                onItemClick = { key ->
                                    when (key) {
                                        "appearance", "general", "player", "content", "notifications", "downloads", "data_backup" -> {
                                            val route = if (key == "data_backup") "backup" else key
                                            navController.navigate(route)
                                        }
                                        "update" -> {
                                            // Handle update check
                                        }
                                        "view_logs" -> {
                                            // Handle logs
                                        }
                                    }
                                },
                                versionName = BuildConfig.VERSION_NAME
                            )
                        }
                        composable("appearance") {
                            SettingsGroupScreen(
                                title = stringResource(R.string.appearance),
                                items = SettingsRegistry.getAppearanceItems(),
                                onNavigate = {}
                            )
                        }
                        composable("general") {
                            SettingsGroupScreen(
                                title = stringResource(R.string.general),
                                items = SettingsRegistry.getGeneralItems(),
                                onNavigate = {}
                            )
                        }
                        composable("player") {
                            SettingsGroupScreen(
                                title = stringResource(R.string.player),
                                items = SettingsRegistry.getPlayerItems(),
                                onNavigate = {}
                            )
                        }
                        composable("content") {
                            SettingsGroupScreen(
                                title = stringResource(R.string.content_settings),
                                items = SettingsRegistry.getContentItems(),
                                onNavigate = {}
                            )
                        }
                        composable("notifications") {
                            SettingsGroupScreen(
                                title = stringResource(R.string.notifications),
                                items = SettingsRegistry.getNotificationsItems(),
                                onNavigate = {}
                            )
                        }
                        composable("downloads") {
                            SettingsGroupScreen(
                                title = stringResource(R.string.downloads),
                                items = SettingsRegistry.getDownloadsItems(),
                                onNavigate = {}
                            )
                        }
                        composable("backup") {
                            SettingsGroupScreen(
                                title = stringResource(R.string.data_backup),
                                items = SettingsRegistry.getBackupItems(),
                                onNavigate = {}
                            )
                        }
                    }
                }
            }
        }
    }
}
