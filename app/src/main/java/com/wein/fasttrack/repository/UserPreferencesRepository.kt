package com.wein.fasttrack.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class UserPreferencesRepository(private val context: Context) {

    private val DAILY_BUDGET_CAP = longPreferencesKey("daily_budget_cap")

    val dailyBudgetCap: Flow<Long> = context.dataStore.data
        .map { preferences ->
            preferences[DAILY_BUDGET_CAP] ?: 0L
        }

    suspend fun setDailyBudgetCap(amount: Long) {
        context.dataStore.edit { preferences ->
            preferences[DAILY_BUDGET_CAP] = amount
        }
    }
}
