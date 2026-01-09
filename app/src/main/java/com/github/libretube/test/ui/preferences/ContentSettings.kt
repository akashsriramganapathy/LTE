package com.github.libretube.test.ui.preferences

import android.os.Bundle
import com.github.libretube.test.R
import com.github.libretube.test.ui.base.BasePreferenceFragment

class ContentSettings : BasePreferenceFragment() {
    override val titleResourceId: Int = R.string.content_settings

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        setPreferencesFromResource(R.xml.content_settings, rootKey)

        findPreference<androidx.preference.EditTextPreference>(com.github.libretube.test.constants.PreferenceKeys.SB_USER_ID)?.setOnPreferenceChangeListener { _, newValue ->
            // Relaxed validation to support:
            // 1. Standard/Simple UUIDs (32-36 chars)
            // 2. Legacy LibreTube IDs (30 chars Alphanumeric)
            // 3. Public Hash/Long IDs (64+ chars)
            // 4. Custom generated IDs
            // Range 20-128 chars, allowed: Alphanumeric and dashes
            val idRegex = Regex("^[a-zA-Z0-9-]{20,128}$")
            if (newValue.toString().isEmpty() || newValue.toString().matches(idRegex)) {
                true
            } else {
                android.widget.Toast.makeText(requireContext(), R.string.invalid_user_id_format, android.widget.Toast.LENGTH_SHORT).show()
                false
            }
        }
    }
}
