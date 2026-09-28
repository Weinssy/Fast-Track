package com.wein.fasttrack.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.wein.fasttrack.MainActivity
import com.wein.fasttrack.data.AppDatabase
import com.wein.fasttrack.repository.ExpenseRepository
import com.wein.fasttrack.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.text.NumberFormat
import java.util.Locale

class FastTrackWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val database = AppDatabase.getDatabase(context)
        val repository = ExpenseRepository(database.expenseDao(), database.tagDao())
        val userPrefs = UserPreferencesRepository(context)
        val todayTotal = try {
            repository.getTodayTotal().first() ?: 0L
        } catch (e: Exception) {
            0L
        }
        val budgetCap = try {
            userPrefs.dailyBudgetCap.first()
        } catch (e: Exception) {
            0L
        }

        provideContent {
            FastTrackWidgetContent(todayTotal, budgetCap)
        }
    }
}

@Composable
fun FastTrackWidgetContent(todayTotal: Long, budgetCap: Long) {
    val currencyFormat = NumberFormat.getCurrencyInstance(Locale("id", "ID")).apply {
        maximumFractionDigits = 0
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(Color(0xFF16161A))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.Start
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(Color(0xFF242629))
                .padding(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "Hari Ini",
                style = TextStyle(
                    color = androidx.glance.unit.ColorProvider(Color(0xFF94A1B2)),
                    fontSize = 14.sp
                )
            )
            val amountColor = when {
                budgetCap == 0L -> Color(0xFFFFFFFE)
                todayTotal >= budgetCap -> Color(0xFFE53E3E)
                todayTotal >= budgetCap * 0.8 -> Color(0xFFF6AD55)
                else -> Color(0xFFFFFFFE)
            }
            
            Text(
                text = currencyFormat.format(todayTotal),
                style = TextStyle(
                    color = androidx.glance.unit.ColorProvider(amountColor),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = GlanceModifier.padding(top = 4.dp, bottom = 12.dp)
            )
            Row(
                modifier = GlanceModifier
                    .background(Color(0xFF2CB67D))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clickable(actionStartActivity(android.content.Intent(androidx.glance.LocalContext.current, MainActivity::class.java))),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "+ Catat",
                    style = TextStyle(
                        color = androidx.glance.unit.ColorProvider(Color.White),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }
    }
}

class FastTrackWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FastTrackWidget()
}
