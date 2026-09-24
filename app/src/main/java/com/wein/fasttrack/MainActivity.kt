package com.wein.fasttrack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
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
import com.wein.fasttrack.ui.AnalyticsScreen
import com.wein.fasttrack.viewmodel.AnalyticsViewModel
import com.wein.fasttrack.viewmodel.AnalyticsViewModelFactory
import com.wein.fasttrack.viewmodel.ExpenseViewModelFactory

enum class Screen { MAIN, ANALYTICS }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = ExpenseRepository(database.expenseDao(), database.tagDao())
        
        val expenseFactory = ExpenseViewModelFactory(repository)
        val expenseViewModel = ViewModelProvider(this, expenseFactory)[ExpenseViewModel::class.java]
        
        val analyticsFactory = AnalyticsViewModelFactory(repository)
        val analyticsViewModel = ViewModelProvider(this, analyticsFactory)[AnalyticsViewModel::class.java]

        setContent {
            var currentScreen by remember { mutableStateOf(Screen.MAIN) }

            FastTrackTheme {
                Crossfade(targetState = currentScreen, label = "ScreenTransition") { screen ->
                    when (screen) {
                        Screen.MAIN -> {
                            FastTrackScreen(
                                viewModel = expenseViewModel,
                                onNavigateToAnalytics = { currentScreen = Screen.ANALYTICS }
                            )
                        }
                        Screen.ANALYTICS -> {
                            AnalyticsScreen(
                                viewModel = analyticsViewModel,
                                onNavigateBack = { currentScreen = Screen.MAIN }
                            )
                        }
                    }
                }
            }
        }
    }
}
