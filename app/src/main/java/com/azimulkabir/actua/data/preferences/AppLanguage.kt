package com.azimulkabir.actua.data.preferences

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.core.app.LocaleManagerCompat

/**
 * AppCompat applies app locales to activities. Receivers and workers need an
 * explicit configuration context on Android versions before framework-managed
 * per-app languages.
 */
fun Context.withAppLanguage(): Context {
    val languageTags = LocaleManagerCompat.getApplicationLocales(this).toLanguageTags()
    if (languageTags.isBlank()) return this

    val configuration = Configuration(resources.configuration).apply {
        setLocales(LocaleList.forLanguageTags(languageTags))
    }
    return createConfigurationContext(configuration)
}
