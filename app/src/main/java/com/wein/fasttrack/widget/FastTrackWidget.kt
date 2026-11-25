package com.wein.fasttrack.widget

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.wein.fasttrack.MainActivity
import com.wein.fasttrack.R
import com.wein.fasttrack.data.AppDatabase
import com.wein.fasttrack.repository.ExpenseRepository
import com.wein.fasttrack.repository.ThemeMode
import com.wein.fasttrack.repository.UserPreferencesRepository
import com.wein.fasttrack.ui.theme.AccentPrimary
import com.wein.fasttrack.ui.theme.BackgroundDark
import com.wein.fasttrack.ui.theme.BackgroundLight
import com.wein.fasttrack.ui.theme.ErrorDark
import com.wein.fasttrack.ui.theme.ErrorLight
import com.wein.fasttrack.ui.theme.OnPrimaryLight
import com.wein.fasttrack.ui.theme.OnPrimaryText
import com.wein.fasttrack.ui.theme.PrimaryLight
import com.wein.fasttrack.ui.theme.SurfaceDark
import com.wein.fasttrack.ui.theme.SurfaceLight
import com.wein.fasttrack.ui.theme.TextPrimary
import com.wein.fasttrack.ui.theme.TextPrimaryLight
import com.wein.fasttrack.ui.theme.TextSecondary
import com.wein.fasttrack.ui.theme.TextSecondaryLight
import com.wein.fasttrack.ui.theme.WarningDark
import com.wein.fasttrack.ui.theme.WarningLight
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.flow.first

class FastTrackWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val database = AppDatabase.getDatabase(context)
        val repository = ExpenseRepository(database, database.expenseDao(), database.tagDao())
        val userPrefs = UserPreferencesRepository(context)

        val todayTotal = runCatching {
            repository.getTodayTotal().first() ?: 0L
        }.getOrDefault(0L)

        val budgetCap = runCatching {
            userPrefs.dailyBudgetCap.first()
        }.getOrDefault(0L)

        val themeMode = runCatching {
            userPrefs.themeMode.first()
        }.getOrDefault(ThemeMode.DARK)

        val isSystemDark =
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        val isDarkTheme = when (themeMode) {
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
            ThemeMode.SYSTEM -> isSystemDark
        }

        provideContent {
            FastTrackWidgetContent(
                todayTotal = todayTotal,
                budgetCap = budgetCap,
                isDarkTheme = isDarkTheme
            )
        }
    }
}

@Composable
fun FastTrackWidgetContent(
    todayTotal: Long,
    budgetCap: Long,
    isDarkTheme: Boolean = true
) {
    val context = LocalContext.current
    val currencyFormat = NumberFormat.getCurrencyInstance(Locale("id", "ID")).apply {
        maximumFractionDigits = 0
    }

    val containerBg = if (isDarkTheme) BackgroundDark else BackgroundLight
    val cardBg = if (isDarkTheme) SurfaceDark else SurfaceLight
    val labelColor = if (isDarkTheme) TextSecondary else TextSecondaryLight
    val normalTextColor = if (isDarkTheme) TextPrimary else TextPrimaryLight
    val primaryBg = if (isDarkTheme) AccentPrimary else PrimaryLight
    val onPrimaryColor = if (isDarkTheme) OnPrimaryText else OnPrimaryLight
    val errorColor = if (isDarkTheme) ErrorDark else ErrorLight
    val warningColor = if (isDarkTheme) WarningDark else WarningLight

    val isExceeded = budgetCap > 0L && todayTotal >= budgetCap
    val isWarning = budgetCap > 0L && !isExceeded && todayTotal >= (budgetCap * 0.8)

    val amountColor = when {
        isExceeded -> errorColor
        isWarning -> warningColor
        else -> normalTextColor
    }

    val capStatusText = when {
        budgetCap <= 0L -> null
        isExceeded -> context.getString(R.string.widget_budget_exceeded)
        isWarning -> context.getString(R.string.widget_budget_warning)
        else -> context.getString(
            R.string.widget_budget_remaining,
            currencyFormat.format(budgetCap - todayTotal)
        )
    }

    val capStatusColor = when {
        isExceeded -> errorColor
        isWarning -> warningColor
        else -> labelColor
    }

    val entryIntent = Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        putExtra("shortcut", "entry")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(containerBg)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.Start
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .cornerRadius(16.dp)
                .background(cardBg)
                .padding(14.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = context.getString(R.string.today_total),
                style = TextStyle(
                    color = ColorProvider(labelColor),
                    fontSize = 13.sp
                )
            )

            Text(
                text = currencyFormat.format(todayTotal),
                maxLines = 1,
                style = TextStyle(
                    color = ColorProvider(amountColor),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = GlanceModifier.padding(top = 4.dp)
            )

            if (capStatusText != null) {
                Text(
                    text = capStatusText,
                    maxLines = 1,
                    style = TextStyle(
                        color = ColorProvider(capStatusColor),
                        fontSize = 11.sp
                    ),
                    modifier = GlanceModifier.padding(top = 2.dp)
                )
            }

            Row(
                modifier = GlanceModifier
                    .padding(top = 10.dp)
                    .cornerRadius(10.dp)
                    .background(primaryBg)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .clickable(actionStartActivity(entryIntent)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = context.getString(R.string.widget_add_expense),
                    style = TextStyle(
                        color = ColorProvider(onPrimaryColor),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}

class FastTrackWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FastTrackWidget()
}
