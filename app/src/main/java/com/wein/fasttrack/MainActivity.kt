package com.wein.fasttrack

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.wein.fasttrack.data.AppDatabase
import com.wein.fasttrack.repository.ExpenseRepository
import com.wein.fasttrack.repository.SplitBillRepository
import com.wein.fasttrack.repository.ThemeMode
import com.wein.fasttrack.repository.UserPreferencesRepository
import com.wein.fasttrack.ui.AnalyticsScreen
import com.wein.fasttrack.ui.BiometricLockScreen
import com.wein.fasttrack.ui.FastTrackScreen
import com.wein.fasttrack.ui.HistoryScreen
import com.wein.fasttrack.ui.SplitBillScreen
import com.wein.fasttrack.ui.UpdateDialog
import com.wein.fasttrack.ui.theme.FastTrackTheme
import com.wein.fasttrack.utils.AppUpdateManager
import com.wein.fasttrack.utils.BiometricAuthManager
import com.wein.fasttrack.utils.GitHubReleaseInfo
import com.wein.fasttrack.utils.UpdateCheckResult
import com.wein.fasttrack.viewmodel.AnalyticsViewModel
import com.wein.fasttrack.viewmodel.AnalyticsViewModelFactory
import com.wein.fasttrack.viewmodel.ExpenseViewModel
import com.wein.fasttrack.viewmodel.ExpenseViewModelFactory
import com.wein.fasttrack.viewmodel.HistoryViewModel
import com.wein.fasttrack.viewmodel.HistoryViewModelFactory
import com.wein.fasttrack.viewmodel.SplitBillViewModel
import com.wein.fasttrack.viewmodel.SplitBillViewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class Screen { MAIN, SPLIT_BILL, HISTORY, ANALYTICS }

class MainActivity : FragmentActivity() {
    private val currentScreenState = MutableStateFlow(Screen.MAIN)

    @OptIn(ExperimentalLayoutApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleIntent(intent)

        val database = AppDatabase.getDatabase(applicationContext)
        val repository = ExpenseRepository(database, database.expenseDao(), database.tagDao())
        val splitBillRepository = SplitBillRepository(applicationContext, database)
        val userPrefs = UserPreferencesRepository(applicationContext)

        val expenseFactory = ExpenseViewModelFactory(application, repository, userPrefs, splitBillRepository)
        val expenseViewModel = ViewModelProvider(this, expenseFactory)[ExpenseViewModel::class.java]

        val splitBillFactory = SplitBillViewModelFactory(application, splitBillRepository, repository)
        val splitBillViewModel = ViewModelProvider(this, splitBillFactory)[SplitBillViewModel::class.java]

        val historyFactory = HistoryViewModelFactory(application, repository)
        val historyViewModel = ViewModelProvider(this, historyFactory)[HistoryViewModel::class.java]

        val analyticsFactory = AnalyticsViewModelFactory(repository, userPrefs)
        val analyticsViewModel = ViewModelProvider(this, analyticsFactory)[AnalyticsViewModel::class.java]

        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP || event == Lifecycle.Event.ON_START) {
                BiometricAuthManager.checkSessionValidity()
            }
        })

        lifecycleScope.launch {
            userPrefs.isBiometricEnabled.collect { isEnabled ->
                if (isEnabled) {
                    window.setFlags(
                        WindowManager.LayoutParams.FLAG_SECURE,
                        WindowManager.LayoutParams.FLAG_SECURE
                    )
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }

        setContent {
            val currentScreen by currentScreenState.collectAsState()
            val isBiometricEnabled by userPrefs.isBiometricEnabled.collectAsState(initial = false)
            val isAppUnlocked by BiometricAuthManager.isAppUnlocked.collectAsState()
            val themeMode by userPrefs.themeMode.collectAsState(initial = ThemeMode.DARK)
            val expenseUiState by expenseViewModel.uiState.collectAsState()

            var startupUpdateRelease by remember { mutableStateOf<GitHubReleaseInfo?>(null) }

            LaunchedEffect(Unit) {
                runCatching { AppUpdateManager.cleanupOldUpdates(applicationContext) }
                runCatching { repository.applyDueRecurringExpenses(applicationContext) }

                val now = System.currentTimeMillis()
                val lastCheckAt = userPrefs.lastUpdateCheckAt.first()
                val ignoredVersion = userPrefs.ignoredUpdateVersion.first()
                if (now - lastCheckAt >= UPDATE_CHECK_INTERVAL_MS) {
                    val result = AppUpdateManager.checkForUpdate(BuildConfig.VERSION_NAME)
                    userPrefs.setLastUpdateCheckAt(now)
                    result.onSuccess { checkResult ->
                        if (checkResult is UpdateCheckResult.UpdateAvailable &&
                            checkResult.release.versionName != ignoredVersion
                        ) {
                            startupUpdateRelease = checkResult.release
                        }
                    }
                }
            }

            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> systemDark
            }

            FastTrackTheme(darkTheme = darkTheme) {
                if (isBiometricEnabled && !isAppUnlocked) {
                    BiometricLockScreen(
                        onUnlockSuccess = { BiometricAuthManager.markUnlocked() },
                        onUnlockRequested = {
                            BiometricAuthManager.authenticate(
                                activity = this@MainActivity,
                                onSuccess = { BiometricAuthManager.markUnlocked() },
                                onError = { /* UI handles retry via button */ }
                            )
                        }
                    )
                } else {
                    val openSplitBillSession: (Long) -> Unit = { sessionId ->
                        splitBillViewModel.selectSession(sessionId)
                        currentScreenState.value = Screen.SPLIT_BILL
                    }

                    Scaffold(
                        bottomBar = {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ) {
                                val itemColors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                NavigationBarItem(
                                    selected = currentScreen == Screen.MAIN,
                                    onClick = { currentScreenState.value = Screen.MAIN },
                                    icon = {
                                        Icon(
                                            imageVector = Icons.Default.Create,
                                            contentDescription = stringResource(R.string.nav_entry)
                                        )
                                    },
                                    label = { Text(stringResource(R.string.nav_entry)) },
                                    colors = itemColors
                                )

                                NavigationBarItem(
                                    selected = currentScreen == Screen.SPLIT_BILL,
                                    onClick = { currentScreenState.value = Screen.SPLIT_BILL },
                                    icon = {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = stringResource(R.string.nav_split_bill)
                                        )
                                    },
                                    label = { Text(stringResource(R.string.nav_split_bill)) },
                                    colors = itemColors
                                )

                                NavigationBarItem(
                                    selected = currentScreen == Screen.HISTORY,
                                    onClick = { currentScreenState.value = Screen.HISTORY },
                                    icon = {
                                        Icon(
                                            imageVector = Icons.Default.List,
                                            contentDescription = stringResource(R.string.nav_history)
                                        )
                                    },
                                    label = { Text(stringResource(R.string.nav_history)) },
                                    colors = itemColors
                                )

                                NavigationBarItem(
                                    selected = currentScreen == Screen.ANALYTICS,
                                    onClick = { currentScreenState.value = Screen.ANALYTICS },
                                    icon = {
                                        Icon(
                                            imageVector = AnalyticsBarChartIcon,
                                            contentDescription = stringResource(R.string.nav_analytics)
                                        )
                                    },
                                    label = { Text(stringResource(R.string.nav_analytics)) },
                                    colors = itemColors
                                )
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.background
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                                .consumeWindowInsets(innerPadding)
                        ) {
                            Crossfade(targetState = currentScreen, label = "ScreenTransition") { screen ->
                                when (screen) {
                                    Screen.MAIN -> {
                                        FastTrackScreen(
                                            viewModel = expenseViewModel,
                                            activity = this@MainActivity,
                                            onOpenSplitBillSession = openSplitBillSession,
                                            onNavigateToAnalytics = {
                                                currentScreenState.value = Screen.ANALYTICS
                                            }
                                        )
                                    }
                                    Screen.SPLIT_BILL -> {
                                        SplitBillScreen(viewModel = splitBillViewModel)
                                    }
                                    Screen.HISTORY -> {
                                        HistoryScreen(
                                            viewModel = historyViewModel,
                                            onOpenSplitBillSession = openSplitBillSession
                                        )
                                    }
                                    Screen.ANALYTICS -> {
                                        AnalyticsScreen(viewModel = analyticsViewModel)
                                    }
                                }
                            }
                        }
                    }

                    val activeRelease = expenseUiState.availableUpdateRelease ?: startupUpdateRelease
                    if (activeRelease != null) {
                        UpdateDialog(
                            currentVersion = BuildConfig.VERSION_NAME,
                            release = activeRelease,
                            onDismiss = {
                                startupUpdateRelease = null
                                expenseViewModel.dismissUpdateDialog()
                            },
                            onSkipVersion = { skippedVersion ->
                                startupUpdateRelease = null
                                expenseViewModel.ignoreUpdateVersion(skippedVersion)
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.getStringExtra("shortcut")?.let { shortcut ->
            when (shortcut) {
                "entry" -> currentScreenState.value = Screen.MAIN
                "split_bill" -> currentScreenState.value = Screen.SPLIT_BILL
                "history" -> currentScreenState.value = Screen.HISTORY
                "analytics" -> currentScreenState.value = Screen.ANALYTICS
            }
        }
    }

    private companion object {
        const val UPDATE_CHECK_INTERVAL_MS = 12L * 3600L * 1000L
    }
}

private val AnalyticsBarChartIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "AnalyticsBarChart",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(4f, 19f)
            lineTo(4f, 13f)
            lineTo(8f, 13f)
            lineTo(8f, 19f)
            close()

            moveTo(10f, 19f)
            lineTo(10f, 5f)
            lineTo(14f, 5f)
            lineTo(14f, 19f)
            close()

            moveTo(16f, 19f)
            lineTo(16f, 9f)
            lineTo(20f, 9f)
            lineTo(20f, 19f)
            close()
        }
    }.build()
}
