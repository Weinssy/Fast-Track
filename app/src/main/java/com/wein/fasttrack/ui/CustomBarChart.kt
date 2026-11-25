package com.wein.fasttrack.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wein.fasttrack.viewmodel.DailyTotal
import java.text.NumberFormat
import java.util.Locale

@Composable
fun CustomBarChart(
    dailyTotals: List<DailyTotal>,
    maxAmount: Long,
    modifier: Modifier = Modifier
) {
    val barColor = MaterialTheme.colorScheme.surfaceVariant
    val highlightColor = MaterialTheme.colorScheme.primary
    val labelColorArgb = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val highlightTextColorArgb = MaterialTheme.colorScheme.onSurface.toArgb()

    var selectedIndex by remember(dailyTotals.size) {
        mutableIntStateOf((dailyTotals.size - 1).coerceAtLeast(0))
    }

    LaunchedEffect(dailyTotals) {
        if (dailyTotals.isNotEmpty() && selectedIndex !in dailyTotals.indices) {
            selectedIndex = dailyTotals.lastIndex
        }
    }

    val density = LocalDensity.current
    val labelPaint = remember(density, labelColorArgb) {
        android.graphics.Paint().apply {
            color = labelColorArgb
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = with(density) { 11.dp.toPx() }
            isAntiAlias = true
        }
    }
    val valuePaint = remember(density, highlightTextColorArgb) {
        android.graphics.Paint().apply {
            color = highlightTextColorArgb
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = with(density) { 10.dp.toPx() }
            isAntiAlias = true
        }
    }

    val currencyFormat = remember {
        NumberFormat.getCurrencyInstance(Locale("id", "ID")).apply {
            maximumFractionDigits = 0
        }
    }

    val selectedEntry = dailyTotals.getOrNull(selectedIndex)

    Column(modifier = modifier.fillMaxWidth()) {
        if (selectedEntry != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Terpilih: ${selectedEntry.dayLabel}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = currencyFormat.format(selectedEntry.totalAmount),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .padding(top = 12.dp, bottom = 8.dp)
                .pointerInput(dailyTotals) {
                    detectTapGestures { tapOffset ->
                        val barCount = dailyTotals.size
                        if (barCount == 0) return@detectTapGestures
                        val slotWidth = size.width.toFloat() / barCount
                        val tapped = (tapOffset.x / slotWidth).toInt().coerceIn(0, barCount - 1)
                        selectedIndex = tapped
                    }
                }
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val barCount = dailyTotals.size
            if (barCount == 0) return@Canvas

            val topValueSpace = 30f
            val bottomLabelSpace = 34f
            val barWidth = (canvasWidth / (barCount * 1.85f)).coerceIn(24f, 68f)
            val spacing = (canvasWidth - (barWidth * barCount)) / (barCount + 1)
            val maxBarHeight = (canvasHeight - topValueSpace - bottomLabelSpace).coerceAtLeast(24f)
            val baselineY = topValueSpace + maxBarHeight

            dailyTotals.forEachIndexed { index, dailyTotal ->
                val xOffset = spacing + (index * (barWidth + spacing))
                val heightRatio = if (maxAmount > 0L) {
                    dailyTotal.totalAmount.toFloat() / maxAmount.toFloat()
                } else {
                    0f
                }
                val barHeight = if (dailyTotal.totalAmount > 0L) {
                    (maxBarHeight * heightRatio).coerceAtLeast(12f)
                } else {
                    8f
                }
                val yOffset = baselineY - barHeight
                val isSelected = index == selectedIndex

                drawRoundRect(
                    color = if (isSelected) highlightColor else barColor,
                    topLeft = Offset(xOffset, yOffset),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(12f, 12f)
                )

                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawText(
                        dailyTotal.dayLabel,
                        xOffset + (barWidth / 2f),
                        canvasHeight - 4f,
                        labelPaint
                    )

                    if (dailyTotal.totalAmount > 0L) {
                        canvas.nativeCanvas.drawText(
                            formatCompactRupiah(dailyTotal.totalAmount),
                            xOffset + (barWidth / 2f),
                            (yOffset - 10f).coerceAtLeast(18f),
                            if (isSelected) valuePaint else labelPaint
                        )
                    }
                }
            }
        }
    }
}

private fun formatCompactRupiah(amount: Long): String {
    return when {
        amount >= 1_000_000L -> {
            val jt = amount / 1_000_000.0
            if (jt % 1.0 == 0.0) "${jt.toInt()}jt" else String.format(Locale("id", "ID"), "%.1fjt", jt)
        }
        amount >= 1_000L -> "${amount / 1_000L}rb"
        else -> amount.toString()
    }
}
