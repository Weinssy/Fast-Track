package com.wein.fasttrack.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wein.fasttrack.R
import com.wein.fasttrack.ui.theme.WarningDark
import com.wein.fasttrack.ui.theme.WarningLight
import com.wein.fasttrack.viewmodel.AnalyticsPeriod
import com.wein.fasttrack.viewmodel.AnalyticsViewModel
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    viewModel: AnalyticsViewModel,
    onNavigateBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val currencyFormat = remember {
        NumberFormat.getCurrencyInstance(Locale("id", "ID")).apply {
            maximumFractionDigits = 0
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadAnalytics()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.analytics), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            val periodTitle = when (uiState.selectedPeriod) {
                AnalyticsPeriod.SEVEN_DAYS -> stringResource(R.string.total_this_week)
                AnalyticsPeriod.THIS_MONTH -> stringResource(R.string.monthly_total)
                AnalyticsPeriod.LAST_MONTH -> "Total Bulan Lalu"
            }
            val trendTitle = when (uiState.selectedPeriod) {
                AnalyticsPeriod.SEVEN_DAYS -> stringResource(R.string.daily_trend)
                AnalyticsPeriod.THIS_MONTH,
                AnalyticsPeriod.LAST_MONTH -> "Tren Mingguan"
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AnalyticsPeriod.entries.forEach { period ->
                            FilterChip(
                                selected = uiState.selectedPeriod == period,
                                onClick = { viewModel.selectPeriod(period) },
                                label = { Text(period.label) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                item {
                    MonthlyBudgetProgressCard(
                        currentMonthTotal = uiState.currentMonthTotal,
                        monthlyBudgetCap = uiState.monthlyBudgetCap,
                        currencyFormat = currencyFormat
                    )
                }

                item {
                    MetricsCard(
                        periodLabel = periodTitle,
                        periodTotal = currencyFormat.format(uiState.periodTotal),
                        dailyAverage = currencyFormat.format(uiState.dailyAverage),
                        comparisonPercent = uiState.comparisonPercent,
                        previousLabel = when (uiState.selectedPeriod) {
                            AnalyticsPeriod.SEVEN_DAYS -> "7 hari sebelumnya"
                            AnalyticsPeriod.THIS_MONTH -> "bulan lalu"
                            AnalyticsPeriod.LAST_MONTH -> "2 bulan lalu"
                        }
                    )
                }

                item {
                    Column {
                        Text(
                            text = trendTitle,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            CustomBarChart(
                                dailyTotals = uiState.dailyTotals,
                                maxAmount = uiState.maxDailyAmount,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }

                item {
                    Text(
                        text = stringResource(R.string.category_breakdown),
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                }

                if (uiState.tagTotals.isEmpty()) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Text(
                                text = "Belum ada pengeluaran pada periode ini.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                } else {
                    items(uiState.tagTotals) { tagTotal ->
                        val fraction = if (uiState.periodTotal > 0L) {
                            (tagTotal.totalAmount.toFloat() / uiState.periodTotal.toFloat()).coerceIn(0f, 1f)
                        } else {
                            0f
                        }

                        TagDistributionItem(
                            tag = tagTotal.tag ?: stringResource(R.string.tag_umum),
                            amount = currencyFormat.format(tagTotal.totalAmount),
                            percentage = fraction
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun MonthlyBudgetProgressCard(
    currentMonthTotal: Long,
    monthlyBudgetCap: Long,
    currencyFormat: NumberFormat
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val warningColor = if (isDark) WarningDark else WarningLight

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.monthly_budget),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (monthlyBudgetCap > 0L) {
                    val pct = ((currentMonthTotal.toDouble() / monthlyBudgetCap.toDouble()) * 100).toInt()
                    Text(
                        text = "$pct%",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            currentMonthTotal >= monthlyBudgetCap -> MaterialTheme.colorScheme.error
                            currentMonthTotal >= monthlyBudgetCap * 0.8 -> warningColor
                            else -> MaterialTheme.colorScheme.primary
                        }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = currencyFormat.format(currentMonthTotal),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (monthlyBudgetCap > 0L) {
                        "/ ${currencyFormat.format(monthlyBudgetCap)}"
                    } else {
                        "Target belum diatur"
                    },
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (monthlyBudgetCap > 0L) {
                val progress = (currentMonthTotal.toFloat() / monthlyBudgetCap.toFloat()).coerceIn(0f, 1f)
                val barColor = when {
                    currentMonthTotal >= monthlyBudgetCap -> MaterialTheme.colorScheme.error
                    currentMonthTotal >= monthlyBudgetCap * 0.8 -> warningColor
                    else -> MaterialTheme.colorScheme.primary
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = barColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                val remaining = monthlyBudgetCap - currentMonthTotal
                Text(
                    text = if (remaining >= 0L) {
                        "Sisa anggaran bulan ini: ${currencyFormat.format(remaining)}"
                    } else {
                        "Melebihi target sebesar ${currencyFormat.format(-remaining)}"
                    },
                    fontSize = 12.sp,
                    color = if (remaining >= 0L) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun MetricsCard(
    periodLabel: String,
    periodTotal: String,
    dailyAverage: String,
    comparisonPercent: Int? = null,
    previousLabel: String = "",
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = periodLabel,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = periodTotal,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = stringResource(R.string.daily_average),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = dailyAverage,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (comparisonPercent != null) {
                val isIncrease = comparisonPercent > 0
                val isDecrease = comparisonPercent < 0
                val badgeBg = when {
                    isIncrease -> MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
                    isDecrease -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
                val badgeTextColor = when {
                    isIncrease -> MaterialTheme.colorScheme.error
                    isDecrease -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                val sign = if (comparisonPercent > 0) "+" else if (comparisonPercent < 0) "-" else ""
                val badgeText = "$sign${abs(comparisonPercent)}% dibanding $previousLabel"

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(badgeBg)
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = badgeTextColor
                    )
                }
            }
        }
    }
}

@Composable
fun TagDistributionItem(
    tag: String,
    amount: String,
    percentage: Float,
    modifier: Modifier = Modifier
) {
    val pctDisplay = (percentage * 100).toInt()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$tag ($pctDisplay%)",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = amount,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(percentage.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}
