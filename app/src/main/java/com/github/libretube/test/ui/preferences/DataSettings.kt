package com.github.libretube.test.ui.preferences

import android.os.Bundle
import androidx.core.app.ActivityCompat
import androidx.preference.ListPreference
import androidx.preference.Preference
import com.github.libretube.test.R
import com.github.libretube.test.constants.PreferenceKeys
import com.github.libretube.test.helpers.ImageHelper
import com.github.libretube.test.helpers.PreferenceHelper
import com.github.libretube.test.ui.base.BasePreferenceFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.github.libretube.test.extensions.toastFromMainThread
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DataSettings : BasePreferenceFragment() {
    override val titleResourceId: Int = R.string.data_backup

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        setPreferencesFromResource(R.xml.data_settings, rootKey)

        val maxImageCache = findPreference<ListPreference>(PreferenceKeys.MAX_IMAGE_CACHE)
        maxImageCache?.setOnPreferenceChangeListener { _, _ ->
            ImageHelper.initializeImageLoader(requireContext())
            true
        }

        findPreference<Preference>(PreferenceKeys.RESET_SETTINGS)?.setOnPreferenceClickListener {
            showResetDialog()
            true
        }




    }



    private fun showResetDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.reset)
            .setMessage(R.string.reset_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.reset) { _, _ ->
                // clear default preferences
                PreferenceHelper.clearPreferences()

                ActivityCompat.recreate(requireActivity())
            }
            .show()
    }
}
