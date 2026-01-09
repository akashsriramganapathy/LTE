package com.github.libretube.test.ui.preferences

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import com.github.libretube.test.R
import com.github.libretube.test.constants.PreferenceKeys
import com.github.libretube.test.helpers.PreferenceHelper
import com.github.libretube.test.ui.base.BasePreferenceFragment
import com.github.libretube.test.extensions.toastFromMainThread

class DownloadSettings : BasePreferenceFragment() {
    override val titleResourceId: Int = R.string.downloads



    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        setPreferencesFromResource(R.xml.download_settings, rootKey)

        setupStoragePreferences()
        
        // Listen for Concurrency Changes
        val concurrencyPref = findPreference<com.github.libretube.test.ui.preferences.SeekBarPreference>(PreferenceKeys.MAX_CONCURRENT_DOWNLOADS)
        concurrencyPref?.setOnPreferenceChangeListener { _, newValue ->
             val count = (newValue as Int)
             com.github.libretube.test.helpers.DownloadManager.updateConcurrency(count)
             true
        }
    }

    private fun setupStoragePreferences() {
        val externalProvider = findPreference<androidx.preference.EditTextPreference>(PreferenceKeys.EXTERNAL_DOWNLOAD_PROVIDER)
        val mediaVisible = findPreference<SwitchPreferenceCompat>(PreferenceKeys.MEDIA_VISIBLE)

        fun updateVisibility(hasProvider: Boolean) {
            mediaVisible?.isVisible = hasProvider
        }

        externalProvider?.setOnPreferenceChangeListener { _, newValue ->
            val packageName = newValue as String
            updateVisibility(packageName.isNotEmpty())
            true
        }

        // Initial State
        val currentPackage = PreferenceHelper.getString(PreferenceKeys.EXTERNAL_DOWNLOAD_PROVIDER, "")
        updateVisibility(currentPackage.isNotEmpty())


    }
}
