package com.example.smartdisplay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private fun String?.parseBool(): Boolean? {
    if (this == null) return null
    return when (lowercase()) {
        "true" -> true
        "false" -> false
        else -> null
    }
}

fun resolveStyleDef(config: ClockConfig): ClockStyleDef {
    val base = ClockStyles.all.getOrElse(config.clockStyleIndex) { ClockStyles.all[0] }
    val overrides = config.styleOverrides[config.clockStyleIndex] ?: emptyMap()
    return base.copy(
        fontFamily = overrides["fontFamily"] ?: base.fontFamily,
        textColorHex = overrides["textColorHex"] ?: base.textColorHex,
        accentColorHex = overrides["accentColorHex"] ?: base.accentColorHex,
        secondsColorHex = overrides["secondsColorHex"] ?: base.secondsColorHex,
        showSeconds = overrides["showSeconds"]?.parseBool() ?: base.showSeconds,
        showDate = overrides["showDate"]?.parseBool() ?: base.showDate,
        dateFormat = overrides["dateFormat"] ?: base.dateFormat,
        timeFontMultiplier = overrides["timeFontMultiplier"]?.toFloatOrNull() ?: base.timeFontMultiplier,
        fontWeight = overrides["fontWeight"]?.toIntOrNull() ?: base.fontWeight,
        letterSpacing = overrides["letterSpacing"]?.toFloatOrNull() ?: base.letterSpacing,
        showAmPm = overrides["showAmPm"]?.parseBool() ?: base.showAmPm,
        analogTickColorHex = overrides["analogTickColorHex"] ?: base.analogTickColorHex,
        analogHandColorHex = overrides["analogHandColorHex"] ?: base.analogHandColorHex,
        analogShowNumbers = overrides["analogShowNumbers"]?.parseBool() ?: base.analogShowNumbers,
        analogHourHandWidth = overrides["analogHourHandWidth"]?.toFloatOrNull() ?: base.analogHourHandWidth,
        analogMinuteHandWidth = overrides["analogMinuteHandWidth"]?.toFloatOrNull() ?: base.analogMinuteHandWidth,
        analogSecondHandWidth = overrides["analogSecondHandWidth"]?.toFloatOrNull() ?: base.analogSecondHandWidth,
        showBorderGlow = overrides["showBorderGlow"]?.parseBool() ?: base.showBorderGlow,
        showGradientText = overrides["showGradientText"]?.parseBool() ?: base.showGradientText,
        useBoldTime = overrides["useBoldTime"]?.parseBool() ?: base.useBoldTime,
        useItalicTime = overrides["useItalicTime"]?.parseBool() ?: base.useItalicTime,
        dateAboveTime = overrides["dateAboveTime"]?.parseBool() ?: base.dateAboveTime
    )
}

@Composable
fun ClockWidget(
    config: ClockConfig,
    modifier: Modifier = Modifier
) {
    var currentTime by remember { mutableStateOf(Calendar.getInstance()) }
    val style = remember(config.clockStyleIndex, config.styleOverrides) { resolveStyleDef(config) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = Calendar.getInstance()
            delay(if (style.showSeconds) 200 else 5000)
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (style.isAnalog) {
            AnalogClockFace(
                calendar = currentTime,
                style = style,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            DigitalClockFace(
                calendar = currentTime,
                style = style,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun DigitalClockFace(
    calendar: Calendar,
    style: ClockStyleDef,
    modifier: Modifier = Modifier
) {
    val is24h = !style.showAmPm
    val hours = if (is24h) {
        String.format("%02d", calendar.get(Calendar.HOUR_OF_DAY))
    } else {
        val h = calendar.get(Calendar.HOUR)
        String.format("%02d", if (h == 0) 12 else h)
    }
    val minutes = String.format("%02d", calendar.get(Calendar.MINUTE))
    val seconds = String.format("%02d", calendar.get(Calendar.SECOND))
    val amPm = if (style.showAmPm) {
        if (calendar.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"
    } else ""

    val textColor = Color(android.graphics.Color.parseColor(style.textColorHex))
    val accentColor = Color(android.graphics.Color.parseColor(style.accentColorHex))
    val secondsColor = Color(android.graphics.Color.parseColor(style.secondsColorHex))

    val fontFamily = when (style.fontFamily) {
        "serif" -> FontFamily.Serif
        "monospace" -> FontFamily.Monospace
        else -> FontFamily.Default
    }

    val fontWeight = when {
        style.useBoldTime -> FontWeight.Bold
        style.fontWeight <= 200 -> FontWeight.Thin
        style.fontWeight <= 300 -> FontWeight.Light
        style.fontWeight <= 400 -> FontWeight.Normal
        style.fontWeight <= 500 -> FontWeight.Medium
        style.fontWeight <= 600 -> FontWeight.SemiBold
        style.fontWeight <= 700 -> FontWeight.Bold
        style.fontWeight <= 800 -> FontWeight.ExtraBold
        else -> FontWeight.Black
    }

    val fontStyle = if (style.useItalicTime) FontStyle.Italic else FontStyle.Normal
    val isLandscape = LocalConfiguration.current.screenWidthDp > LocalConfiguration.current.screenHeightDp
    val baseFontSize = if (isLandscape) 56.sp else 72.sp
    val timeFontSize = baseFontSize * style.timeFontMultiplier
    val secFontSize = (if (isLandscape) 22.sp else 28.sp) * style.timeFontMultiplier

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val sdf = SimpleDateFormat(style.dateFormat, Locale.getDefault())
        val dateText = sdf.format(Date(calendar.timeInMillis))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp)
        ) {
            if (style.dateAboveTime && style.showDate) {
                Text(
                    text = dateText,
                    color = textColor.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    fontFamily = fontFamily,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                )
            }

            val timeText = "$hours:$minutes"
            Text(
                text = timeText,
                color = if (style.showGradientText) accentColor else textColor,
                fontSize = timeFontSize,
                fontWeight = fontWeight,
                fontFamily = fontFamily,
                fontStyle = fontStyle,
                textAlign = TextAlign.Center,
                letterSpacing = style.letterSpacing.sp,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (style.showSeconds) {
                    Text(
                        text = seconds,
                        color = secondsColor,
                        fontSize = secFontSize,
                        fontWeight = fontWeight,
                        fontFamily = fontFamily,
                        letterSpacing = 2.sp
                    )
                }
                if (amPm.isNotEmpty()) {
                    Text(
                        text = " $amPm",
                        color = accentColor,
                        fontSize = (if (isLandscape) 18.sp else 22.sp) * style.timeFontMultiplier,
                        fontWeight = FontWeight.Medium,
                        fontFamily = fontFamily
                    )
                }
            }

            if (!style.dateAboveTime && style.showDate) {
                Text(
                    text = dateText,
                    color = textColor.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    fontFamily = fontFamily,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun AnalogClockFace(
    calendar: Calendar,
    style: ClockStyleDef,
    modifier: Modifier = Modifier
) {
    val tickColor = Color(android.graphics.Color.parseColor(style.analogTickColorHex))
    val handColor = Color(android.graphics.Color.parseColor(style.analogHandColorHex))
    val accentColor = Color(android.graphics.Color.parseColor(style.accentColorHex))

    val hours = calendar.get(Calendar.HOUR).toFloat() + calendar.get(Calendar.MINUTE) / 60f
    val minutes = calendar.get(Calendar.MINUTE).toFloat() + calendar.get(Calendar.SECOND) / 60f
    val seconds = calendar.get(Calendar.SECOND).toFloat()

    val isLandscape = LocalConfiguration.current.screenWidthDp > LocalConfiguration.current.screenHeightDp
    val canvasPadding = if (isLandscape) 8.dp else 24.dp

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize().padding(canvasPadding)) {
            val cx = size.width / 2
            val cy = size.height / 2
            val radius = min(cx, cy) * 0.85f

            if (style.showBorderGlow) {
                drawCircle(color = tickColor.copy(alpha = 0.15f), radius = radius + 8f)
                drawCircle(color = tickColor.copy(alpha = 0.08f), radius = radius + 4f)
            }

            drawCircle(color = tickColor.copy(alpha = 0.1f), radius = radius + 4f)
            drawCircle(color = tickColor.copy(alpha = 0.05f), radius = radius - 4f)

            for (i in 0 until 60) {
                val angle = Math.toRadians((i * 6).toDouble())
                val isHour = i % 5 == 0
                val innerR = if (isHour) radius * 0.85f else radius * 0.92f
                val outerR = radius * 0.96f
                val width = if (isHour) 4f else 1.5f
                val alpha = if (isHour) 0.8f else 0.35f
                drawLine(
                    color = tickColor.copy(alpha = alpha),
                    start = Offset(
                        cx + (innerR * cos(angle)).toFloat(),
                        cy + (innerR * sin(angle)).toFloat()
                    ),
                    end = Offset(
                        cx + (outerR * cos(angle)).toFloat(),
                        cy + (outerR * sin(angle)).toFloat()
                    ),
                    strokeWidth = width
                )
            }

            if (style.analogShowNumbers) {
                for (i in 1..12) {
                    val angle = Math.toRadians((i * 30 - 90).toDouble())
                    val dotR = radius * 0.74f
                    drawCircle(
                        color = tickColor.copy(alpha = 0.5f),
                        radius = 4f,
                        center = Offset(
                            cx + (dotR * cos(angle)).toFloat(),
                            cy + (dotR * sin(angle)).toFloat()
                        )
                    )
                }
            }

            val hourAngle = Math.toRadians(((hours * 30) - 180).toDouble())
            val handRadius = radius * 0.5f
            drawLine(
                color = handColor,
                start = Offset(cx, cy),
                end = Offset(
                    cx + (handRadius * cos(hourAngle)).toFloat(),
                    cy + (handRadius * sin(hourAngle)).toFloat()
                ),
                strokeWidth = style.analogHourHandWidth,
                cap = StrokeCap.Round
            )

            val minAngle = Math.toRadians(((minutes * 6) - 180).toDouble())
            val minRadius = radius * 0.7f
            drawLine(
                color = handColor,
                start = Offset(cx, cy),
                end = Offset(
                    cx + (minRadius * cos(minAngle)).toFloat(),
                    cy + (minRadius * sin(minAngle)).toFloat()
                ),
                strokeWidth = style.analogMinuteHandWidth,
                cap = StrokeCap.Round
            )

            if (style.showSeconds) {
                val secAngle = Math.toRadians(((seconds * 6) - 180).toDouble())
                val secRadius = radius * 0.78f
                drawLine(
                    color = accentColor,
                    start = Offset(cx, cy),
                    end = Offset(
                        cx + (secRadius * cos(secAngle)).toFloat(),
                        cy + (secRadius * sin(secAngle)).toFloat()
                    ),
                    strokeWidth = style.analogSecondHandWidth,
                    cap = StrokeCap.Round
                )
            }

            drawCircle(color = handColor, radius = 6f, center = Offset(cx, cy))
        }

        if (style.showDate) {
            val dateStr = SimpleDateFormat(style.dateFormat, Locale.getDefault())
                .format(Date(calendar.timeInMillis))
            Text(
                text = dateStr,
                color = Color(android.graphics.Color.parseColor(style.textColorHex)).copy(alpha = 0.7f),
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 180.dp)
            )
        }
    }
}
