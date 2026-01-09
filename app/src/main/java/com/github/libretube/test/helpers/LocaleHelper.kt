package com.github.libretube.test.helpers

import android.content.Context
import android.telephony.TelephonyManager
import androidx.core.content.getSystemService
import androidx.core.os.ConfigurationCompat
import com.github.libretube.test.constants.PreferenceKeys
import com.github.libretube.test.obj.Country
import java.util.Locale

object LocaleHelper {

    fun getDetectedCountry(context: Context): String {
        return detectSIMCountry(context)
            ?: detectNetworkCountry(context)
            ?: detectLocaleCountry(context)
            ?: "UK"
    }

    private fun detectSIMCountry(context: Context): String? {
        return context.getSystemService<TelephonyManager>()?.simCountryIso?.ifEmpty { null }
    }

    private fun detectNetworkCountry(context: Context): String? {
        return context.getSystemService<TelephonyManager>()?.networkCountryIso?.ifEmpty { null }
    }

    private fun detectLocaleCountry(context: Context): String? {
        return ConfigurationCompat.getLocales(context.resources.configuration)[0]!!.country
            .ifEmpty { null }
    }

    fun getAvailableCountries(): List<Country> {
        return Locale.getISOCountries()
            .map { Country(Locale("", it).displayCountry, it) }
            .sortedBy { it.name }
    }

    fun getAvailableLocales(): List<Country> {
        return Locale.getAvailableLocales()
            .distinctBy { it.language }
            .map { Country(it.displayLanguage, it.language) }
            .sortedBy { it.name }
    }
}

