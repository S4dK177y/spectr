package app.spectr.mood.data.local.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

internal val Context.spectrPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "spectr_settings"
)
