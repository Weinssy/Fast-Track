package com.wein.fasttrack.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class UserPreferencesRepository(private val context: Context) {

    private val DAILY_BUDGET_CAP = longPreferencesKey("daily_budget_cap")
    private val IS_BIOMETRIC_ENABLED = booleanPreferencesKey("is_biometric_enabled")

    val dailyBudgetCap: Flow<Long> = context.dataStore.data
        .map { preferences ->
            preferences[DAILY_BUDGET_CAP] ?: 0L
        }

    val isBiometricEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[IS_BIOMETRIC_ENABLED] ?: false
        }

    suspend fun setDailyBudgetCap(amount: Long) {
        context.dataStore.edit { preferences ->
            preferences[DAILY_BUDGET_CAP] = amount
        }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_BIOMETRIC_ENABLED] = enabled
        }
    }
}
