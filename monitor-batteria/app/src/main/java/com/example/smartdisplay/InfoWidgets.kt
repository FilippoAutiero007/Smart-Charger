package com.example.smartdisplay

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.example.battery.SonoffController
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
private fun WidgetCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
fun DateWidget(
    textColor: Color = Color.White,
    dateFormat: String = "EEEE, d MMMM",
    modifier: Modifier = Modifier
) {
    var dateStr by remember { mutableStateOf("") }
    val sdf = remember { SimpleDateFormat(dateFormat, Locale.getDefault()) }

    LaunchedEffect(Unit) {
        while (true) {
            dateStr = sdf.format(Date())
            delay(30000)
        }
    }

    WidgetCard(modifier = modifier) {
        Text(
            text = dateStr.uppercase(Locale.getDefault()),
            color = textColor.copy(alpha = 0.85f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 2.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun BatteryWidget(
    textColor: Color = Color.White,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var percentage by remember { mutableStateOf(0) }
    var isCharging by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val battery = context.registerReceiver(null, filter)
            if (battery != null) {
                val level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                percentage = ((level.toFloat() / scale.toFloat()) * 100).toInt()
                val status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            }
            delay(30000)
        }
    }

    val icon = if (isCharging) "\u26A1" else "\uD83D\uDD0B"

    WidgetCard(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = icon, fontSize = 13.sp)
            Text(
                text = " $percentage%",
                color = if (percentage <= 20) Color(0xFFFF6B6B) else textColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            if (isCharging) {
                Text(
                    text = "  IN CARICA",
                    color = Color(0xFF4CAF50),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun WeatherWidgetContent(
    weatherData: WeatherData?,
    textColor: Color = Color.White,
    modifier: Modifier = Modifier
) {
    WidgetCard(modifier = modifier) {
        if (weatherData == null) {
            Text("Caricamento meteo...", color = textColor.copy(alpha = 0.5f), fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            return@WidgetCard
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = WeatherService.weatherEmoji(weatherData.icon),
                    fontSize = 24.sp
                )
                Text(
                    text = " ${weatherData.temperature}\u00B0C",
                    color = textColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = weatherData.condition,
                color = textColor.copy(alpha = 0.7f),
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
            ) {
                Text(
                    text = "\uD83D\uDCA7 ${weatherData.humidity}%",
                    color = textColor.copy(alpha = 0.55f),
                    fontSize = 9.sp
                )
                Text(
                    text = "  \uD83C\uDF21\uFE0F ${weatherData.feelsLike}\u00B0C",
                    color = textColor.copy(alpha = 0.55f),
                    fontSize = 9.sp
                )
                Text(
                    text = "  \uD83D\uDCA8 ${weatherData.windSpeed} km/h",
                    color = textColor.copy(alpha = 0.55f),
                    fontSize = 9.sp
                )
            }

            if (weatherData.forecast.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                ) {
                    weatherData.forecast.take(4).forEach { f ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(f.day, color = textColor.copy(alpha = 0.5f), fontSize = 8.sp)
                            Text(WeatherService.weatherEmoji(f.icon), fontSize = 14.sp)
                            Text(
                                "${f.tempHigh}\u00B0",
                                color = textColor,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MusicWidgetContent(
    track: TrackInfo,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    textColor: Color = Color.White,
    accentColor: Color = Color(0xFFD0BCFF),
    modifier: Modifier = Modifier
) {
    WidgetCard(modifier = modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "\uD83C\uDFB5",
                fontSize = 20.sp
            )
            Text(
                text = track.title.ifBlank { "Nessun brano" },
                color = textColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
            )
            if (track.artist.isNotBlank()) {
                Text(
                    text = track.artist,
                    color = textColor.copy(alpha = 0.55f),
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }

            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            ) {
                TextButton(onClick = onPrev, text = "\u23EE", textColor, 16.sp)
                TextButton(
                    onClick = onPlayPause,
                    text = if (track.isPlaying) "\u23F8" else "\u25B6",
                    accentColor, 24.sp
                )
                TextButton(onClick = onNext, text = "\u23ED", textColor, 16.sp)
            }
        }
    }
}

@Composable
fun NewsWidgetContent(
    newsData: NewsData?,
    textColor: Color = Color.White,
    modifier: Modifier = Modifier
) {
    WidgetCard(modifier = modifier) {
        if (newsData == null || newsData.articles.isEmpty()) {
            Text("Nessuna notizia", color = textColor.copy(alpha = 0.5f), fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            return@WidgetCard
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "\uD83D\uDCF0  Notizie",
                color = textColor.copy(alpha = 0.7f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            newsData.articles.take(3).forEach { article ->
                Text(
                    text = "\u2022 ${article.title}",
                    color = textColor.copy(alpha = 0.8f),
                    fontSize = 10.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 13.sp,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
                if (article.source.isNotBlank()) {
                    Text(
                        text = article.source,
                        color = textColor.copy(alpha = 0.4f),
                        fontSize = 8.sp,
                        modifier = Modifier.padding(start = 8.dp, bottom = 3.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun TimerWidgetContent(
    textColor: Color = Color.White,
    accentColor: Color = Color(0xFFD0BCFF),
    modifier: Modifier = Modifier
) {
    var state by remember { mutableStateOf("idle") }
    var remainingSecs by remember { mutableStateOf(60) }
    var totalSecs by remember { mutableStateOf(60) }
    var selectedPreset by remember { mutableStateOf(60) }

    val presets = listOf(
        60 to "1m", 300 to "5m", 600 to "10m",
        1800 to "30m", 3600 to "1h"
    )

    LaunchedEffect(state, remainingSecs) {
        if (state == "running" && remainingSecs > 0) {
            delay(1000)
            remainingSecs--
            if (remainingSecs == 0) {
                state = "done"
            }
        }
    }

    val progress = if (totalSecs > 0) remainingSecs.toFloat() / totalSecs.toFloat() else 1f

    val hourglassRotation by animateFloatAsState(
        targetValue = if (state == "running") 180f else 0f,
        animationSpec = if (state == "running")
            infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Reverse)
        else tween(500)
    )

    val minutes = remainingSecs / 60
    val seconds = remainingSecs % 60

    WidgetCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.padding(end = 8.dp)) {
                    Canvas(modifier = Modifier.size(32.dp)) {
                        val cx = size.width / 2
                        val cy = size.height / 2
                        val r = minOf(cx, cy) * 0.85f

                        if (state == "running" || state == "done") {
                            val sweep = (1f - progress) * 360f
                            drawArc(
                                color = accentColor.copy(alpha = if (state == "done") 1f else 0.7f),
                                startAngle = -90f,
                                sweepAngle = sweep,
                                useCenter = false,
                                style = Stroke(width = 3f, cap = StrokeCap.Round),
                                topLeft = androidx.compose.ui.geometry.Offset(cx - r, cy - r),
                                size = androidx.compose.ui.geometry.Size(r * 2, r * 2)
                            )
                        }
                        drawArc(
                            color = accentColor.copy(alpha = 0.2f),
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = Stroke(width = 2f, cap = StrokeCap.Round),
                            topLeft = androidx.compose.ui.geometry.Offset(cx - r, cy - r),
                            size = androidx.compose.ui.geometry.Size(r * 2, r * 2)
                        )
                    }
                    Text(
                        text = "\u23F3",
                        fontSize = 20.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                Column {
                    Text(
                        text = if (state == "done") "\u23F0 Tempo scaduto!" else String.format("%02d:%02d", minutes, seconds),
                        color = if (state == "done") Color(0xFFFF6B6B) else textColor,
                        fontSize = if (state == "done") 12.sp else 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = when (state) {
                            "idle" -> "Timer"
                            "running" -> "In corso..."
                            "paused" -> "In pausa"
                            "done" -> "\uD83D\uDD14"
                            else -> ""
                        },
                        color = textColor.copy(alpha = 0.5f),
                        fontSize = 9.sp
                    )
                }
            }

            if (state == "idle" || state == "done") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                ) {
                    presets.forEach { (secs, label) ->
                        androidx.compose.material3.FilterChip(
                            selected = selectedPreset == secs,
                            onClick = {
                                selectedPreset = secs
                                remainingSecs = secs
                                totalSecs = secs
                                state = "idle"
                            },
                            label = { Text(label, fontSize = 8.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            ) {
                if (state == "idle" || state == "done") {
                    Button(
                        onClick = {
                            if (remainingSecs <= 0) {
                                remainingSecs = selectedPreset
                                totalSecs = selectedPreset
                            }
                            state = "running"
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                        modifier = Modifier.weight(1f)
                    ) { Text(if (state == "done") "Ripeti" else "Avvia", fontSize = 9.sp) }
                } else {
                    Button(
                        onClick = { state = if (state == "running") "paused" else "running" },
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                        modifier = Modifier.weight(1f)
                    ) { Text(if (state == "running") "Pausa" else "Riprendi", fontSize = 9.sp) }
                    Button(
                        onClick = {
                            remainingSecs = selectedPreset
                            totalSecs = selectedPreset
                            state = "idle"
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF555555)),
                        modifier = Modifier.weight(1f)
                    ) { Text("Reset", fontSize = 9.sp) }
                }
            }
        }
    }
}

@Composable
fun StopwatchWidgetContent(
    textColor: Color = Color.White,
    accentColor: Color = Color(0xFFD0BCFF),
    modifier: Modifier = Modifier
) {
    var running by remember { mutableStateOf(false) }
    var elapsedMs by remember { mutableStateOf(0L) }
    var startTime by remember { mutableStateOf(0L) }
    var lapTimes by remember { mutableStateOf(listOf<Long>()) }

    LaunchedEffect(running) {
        if (running) {
            startTime = System.currentTimeMillis() - elapsedMs
            while (true) {
                elapsedMs = System.currentTimeMillis() - startTime
                delay(16)
            }
        }
    }

    val hours = elapsedMs / 3600000
    val mins = (elapsedMs % 3600000) / 60000
    val secs = (elapsedMs % 60000) / 1000
    val millis = (elapsedMs % 1000) / 10

    WidgetCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "\u23F1\uFE0F",
                fontSize = 20.sp
            )
            Text(
                text = String.format("%02d:%02d:%02d.%02d", hours, mins, secs, millis),
                color = textColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            ) {
                TextButton(
                    onClick = {
                        if (running) {
                            running = false
                        } else {
                            if (elapsedMs > 0) {
                                elapsedMs = 0
                                lapTimes = emptyList()
                            }
                            running = true
                        }
                    },
                    text = if (running) "\u23F8 Stop" else "\u25B6 Start",
                    accentColor, 11.sp
                )
                if (running) {
                    TextButton(
                        onClick = { lapTimes = lapTimes + elapsedMs },
                        text = "\uD83C\uDFC1 Giro",
                        textColor.copy(alpha = 0.7f), 11.sp
                    )
                } else if (elapsedMs > 0) {
                    TextButton(
                        onClick = { elapsedMs = 0; lapTimes = emptyList() },
                        text = "\uD83D\uDD04 Reset",
                        textColor.copy(alpha = 0.7f), 11.sp
                    )
                }
            }

            if (lapTimes.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    lapTimes.takeLast(5).forEachIndexed { idx, lap ->
                        val lh = lap / 3600000
                        val lm = (lap % 3600000) / 60000
                        val ls = (lap % 60000) / 1000
                        val lms = (lap % 1000) / 10
                        Text(
                            text = "Giro ${idx + 1}:  ${String.format("%02d:%02d:%02d.%02d", lh, lm, ls, lms)}",
                            color = textColor.copy(alpha = 0.6f),
                            fontSize = 8.sp
                        )
                    }
                }
            }
        }
    }
}

data class CountdownEvent(
    val name: String,
    val icon: String,
    val month: Int,
    val day: Int,
    val isVariable: Boolean = false
)

private val specialDates = listOf(
    CountdownEvent("Capodanno", "\uD83C\uDF86", 1, 1),
    CountdownEvent("Epifania", "\u2B50", 1, 6),
    CountdownEvent("Carnevale", "\uD83C\uDFA8", 2, 12),
    CountdownEvent("San Valentino", "\u2764\uFE0F", 2, 14),
    CountdownEvent("Festa della Donna", "\uD83C\uDF3B", 3, 8),
    CountdownEvent("Anniversario Liberazione", "\uD83C\uDFF4", 4, 25),
    CountdownEvent("Primo Maggio", "\uD83C\uDF1F", 5, 1),
    CountdownEvent("Festa della Repubblica", "\uD83C\uDEE6\uD83C\uDDF9", 6, 2),
    CountdownEvent("Inizio Estate", "\u2600\uFE0F", 6, 21),
    CountdownEvent("Ferragosto", "\uD83C\uDFD6\uFE0F", 8, 15),
    CountdownEvent("Ognissanti", "\uD83D\uDD4A\uFE0F", 11, 1),
    CountdownEvent("Immacolata", "\uD83D\uDE4C", 12, 8),
    CountdownEvent("Natale", "\uD83C\uDF85", 12, 25),
    CountdownEvent("Santo Stefano", "\uD83C\uDF81", 12, 26)
)

private fun calcEaster(year: Int): Pair<Int, Int> {
    val a = year % 19
    val b = year / 100
    val c = year % 100
    val d = b / 4
    val e = b % 4
    val f = (b + 8) / 25
    val g = (b - f + 1) / 3
    val h = (19 * a + b - d - g + 15) % 30
    val i = c / 4
    val k = c % 4
    val l = (32 + 2 * e + 2 * i - h - k) % 7
    val m = (a + 11 * h + 22 * l) / 451
    val month = (h + l - 7 * m + 114) / 31
    val day = ((h + l - 7 * m + 114) % 31) + 1
    return month to day
}

private fun findNextEvent(): Pair<CountdownEvent, Long> {
    val now = Calendar.getInstance()
    val today = now.timeInMillis

    val easter = calcEaster(now.get(Calendar.YEAR))
    val easterCal = Calendar.getInstance().apply { set(Calendar.YEAR, now.get(Calendar.YEAR)); set(Calendar.MONTH, easter.first - 1); set(Calendar.DAY_OF_MONTH, easter.second) }

    var best: CountdownEvent? = null
    var bestDiff = Long.MAX_VALUE

    for (e in specialDates) {
        val cal = Calendar.getInstance()
        cal.set(Calendar.MONTH, e.month - 1)
        cal.set(Calendar.DAY_OF_MONTH, e.day)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        var diff = cal.timeInMillis - today
        if (diff < 0) {
            cal.add(Calendar.YEAR, 1)
            diff = cal.timeInMillis - today
        }
        if (diff < bestDiff) {
            bestDiff = diff
            best = e
        }
    }

    val easterDiff = easterCal.timeInMillis - today
    val easterEvent = CountdownEvent("Pasqua", "\uD83D\uDC30", easter.first, easter.second, isVariable = true)
    if (easterDiff > 0 && easterDiff < bestDiff) {
        best = easterEvent
        bestDiff = easterDiff
    }

    return Pair(best ?: specialDates[0], bestDiff)
}

@Composable
fun CountdownWidgetContent(
    customDate: String = "",
    customLabel: String = "",
    textColor: Color = Color.White,
    accentColor: Color = Color(0xFFD0BCFF),
    modifier: Modifier = Modifier
) {
    var currentEvent by remember { mutableStateOf<Pair<CountdownEvent, Long>?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            currentEvent = findNextEvent()
            delay(60000)
        }
    }

    val (event, diffMs) = currentEvent ?: return
    val days = (diffMs / 86400000).toInt()
    val hours = ((diffMs % 86400000) / 3600000).toInt()
    val totalDays = days + (if (hours > 12) 1 else 0)

    WidgetCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = event.icon, fontSize = 24.sp)
            Text(
                text = event.name,
                color = textColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "$totalDays",
                    color = accentColor,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = " giorni",
                    color = textColor.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 4.dp, start = 2.dp)
                )
            }
            Text(
                text = "${event.day}/${event.month}",
                color = textColor.copy(alpha = 0.5f),
                fontSize = 9.sp
            )
        }
    }
}

@Composable
fun EWeLinkWidgetContent(
    textColor: Color = Color.White,
    accentColor: Color = Color(0xFFD0BCFF),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val controller = remember { SonoffController(context) }
    var deviceId by remember { mutableStateOf("") }
    var lastStatus by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var lastCmd by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        deviceId = controller.getDeviceId()
        lastStatus = controller.getLastStatus()
        lastCmd = controller.getLastCommand()
    }

    val isValid = deviceId.isNotEmpty() && controller.hasValidCredentials()

    WidgetCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isValid) "\uD83D\uDD0C" else "\u26A0\uFE0F",
                fontSize = 22.sp
            )
            Text(
                text = if (isValid) "Dispositivo" else "Non configurato",
                color = textColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            if (isValid && deviceId.isNotBlank()) {
                Text(
                    text = deviceId.take(12) + "...",
                    color = textColor.copy(alpha = 0.5f),
                    fontSize = 7.sp,
                    maxLines = 1
                )
                Text(
                    text = lastStatus,
                    color = textColor.copy(alpha = 0.7f),
                    fontSize = 9.sp
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ToggleButton(
                        text = "OFF",
                        isSelected = lastCmd == "off",
                        selectedColor = Color(0xFF555555),
                        onClick = {
                            loading = true
                            controller.turnOff(deviceId)
                            lastCmd = "off"
                            lastStatus = controller.getLastStatus()
                            loading = false
                        },
                        enabled = !loading && lastCmd != "off",
                        modifier = Modifier.weight(1f)
                    )
                    ToggleButton(
                        text = "ON",
                        isSelected = lastCmd == "on",
                        selectedColor = Color(0xFF4CAF50),
                        onClick = {
                            loading = true
                            controller.turnOn(deviceId)
                            lastCmd = "on"
                            lastStatus = controller.getLastStatus()
                            loading = false
                        },
                        enabled = !loading && lastCmd != "on",
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                Text(
                    text = "Vai su Impostazioni >\nControllo Sonoff",
                    color = textColor.copy(alpha = 0.5f),
                    fontSize = 8.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 10.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
fun PhotoWidgetContent(
    photoUris: List<String>,
    effect: TransitionEffect = TransitionEffect.FADE,
    intervalSecs: Int = 10,
    textColor: Color = Color.White,
    modifier: Modifier = Modifier
) {
    WidgetCard(modifier = modifier) {
        if (photoUris.isEmpty()) {
            Text("\uD83D\uDDBC Nessuna foto", color = textColor.copy(alpha = 0.5f), fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            return@WidgetCard
        }
        Box(modifier = Modifier.fillMaxWidth().height(120.dp)) {
            PhotoCarousel(
                photoUris = photoUris,
                effect = effect,
                intervalSecs = intervalSecs
            )
        }
    }
}

@Composable
private fun ToggleButton(
    text: String,
    isSelected: Boolean,
    selectedColor: Color,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) selectedColor else Color(0xFF333333),
            disabledContainerColor = if (isSelected) selectedColor else Color(0xFF333333)
        ),
        modifier = modifier.height(28.dp),
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun TextButton(
    onClick: () -> Unit,
    text: String,
    color: Color,
    fontSize: androidx.compose.ui.unit.TextUnit
) {
    androidx.compose.material3.TextButton(onClick = onClick) {
        Text(text = text, color = color, fontSize = fontSize)
    }
}
