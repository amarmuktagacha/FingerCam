package com.shohan.fingercam.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

const val DEFAULT_THRESHOLD = 32
const val MIN_THRESHOLD = 20
const val MAX_THRESHOLD = 80

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {

    private val thresholdKey = intPreferencesKey("match_threshold")

    val threshold: Flow<Int> = context.settingsDataStore.data.map { prefs ->
        (prefs[thresholdKey] ?: DEFAULT_THRESHOLD).coerceIn(MIN_THRESHOLD, MAX_THRESHOLD)
    }

    suspend fun setThreshold(value: Int) {
        context.settingsDataStore.edit { prefs ->
            prefs[thresholdKey] = value.coerceIn(MIN_THRESHOLD, MAX_THRESHOLD)
        }
    }
}
