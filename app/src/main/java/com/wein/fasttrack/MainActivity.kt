package com.wein.fasttrack

import android.os.Bundle
import android.view.WindowManager
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.wein.fasttrack.data.AppDatabase
import com.wein.fasttrack.repository.ExpenseRepository
import com.wein.fasttrack.ui.FastTrackScreen
import com.wein.fasttrack.ui.theme.FastTrackTheme
import com.wein.fasttrack.viewmodel.ExpenseViewModel
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import com.wein.fasttrack.ui.AnalyticsScreen
import com.wein.fasttrack.viewmodel.AnalyticsViewModel
import com.wein.fasttrack.viewmodel.AnalyticsViewModelFactory
import com.wein.fasttrack.viewmodel.ExpenseViewModelFactory
import com.wein.fasttrack.utils.BiometricAuthManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import android.content.Intent

enum class Screen { MAIN, ANALYTICS }

class MainActivity : FragmentActivity() {
    private val currentScreenState = MutableStateFlow(Screen.MAIN)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        handleIntent(intent)
        
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = ExpenseRepository(database, database.expenseDao(), database.tagDao())
        
        val userPrefs = com.wein.fasttrack.repository.UserPreferencesRepository(applicationContext)
        val expenseFactory = ExpenseViewModelFactory(application, repository, userPrefs)
        val expenseViewModel = ViewModelProvider(this, expenseFactory)[ExpenseViewModel::class.java]
        
        val analyticsFactory = AnalyticsViewModelFactory(repository)
        val analyticsViewModel = ViewModelProvider(this, analyticsFactory)[AnalyticsViewModel::class.java]

        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP || event == Lifecycle.Event.ON_START) {
                BiometricAuthManager.checkSessionValidity()
            }
        })
        
        lifecycleScope.launch {
            userPrefs.isBiometricEnabled.collect { isEnabled ->
                if (isEnabled) {
                    window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }

        setContent {
            val currentScreen by currentScreenState.collectAsState()
            val isBiometricEnabled by userPrefs.isBiometricEnabled.collectAsState(initial = false)
            val isAppUnlocked by BiometricAuthManager.isAppUnlocked.collectAsState()

            FastTrackTheme {
                if (isBiometricEnabled && !isAppUnlocked) {
                    com.wein.fasttrack.ui.BiometricLockScreen(
                        onUnlockSuccess = { BiometricAuthManager.markUnlocked() },
                        onUnlockRequested = {
                            BiometricAuthManager.authenticate(
                                activity = this@MainActivity,
                                onSuccess = { BiometricAuthManager.markUnlocked() },
                                onError = { /* UI can handle or ignore */ }
                            )
                        }
                    )
                } else {
                    Crossfade(targetState = currentScreen, label = "ScreenTransition") { screen ->
                        when (screen) {
                            Screen.MAIN -> {
                                FastTrackScreen(
                                    viewModel = expenseViewModel,
                                    onNavigateToAnalytics = { currentScreenState.value = Screen.ANALYTICS },
                                    activity = this@MainActivity
                                )
                            }
                            Screen.ANALYTICS -> {
                                AnalyticsScreen(
                                    viewModel = analyticsViewModel,
                                    onNavigateBack = { currentScreenState.value = Screen.MAIN }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.getStringExtra("shortcut")?.let { shortcut ->
            when (shortcut) {
                "entry" -> currentScreenState.value = Screen.MAIN
                "analytics" -> currentScreenState.value = Screen.ANALYTICS
            }
        }
    }
}
