package com.github.libretube.test.ui.preferences

import android.os.Build
import android.os.Bundle
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.preference.ListPreference
import androidx.preference.Preference
import com.github.libretube.test.R
import com.github.libretube.test.constants.PreferenceKeys
import com.github.libretube.test.extensions.toastFromMainThread
import com.github.libretube.test.helpers.ImageHelper
import com.github.libretube.test.helpers.LocaleHelper
import com.github.libretube.test.helpers.PreferenceHelper
import com.github.libretube.test.ui.base.BasePreferenceFragment
import com.github.libretube.test.ui.dialogs.RequireRestartDialog
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.github.libretube.test.util.UpdateWorker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.concurrent.TimeUnit

class GeneralSettings : BasePreferenceFragment() {
    override val titleResourceId: Int = R.string.general



    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        setPreferencesFromResource(R.xml.general_settings, rootKey)


        val autoRotation = findPreference<ListPreference>(PreferenceKeys.ORIENTATION)
        autoRotation?.setOnPreferenceChangeListener { _, _ ->
            RequireRestartDialog().show(childFragmentManager, RequireRestartDialog::class.java.name)
            true
        }

        val updateFrequency = findPreference<ListPreference>(PreferenceKeys.CHECKING_FREQUENCY)
        updateFrequency?.setOnPreferenceChangeListener { _, newValue ->
            val frequency = (newValue as String).toInt()
            if (frequency == 15 || frequency == 30) {
                showBatteryDrainWarning(frequency)
            } else {
                scheduleUpdateWorker(frequency)
            }
            true
        }

        val setDefaultApp = findPreference<Preference>(PreferenceKeys.SET_DEFAULT_APP)
        setDefaultApp?.setOnPreferenceClickListener {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                android.content.Intent(android.provider.Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, android.net.Uri.parse("package:${requireContext().packageName}"))
            } else {
                android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${requireContext().packageName}"))
            }
            startActivity(intent)
            true
        }


    }

    private fun showBatteryDrainWarning(frequency: Int) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.battery_drain_warning_title)
            .setMessage(R.string.battery_drain_warning_message)
            .setNegativeButton(R.string.cancel) { _, _ ->
                // Reset to default or previous value if needed
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                scheduleUpdateWorker(frequency)
            }
            .show()
    }

    private fun scheduleUpdateWorker(frequencyInMinutes: Int) {
        val workManager = WorkManager.getInstance(requireContext())
        val workRequest = PeriodicWorkRequest.Builder(
            UpdateWorker::class.java,
            frequencyInMinutes.toLong(),
            TimeUnit.MINUTES
        ).build()

        workManager.enqueueUniquePeriodicWork(
            "UpdateCheck",
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }
}

