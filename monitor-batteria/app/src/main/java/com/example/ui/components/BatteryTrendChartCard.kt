package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.battery.LocalBatteryLog
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

@Composable
fun BatteryTrendChartCard(
    logs: List<LocalBatteryLog>,
    modifier: Modifier = Modifier,
    title: String = "Grafico d'Andamento Temporale"
) {
    var chartMode by remember { mutableStateOf("linee") } // "linee" o "barre"
    var selectedIndex by remember { mutableStateOf(-1) }
    var isExpandedFullscreen by remember { mutableStateOf(false) }

    val displayLogs = remember(logs) {
        if (logs.size > 30) logs.take(30).reversed() else logs.reversed()
    }

    if (displayLogs.isEmpty()) {
        Card(
            modifier = modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardDark),
            border = BorderStroke(1.dp, OutlineDark.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "In attesa di letture telemetriche per il grafico...",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }
        }
        return
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        border = BorderStroke(1.dp, OutlineDark.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Header del grafico con titolo, modalità e tasto espansione orizzontale
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        ),
                        color = TextPrimary
                    )
                    Text(
                        text = "${displayLogs.size} campioni recenti",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Selector Linee / Barre
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(BackgroundDark)
                            .padding(2.dp)
                    ) {
                        listOf("linee" to "Linee", "barre" to "Barre").forEach { (mode, label) ->
                            val isSelected = chartMode == mode
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (isSelected) ElegantPurple else Color.Transparent)
                                    .clickable {
                                        chartMode = mode
                                        selectedIndex = -1
                                    }
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) BackgroundDark else TextSecondary
                                )
                            }
                        }
                    }

                    // Tasto Espandi a Schermo Intero / Orizzontale
                    Surface(
                        onClick = { isExpandedFullscreen = true },
                        shape = RoundedCornerShape(8.dp),
                        color = TranslucentElegantPurple,
                        modifier = Modifier.height(28.dp)
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Espandi ⤢",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElegantPurple
                            )
                        }
                    }
                }
            }

            // Vista compatta del grafico
            ChartSurface(
                displayLogs = displayLogs,
                chartMode = chartMode,
                selectedIndex = selectedIndex,
                onSelectIndex = { selectedIndex = it },
                heightDp = 160
            )

            // Dettaglio del punto selezionato
            if (selectedIndex in displayLogs.indices) {
                val selectedLog = displayLogs[selectedIndex]
                val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(selectedLog.timestamp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(BackgroundDark.copy(alpha = 0.6f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Rilevazione ore $timeStr",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "${selectedLog.percentage}% • ${if (selectedLog.isCharging) "In Carica" else "Scarica"}",
                        color = if (selectedLog.isCharging) GreenHealthy else ElegantPurple,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    // Modalità Schermo Intero / Vista Orizzontale Panoramica
    if (isExpandedFullscreen) {
        Dialog(
            onDismissRequest = { isExpandedFullscreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BackgroundDark),
                color = BackgroundDark
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Telemetria Batteria ad Alta Risoluzione",
                                color = TextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Visualizzazione estesa orizzontale (${displayLogs.size} campioni)",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }

                        IconButton(onClick = { isExpandedFullscreen = false }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Chiudi",
                                tint = TextPrimary
                            )
                        }
                    }

                    // Grafico espanso a pieno schermo
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        colors = CardDefaults.cardColors(containerColor = CardDark),
                        border = BorderStroke(1.dp, OutlineDark.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ChartSurface(
                                displayLogs = displayLogs,
                                chartMode = chartMode,
                                selectedIndex = selectedIndex,
                                onSelectIndex = { selectedIndex = it },
                                heightDp = 300,
                                isFullscreen = true
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartSurface(
    displayLogs: List<LocalBatteryLog>,
    chartMode: String,
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit,
    heightDp: Int,
    isFullscreen: Boolean = false
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(heightDp.dp)
    ) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()

        val leftPadding = 70f
        val bottomPadding = 40f
        val graphWidth = width - leftPadding
        val graphHeight = height - bottomPadding

        val purpleColor = ElegantPurple
        val greenColor = GreenHealthy
        val gridColor = OutlineDark.copy(alpha = 0.35f)
        val labelPaintColor = android.graphics.Color.argb(
            (255 * 0.65f).toInt(),
            202, 196, 208 // TextSecondary
        )

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(displayLogs) {
                    detectTapGestures { offset ->
                        val x = offset.x
                        if (x >= leftPadding && x <= width && displayLogs.size > 1) {
                            val indexWidth = graphWidth / (displayLogs.size - 1)
                            val relativeX = x - leftPadding
                            val rawIndex = (relativeX / indexWidth).roundToInt()
                            onSelectIndex(rawIndex.coerceIn(0, displayLogs.size - 1))
                        }
                    }
                }
        ) {
            // 1. Griglia Orizzontale (0%, 25%, 50%, 75%, 100%)
            val levels = listOf(100f, 75f, 50f, 25f, 0f)
            levels.forEach { level ->
                val y = (100f - level) / 100f * graphHeight

                drawLine(
                    color = gridColor,
                    start = Offset(leftPadding, y),
                    end = Offset(width, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                        floatArrayOf(12f, 12f), 0f
                    )
                )

                drawContext.canvas.nativeCanvas.drawText(
                    "${level.toInt()}%",
                    12f,
                    y + 8f,
                    android.graphics.Paint().apply {
                        color = labelPaintColor
                        textSize = if (isFullscreen) 28f else 22f
                        typeface = android.graphics.Typeface.create(
                            android.graphics.Typeface.DEFAULT,
                            android.graphics.Typeface.BOLD
                        )
                    }
                )
            }

            // 2. Mappatura punti
            val points = displayLogs.mapIndexed { index, log ->
                val x = leftPadding + if (displayLogs.size > 1) {
                    index.toFloat() / (displayLogs.size - 1) * graphWidth
                } else {
                    graphWidth / 2f
                }
                val y = (100f - log.percentage.toFloat()) / 100f * graphHeight
                Offset(x, y)
            }

            if (chartMode == "linee") {
                if (points.size > 1) {
                    val strokePath = Path().apply {
                        moveTo(points[0].x, points[0].y)
                        for (i in 1 until points.size) {
                            val prev = points[i - 1]
                            val curr = points[i]
                            cubicTo(
                                (prev.x + curr.x) / 2f, prev.y,
                                (prev.x + curr.x) / 2f, curr.y,
                                curr.x, curr.y
                            )
                        }
                    }

                    val fillPath = Path().apply {
                        moveTo(leftPadding, graphHeight)
                        lineTo(points[0].x, points[0].y)
                        for (i in 1 until points.size) {
                            val prev = points[i - 1]
                            val curr = points[i]
                            cubicTo(
                                (prev.x + curr.x) / 2f, prev.y,
                                (prev.x + curr.x) / 2f, curr.y,
                                curr.x, curr.y
                            )
                        }
                        lineTo(points.last().x, graphHeight)
                        close()
                    }

                    // Sfumatura d'area
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                purpleColor.copy(alpha = 0.35f),
                                purpleColor.copy(alpha = 0.0f)
                            ),
                            startY = 0f,
                            endY = graphHeight
                        )
                    )

                    // Linea principale
                    drawPath(
                        path = strokePath,
                        color = purpleColor,
                        style = Stroke(width = if (isFullscreen) 3.5.dp.toPx() else 2.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // Cerchietti dei punti
                points.forEachIndexed { index, point ->
                    val log = displayLogs[index]
                    val isSelected = index == selectedIndex

                    if (isSelected) {
                        drawCircle(
                            color = purpleColor.copy(alpha = 0.35f),
                            radius = if (isFullscreen) 12.dp.toPx() else 9.dp.toPx(),
                            center = point
                        )
                    }

                    drawCircle(
                        color = if (log.isCharging) greenColor else purpleColor,
                        radius = if (isSelected) 5.dp.toPx() else 3.dp.toPx(),
                        center = point
                    )
                }
            } else {
                // Modalità Istogramma a barre
                val barWidth = if (points.size > 1) {
                    (graphWidth / points.size) * 0.65f
                } else {
                    25f
                }

                points.forEachIndexed { index, point ->
                    val log = displayLogs[index]
                    val isSelected = index == selectedIndex
                    val barHeight = graphHeight - point.y
                    val color = if (log.isCharging) greenColor else purpleColor
                    val alpha = if (selectedIndex == -1 || isSelected) 1.0f else 0.4f

                    drawRoundRect(
                        color = color.copy(alpha = alpha),
                        topLeft = Offset(point.x - barWidth / 2f, point.y),
                        size = Size(barWidth, barHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx())
                    )

                    if (isSelected) {
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.8f),
                            topLeft = Offset(point.x - barWidth / 2f - 2f, point.y - 2f),
                            size = Size(barWidth + 4f, barHeight + 4f),
                            style = Stroke(width = 1.2.dp.toPx()),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )
                    }
                }
            }

            // 3. Etichette temporali asse X
            val step = if (displayLogs.size >= 4) displayLogs.size / 3 else 1
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            for (i in 0 until displayLogs.size step step) {
                val point = points[i]
                val timeLabel = timeFormat.format(Date(displayLogs[i].timestamp))
                drawContext.canvas.nativeCanvas.drawText(
                    timeLabel,
                    point.x - 25f,
                    height - 8f,
                    android.graphics.Paint().apply {
                        color = labelPaintColor
                        textSize = if (isFullscreen) 24f else 20f
                    }
                )
            }
        }
    }
}