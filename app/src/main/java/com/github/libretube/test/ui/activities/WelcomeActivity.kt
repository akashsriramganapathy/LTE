package com.github.libretube.test.ui.activities

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.activity.compose.setContent
import com.github.libretube.test.ui.base.BaseActivity
import com.github.libretube.test.ui.models.WelcomeViewModel
import com.github.libretube.test.ui.preferences.BackupRestoreSettings
import com.github.libretube.test.ui.screens.WelcomeScreen
import com.github.libretube.test.ui.theme.LibreTubeTheme

import com.github.libretube.test.R

class WelcomeActivity : BaseActivity() {

    private val viewModel by viewModels<WelcomeViewModel> { WelcomeViewModel.Factory }

    private val restoreFilePicker =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri == null) return@registerForActivityResult
            viewModel.restoreAdvancedBackup(this, uri)
        }

    private val selectBackupLocation = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@registerForActivityResult

        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            val path = uri.toString()
            com.github.libretube.test.helpers.PreferenceHelper.putString(com.github.libretube.test.constants.PreferenceKeys.AUTO_BACKUP_PATH, path)
            com.github.libretube.test.helpers.PreferenceHelper.putBoolean(com.github.libretube.test.constants.PreferenceKeys.AUTO_BACKUP_ENABLED, true)
            
            // Schedule worker immediately
            com.github.libretube.test.workers.AutoBackupWorker.enqueueWork(applicationContext)
            
            Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, R.string.auto_backup_permission_error, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            LibreTubeTheme {
                WelcomeScreen(
                    onSelectBackupLocation = { selectBackupLocation.launch(null) },
                    onSetDefaultApp = {
                        val intent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                            Intent(android.provider.Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, android.net.Uri.parse("package:$packageName"))
                        } else {
                            Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$packageName"))
                        }
                        startActivity(intent)
                    },
                    onRestoreBackup = { restoreFilePicker.launch("*/*") },
                    onConfirm = { viewModel.onConfirmSettings() }
                )
            }
        }

        viewModel.uiState.observe(this) { (error, navigateToMain) ->
            error?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
                viewModel.onErrorShown()
            }

            navigateToMain?.let {
                val mainActivityIntent = Intent(this, MainActivity::class.java)
                startActivity(mainActivityIntent)
                finish()
                viewModel.onNavigated()
            }
        }
    }

    override fun requestOrientationChange() {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER
    }
}

