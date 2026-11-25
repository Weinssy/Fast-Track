package com.wein.fasttrack.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode {
    SYSTEM,
    DARK,
    LIGHT
}

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class UserPreferencesRepository(private val context: Context) {

    private val DAILY_BUDGET_CAP = longPreferencesKey("daily_budget_cap")
    private val MONTHLY_BUDGET_CAP = longPreferencesKey("monthly_budget_cap")
    private val IS_BIOMETRIC_ENABLED = booleanPreferencesKey("is_biometric_enabled")
    private val THEME_MODE = stringPreferencesKey("theme_mode")
    private val LAST_UPDATE_CHECK_AT = longPreferencesKey("last_update_check_at")
    private val IGNORED_UPDATE_VERSION = stringPreferencesKey("ignored_update_version")

    val dailyBudgetCap: Flow<Long> = context.dataStore.data
        .map { preferences ->
            preferences[DAILY_BUDGET_CAP] ?: 0L
        }

    val monthlyBudgetCap: Flow<Long> = context.dataStore.data
        .map { preferences ->
            preferences[MONTHLY_BUDGET_CAP] ?: 0L
        }

    val isBiometricEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[IS_BIOMETRIC_ENABLED] ?: false
        }

    val themeMode: Flow<ThemeMode> = context.dataStore.data
        .map { preferences ->
            val raw = preferences[THEME_MODE]
            if (raw.isNullOrBlank()) {
                ThemeMode.DARK
            } else {
                runCatching { ThemeMode.valueOf(raw) }.getOrDefault(ThemeMode.DARK)
            }
        }

    val lastUpdateCheckAt: Flow<Long> = context.dataStore.data
        .map { preferences ->
            preferences[LAST_UPDATE_CHECK_AT] ?: 0L
        }

    val ignoredUpdateVersion: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[IGNORED_UPDATE_VERSION]?.takeIf { it.isNotBlank() }
        }

    suspend fun setDailyBudgetCap(amount: Long) {
        context.dataStore.edit { preferences ->
            preferences[DAILY_BUDGET_CAP] = amount
        }
    }

    suspend fun setMonthlyBudgetCap(amount: Long) {
        context.dataStore.edit { preferences ->
            preferences[MONTHLY_BUDGET_CAP] = amount
        }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_BIOMETRIC_ENABLED] = enabled
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE] = mode.name
        }
    }

    suspend fun setLastUpdateCheckAt(timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[LAST_UPDATE_CHECK_AT] = timestamp
        }
    }

    suspend fun setIgnoredUpdateVersion(version: String?) {
        context.dataStore.edit { preferences ->
            val trimmed = version?.trim()
            if (trimmed.isNullOrEmpty()) {
                preferences.remove(IGNORED_UPDATE_VERSION)
            } else {
                preferences[IGNORED_UPDATE_VERSION] = trimmed
            }
        }
    }
}
