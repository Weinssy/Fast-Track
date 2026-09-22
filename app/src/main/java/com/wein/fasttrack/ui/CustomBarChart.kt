package com.wein.fasttrack.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.wein.fasttrack.viewmodel.DailyTotal
import java.text.NumberFormat
import java.util.Locale

@Composable
fun CustomBarChart(
    dailyTotals: List<DailyTotal>,
    maxAmount: Long,
    modifier: Modifier = Modifier
) {
    val barColor = Color(0xFF242629)
    val highlightColor = Color(0xFF2CB67D)
    val textColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f).hashCode()
    
    val density = LocalDensity.current
    val textPaint = remember(density) {
        android.graphics.Paint().apply {
            color = textColor
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = with(density) { 12.dp.toPx() }
            isAntiAlias = true
        }
    }

    val currencyFormat = remember {
        NumberFormat.getCurrencyInstance(Locale("id", "ID")).apply {
            maximumFractionDigits = 0
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(top = 16.dp, bottom = 24.dp)
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        
        val barCount = dailyTotals.size
        if (barCount == 0) return@Canvas

        val barWidth = (canvasWidth / (barCount * 2)).coerceAtMost(60f)
        val spacing = (canvasWidth - (barWidth * barCount)) / (barCount + 1)
        val maxBarHeight = canvasHeight - 40f // Leave room for text

        dailyTotals.forEachIndexed { index, dailyTotal ->
            val xOffset = spacing + (index * (barWidth + spacing))
            
            // Calculate height proportional to maxAmount
            val heightRatio = if (maxAmount > 0) dailyTotal.totalAmount.toFloat() / maxAmount.toFloat() else 0f
            val barHeight = (maxBarHeight * heightRatio).coerceAtLeast(10f) // Minimum height 10f for visibility
            
            val yOffset = maxBarHeight - barHeight

            // Determine if it's the last bar (current day)
            val isHighlight = index == barCount - 1
            
            // Draw Bar
            drawRoundRect(
                color = if (isHighlight) highlightColor else barColor,
                topLeft = Offset(xOffset, yOffset),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(12f, 12f)
            )

            drawIntoCanvas { canvas ->
                // Draw Day Label
                canvas.nativeCanvas.drawText(
                    dailyTotal.dayLabel,
                    xOffset + (barWidth / 2),
                    canvasHeight,
                    textPaint
                )
                
                // Draw Value above bar if > 0
                if (dailyTotal.totalAmount > 0) {
                    canvas.nativeCanvas.drawText(
                        currencyFormat.format(dailyTotal.totalAmount),
                        xOffset + (barWidth / 2),
                        yOffset - 10f,
                        textPaint
                    )
                }
            }
        }
    }
}
