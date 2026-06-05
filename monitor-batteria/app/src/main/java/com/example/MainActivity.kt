package com.example

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.battery.BatteryCheckWorker
import com.example.battery.BatteryMonitor
import com.example.battery.BatteryState
import com.example.battery.LocalLogService
import com.example.battery.LocalBatteryLog
import com.example.ui.theme.Amber500
import com.example.ui.theme.Emerald500
import com.example.ui.theme.Emerald600
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.Rose500
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import kotlinx.coroutines.delay
import android.os.Handler
import android.os.Looper
import com.example.battery.SonoffController
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import org.json.JSONArray

class MainActivity : ComponentActivity() {

    private val batteryState = mutableStateOf(BatteryState())

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val state = BatteryMonitor.parseState(intent)
            batteryState.value = state
            // Salva log storico
            LocalLogService.saveLog(context, state.percentage, state.isCharging, "Tempo Reale")

            // Real-time notification check if enabled
            val sharedPrefs = context.getSharedPreferences(BatteryCheckWorker.PREFS_NAME, Context.MODE_PRIVATE)
            val isEnabled = sharedPrefs.getBoolean(BatteryCheckWorker.KEY_ENABLED, true)
            if (isEnabled) {
                val threshold = sharedPrefs.getInt(BatteryCheckWorker.KEY_THRESHOLD, 20)
                val hasNotified = sharedPrefs.getBoolean(BatteryCheckWorker.KEY_NOTIFIED_LOW, false)

                if (state.percentage <= threshold) {
                    if (!state.isCharging) {
                        if (!hasNotified) {
                            sendBatteryLowNotification(context, state.percentage, threshold)
                            sharedPrefs.edit().putBoolean(BatteryCheckWorker.KEY_NOTIFIED_LOW, true).apply()
                        }
                    } else {
                        // Reset notification state if charging is active to allow future triggers
                        if (hasNotified) {
                            sharedPrefs.edit().putBoolean(BatteryCheckWorker.KEY_NOTIFIED_LOW, false).apply()
                        }
                    }
                } else {
                    // Reset notification state when battery returns above threshold
                    if (hasNotified) {
                        sharedPrefs.edit().putBoolean(BatteryCheckWorker.KEY_NOTIFIED_LOW, false).apply()
                    }
                }
            }

            // Controllo Sonoff in tempo reale
            val sonoffPrefs = context.getSharedPreferences(SonoffController.PREFS_NAME, Context.MODE_PRIVATE)
            if (sonoffPrefs.getBoolean(SonoffController.KEY_ENABLED, false)) {
                val deviceId = sonoffPrefs.getString(SonoffController.KEY_DEVICE_ID, "") ?: ""
                val onThreshold = sonoffPrefs.getInt(SonoffController.KEY_ON_THRESHOLD, 30)
                val offThreshold = sonoffPrefs.getInt(SonoffController.KEY_OFF_THRESHOLD, 80)
                val lastCommand = sonoffPrefs.getString(SonoffController.KEY_LAST_COMMAND, "") ?: ""

                if (deviceId.isNotEmpty()) {
                    Thread {
                        try {
                            val controller = SonoffController(context)
                            when {
                                state.percentage >= offThreshold && lastCommand != "off" -> controller.turnOff(deviceId)
                                state.percentage <= onThreshold && lastCommand != "on" -> controller.turnOn(deviceId)
                            }
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Errore Sonoff real-time", e)
                        }
                    }.start()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.enableEdgeToEdge()

        // Schedula il monitoraggio periodico in background all'avvio dell'app
        scheduleBackgroundBatteryCheck(applicationContext)

        setContent {
            MyApplicationTheme {
                val context = LocalContext.current
                
                // Gestione SharedPreferences
                val sharedPrefs = remember {
                    context.getSharedPreferences(BatteryCheckWorker.PREFS_NAME, Context.MODE_PRIVATE)
                }

                var threshold by remember {
                    mutableStateOf(sharedPrefs.getInt(BatteryCheckWorker.KEY_THRESHOLD, 20))
                }
                var notificationsEnabled by remember {
                    mutableStateOf(sharedPrefs.getBoolean(BatteryCheckWorker.KEY_ENABLED, true))
                }

                // Monitoriamo le modifiche ai parametri per salvarli real-time
                LaunchedEffect(threshold) {
                    sharedPrefs.edit().putInt(BatteryCheckWorker.KEY_THRESHOLD, threshold).apply()
                }

                LaunchedEffect(notificationsEnabled) {
                    sharedPrefs.edit().putBoolean(BatteryCheckWorker.KEY_ENABLED, notificationsEnabled).apply()
                }

                // Stato dei permessi di notifica
                var hasNotificationPermission by remember {
                    mutableStateOf(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) == PackageManager.PERMISSION_GRANTED
                        } else {
                            true
                        }
                    )
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    hasNotificationPermission = isGranted
                    if (isGranted) {
                        Toast.makeText(context, "Permesso notifiche concesso!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Il permesso è necessario per ricevere allerte in background.", Toast.LENGTH_LONG).show()
                    }
                }

                // Richiesta automatica all'avvio per Android 13+
                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background
                ) { innerPadding ->
                    BatteryMonitorContent(
                        batteryState = batteryState.value,
                        threshold = threshold,
                        notificationsEnabled = notificationsEnabled,
                        hasPermission = hasNotificationPermission,
                        onThresholdChange = { threshold = it },
                        onNotificationsEnabledChange = { notificationsEnabled = it },
                        onRequestPermission = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                Toast.makeText(context, "Permesso già autorizzato sul tuo sistema.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onSendTestNotification = {
                            sendInstantNotification(context, batteryState.value.percentage, threshold)
                        },
                        innerPadding = innerPadding
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(batteryReceiver)
    }

    private fun scheduleBackgroundBatteryCheck(context: Context) {
        val workRequest = PeriodicWorkRequestBuilder<BatteryCheckWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "BatteryMonitorWork",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    // Invia direttamente una notifica immediata per scopo di test (User Experience ottimale)
    private fun sendInstantNotification(context: Context, percentage: Int, threshold: Int) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                BatteryCheckWorker.CHANNEL_ID,
                "Allerte Batteria",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifiche inviate quando la batteria scende sotto la soglia impostata"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Se nel simulatore la batteria è visualizzata a 100%, simuliamo un valore sotto soglia per dimostrazione
        val simulatedPercentage = if (percentage > threshold) threshold - 3 else percentage
        
        val title = "Allerta Batteria di Prova!"
        val message = "Test OK! La batteria rilevata è al $simulatedPercentage% (soglia d'allerta impostata al $threshold%)."

        val builder = NotificationCompat.Builder(context, BatteryCheckWorker.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            notificationManager.notify(9999, builder.build())
            Toast.makeText(context, "Notifica di prova inviata!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Errore durante l'invio della notifica. Verifica i permessi.", Toast.LENGTH_LONG).show()
        }
    }

    private fun sendBatteryLowNotification(context: Context, percentage: Int, threshold: Int) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                BatteryCheckWorker.CHANNEL_ID,
                "Allerte Batteria",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifiche inviate quando la batteria scende sotto la soglia impostata"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "Batteria Quasi Scarica!"
        val message = "La tua batteria è scesa al $percentage%, al di sotto della soglia impostata di $threshold%."

        val builder = NotificationCompat.Builder(context, BatteryCheckWorker.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            notificationManager.notify(1001, builder.build())
        } catch (e: Exception) {
            Log.e("MainActivity", "Impossibile mostrare la notifica in tempo reale", e)
        }
    }
}

@Composable
fun BatteryMonitorContent(
    batteryState: BatteryState,
    threshold: Int,
    notificationsEnabled: Boolean,
    hasPermission: Boolean,
    onThresholdChange: (Int) -> Unit,
    onNotificationsEnabledChange: (Boolean) -> Unit,
    onRequestPermission: () -> Unit,
    onSendTestNotification: () -> Unit,
    innerPadding: PaddingValues
) {
    val context = LocalContext.current
    var activeTab by remember { mutableStateOf("stato") }

    // Ricarica la cronologia quando scambiamo tab
    val localLogs = remember { mutableStateOf<List<LocalBatteryLog>>(emptyList()) }
    LaunchedEffect(activeTab) {
        if (activeTab == "grafici") {
            localLogs.value = LocalLogService.getLogs(context)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .background(com.example.ui.theme.BackgroundDark)
    ) {
        // Area scrollabile per il contenuto del tab
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 88.dp) // spazio per la bottom navigation
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Unificato elegante "VoltGuard Pro" Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "BATTERY MONITOR",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        ),
                        color = com.example.ui.theme.ElegantPurple
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "VoltGuard Pro",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 24.sp,
                            letterSpacing = (-0.5).sp
                        ),
                        color = com.example.ui.theme.TextPrimary
                    )
                }

                // Indicatore di stato notifiche
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(com.example.ui.theme.CardDark),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (notificationsEnabled && hasPermission) Icons.Default.Notifications else Icons.Default.Warning,
                        contentDescription = "Status",
                        tint = if (notificationsEnabled && hasPermission) com.example.ui.theme.ElegantPurple else com.example.ui.theme.RedAlert,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Mostra avviso se non ci sono permessi per notifiche
            if (!hasPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.RedAlert.copy(alpha = 0.1f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, com.example.ui.theme.RedAlert.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Attenzione",
                            tint = com.example.ui.theme.RedAlert,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Permesso notifiche mancante",
                                fontWeight = FontWeight.Bold,
                                color = com.example.ui.theme.RedAlert,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Funzionalità in background disattivata dal sistema operativo.",
                                color = com.example.ui.theme.TextSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Button(
                            onClick = onRequestPermission,
                            colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.RedAlert),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("Abilita", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = com.example.ui.theme.BackgroundDark)
                        }
                    }
                }
            }

            // CONTENUTO MULTI-TAB DI VOLTGUARD PRO
            when (activeTab) {
                "stato" -> {
                    // TAB STATO: BATTERY CIRCULAR GAUGE
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(220.dp)
                            .padding(vertical = 12.dp)
                    ) {
                        val animatedPercent = animateFloatAsState(
                            targetValue = batteryState.percentage / 100f,
                            animationSpec = tween(durationMillis = 1000),
                            label = "battery_percentage"
                        )

                        Canvas(modifier = Modifier.size(190.dp)) {
                            // Sfondo vuoto elegante
                            drawCircle(
                                color = com.example.ui.theme.OutlineDark,
                                style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                            )
                            
                            // Tracciato colorato elegante
                            drawArc(
                                color = if (batteryState.percentage <= threshold && !batteryState.isCharging) com.example.ui.theme.RedAlert else com.example.ui.theme.ElegantPurple,
                                startAngle = -90f,
                                sweepAngle = animatedPercent.value * 360f,
                                useCenter = false,
                                style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }

                        // Testi d'informazione centrali
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "${batteryState.percentage}",
                                    style = MaterialTheme.typography.headlineLarge.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 72.sp,
                                        letterSpacing = (-2).sp
                                    ),
                                    color = com.example.ui.theme.TextPrimary
                                )
                                Text(
                                    text = "%",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 28.sp
                                    ),
                                    color = com.example.ui.theme.TextSecondary,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )
                            }
                            Text(
                                text = if (batteryState.isCharging) "In Carica" else "Carica Attuale",
                                style = MaterialTheme.typography.bodySmall,
                                color = com.example.ui.theme.TextTertiary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // PULSING BACKGROUND STATUS CHIP
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(com.example.ui.theme.ChipBg)
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Pulsante
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (batteryState.isCharging) com.example.ui.theme.GreenHealthy else com.example.ui.theme.ElegantPurple)
                            )
                            Text(
                                text = if (batteryState.isCharging) "ALIMENTAZIONE CONNESSA" else "MONITORAGGIO ATTIVO (BACKGROUND)",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                                color = com.example.ui.theme.ChipText
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // CARD SOGLIA NOTIFICA
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.CardDark),
                        border = androidx.compose.foundation.BorderStroke(1.dp, com.example.ui.theme.OutlineDark.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Soglia Notifica",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = com.example.ui.theme.TextPrimary
                                    )
                                    Text(
                                        text = "Avvisami quando scende al...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = com.example.ui.theme.TextTertiary
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(com.example.ui.theme.TranslucentElegantPurple)
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "$threshold%",
                                        fontWeight = FontWeight.ExtraBold,
                                        color = com.example.ui.theme.ElegantPurple,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }

                            Slider(
                                value = threshold.toFloat(),
                                onValueChange = { onThresholdChange(it.toInt()) },
                                valueRange = 5f..50f,
                                steps = 45,
                                colors = SliderDefaults.colors(
                                    thumbColor = com.example.ui.theme.ElegantPurple,
                                    activeTrackColor = com.example.ui.theme.ElegantPurple,
                                    inactiveTrackColor = com.example.ui.theme.OutlineDark
                                )
                            )
                        }
                    }

                    // QUICK STATS TWO-COLUMN ROW
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Salute Card
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.CardDark),
                            border = androidx.compose.foundation.BorderStroke(1.dp, com.example.ui.theme.OutlineDark.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "SALUTE",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                                    color = com.example.ui.theme.TextTertiary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = batteryState.health,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.ExtraBold),
                                    color = if (batteryState.health.lowercase() == "ottima" || batteryState.health.lowercase() == "good") com.example.ui.theme.GreenHealthy else com.example.ui.theme.ElegantPurple
                                )
                            }
                        }

                        // Tensione Card
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.CardDark),
                            border = androidx.compose.foundation.BorderStroke(1.dp, com.example.ui.theme.OutlineDark.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "TENSIONE",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                                    color = com.example.ui.theme.TextTertiary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${"%.2f".format(batteryState.voltage / 1000f)} V",
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.ExtraBold),
                                    color = com.example.ui.theme.TextPrimary
                                )
                            }
                        }
                    }

                    // Card dettagli aggiuntivi
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.CardDark),
                        border = androidx.compose.foundation.BorderStroke(1.dp, com.example.ui.theme.OutlineDark.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Dettagli Fisici Batteria",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyLarge,
                                color = com.example.ui.theme.TextPrimary
                            )
                            HorizontalDivider(color = com.example.ui.theme.OutlineDark.copy(alpha = 0.4f))

                            BatteryDetailRow(
                                label = "Temperatura",
                                value = "${batteryState.temperature} °C",
                                icon = Icons.Default.Check,
                                iconColor = com.example.ui.theme.GreenHealthy
                            )
                            BatteryDetailRow(
                                label = "Sorgente Alimentazione",
                                value = batteryState.plugType,
                                icon = Icons.Default.Refresh,
                                iconColor = com.example.ui.theme.ElegantPurple
                            )
                            BatteryDetailRow(
                                label = "Identificativo Stato",
                                value = batteryState.status,
                                icon = Icons.Default.Info,
                                iconColor = com.example.ui.theme.TextSecondary
                            )
                        }
                    }
                }

                "grafici" -> {
                    // TAB GRAFICI: LOCAL HISTORICAL LOGS & TIMELINE
                    Text(
                        text = "Analisi Carica e Scarica",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = com.example.ui.theme.TextPrimary,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (localLogs.value.isEmpty()) {
                        // Empty state tip
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Empty",
                                    tint = com.example.ui.theme.TextTertiary,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Nessun dato registrato",
                                    fontWeight = FontWeight.Bold,
                                    color = com.example.ui.theme.TextSecondary
                                )
                                Text(
                                    text = "Invia una notifica di prova o attendi i log automatici.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = com.example.ui.theme.TextTertiary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 24.dp)
                                )
                            }
                        }
                    } else {
                        // DETAILED ACCURATE INTERACTIVE CHART
                        BatteryTrendChart(localLogs.value)

                        Spacer(modifier = Modifier.height(10.dp))

                        // TELEMETRY LOG FILES LIST
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.CardDark),
                            border = androidx.compose.foundation.BorderStroke(1.dp, com.example.ui.theme.OutlineDark.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Cronologia Letture",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = com.example.ui.theme.TextPrimary
                                    )
                                    Text(
                                        text = "${localLogs.value.size} record",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = com.example.ui.theme.TextTertiary
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                HorizontalDivider(color = com.example.ui.theme.OutlineDark.copy(alpha = 0.4f))

                                localLogs.value.forEachIndexed { index, log ->
                                    val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(log.timestamp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .background(if (log.isCharging) com.example.ui.theme.GreenHealthy.copy(alpha = 0.12f) else com.example.ui.theme.ElegantPurple.copy(alpha = 0.12f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (log.isCharging) Icons.Default.Refresh else Icons.Default.Warning,
                                                contentDescription = null,
                                                tint = if (log.isCharging) com.example.ui.theme.GreenHealthy else com.example.ui.theme.ElegantPurple,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = "${log.percentage}% Carica",
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = com.example.ui.theme.TextPrimary
                                                )
                                                Text(
                                                    text = timeStr,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = com.example.ui.theme.TextTertiary
                                                )
                                            }
                                            Text(
                                                text = "Sorgente: ${log.source} • Status: ${if (log.isCharging) "In carica" else "Scaricamento"}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = com.example.ui.theme.TextSecondary
                                            )
                                        }
                                    }

                                    if (index < localLogs.value.size - 1) {
                                        HorizontalDivider(color = com.example.ui.theme.OutlineDark.copy(alpha = 0.2f))
                                    }
                                }
                            }
                        }
                    }
                }

                "opzioni" -> {
                    // TAB OPZIONI: GENERAL ALERTS & CONVEX BACKEND SYNCHRONIZER
                    Text(
                        text = "Configurazioni Allerte",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = com.example.ui.theme.TextPrimary,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.CardDark),
                        border = androidx.compose.foundation.BorderStroke(1.dp, com.example.ui.theme.OutlineDark.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "Invia Notifiche Allerta",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = com.example.ui.theme.TextPrimary
                                    )
                                    Text(
                                        text = "Ricevi avvisi se sotto le soglie stabilite.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = com.example.ui.theme.TextTertiary
                                    )
                                }
                                Switch(
                                    checked = notificationsEnabled,
                                    onCheckedChange = onNotificationsEnabledChange,
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = com.example.ui.theme.ElegantPurple,
                                        checkedTrackColor = com.example.ui.theme.ElegantPurple.copy(alpha = 0.3f)
                                    )
                                )
                            }

                            HorizontalDivider(color = com.example.ui.theme.OutlineDark.copy(alpha = 0.4f))

                            Button(
                                onClick = onSendTestNotification,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.ElegantPurple),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Notifications,
                                    contentDescription = "Test Notification",
                                    tint = com.example.ui.theme.BackgroundDark,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Invia Notifica Prova",
                                    fontWeight = FontWeight.Bold,
                                    color = com.example.ui.theme.BackgroundDark
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    SonoffSettingsSection()

                }
            }
        }

        // BOTTOM NAVIGATION DECORATION ELEGANT DARK (Material 3 compliant with larger icons and dynamic indicator)
        NavigationBar(
            containerColor = com.example.ui.theme.CardDark,
            tonalElevation = 8.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            NavigationBarItem(
                selected = activeTab == "stato",
                onClick = { activeTab = "stato" },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = "Stato",
                        modifier = Modifier.size(32.dp)
                    )
                },
                label = {
                    Text(
                        text = "Stato",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (activeTab == "stato") FontWeight.ExtraBold else FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = com.example.ui.theme.BackgroundDark,
                    selectedTextColor = com.example.ui.theme.ElegantPurple,
                    indicatorColor = com.example.ui.theme.ElegantPurple,
                    unselectedIconColor = com.example.ui.theme.TextTertiary,
                    unselectedTextColor = com.example.ui.theme.TextTertiary
                )
            )

            NavigationBarItem(
                selected = activeTab == "grafici",
                onClick = { activeTab = "grafici" },
                icon = {
                    Icon(
                        imageVector = Icons.Default.List,
                        contentDescription = "Grafici",
                        modifier = Modifier.size(32.dp)
                    )
                },
                label = {
                    Text(
                        text = "Grafici",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (activeTab == "grafici") FontWeight.ExtraBold else FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = com.example.ui.theme.BackgroundDark,
                    selectedTextColor = com.example.ui.theme.ElegantPurple,
                    indicatorColor = com.example.ui.theme.ElegantPurple,
                    unselectedIconColor = com.example.ui.theme.TextTertiary,
                    unselectedTextColor = com.example.ui.theme.TextTertiary
                )
            )

            NavigationBarItem(
                selected = activeTab == "opzioni",
                onClick = { activeTab = "opzioni" },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Opzioni",
                        modifier = Modifier.size(32.dp)
                    )
                },
                label = {
                    Text(
                        text = "Opzioni",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (activeTab == "opzioni") FontWeight.ExtraBold else FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = com.example.ui.theme.BackgroundDark,
                    selectedTextColor = com.example.ui.theme.ElegantPurple,
                    indicatorColor = com.example.ui.theme.ElegantPurple,
                    unselectedIconColor = com.example.ui.theme.TextTertiary,
                    unselectedTextColor = com.example.ui.theme.TextTertiary
                )
            )
        }
    }
}

@Composable
fun BatteryDetailRow(
    label: String,
    value: String,
    subValue: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(iconColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(16.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = com.example.ui.theme.TextTertiary
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = com.example.ui.theme.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subValue != null) {
                    Text(
                        text = "($subValue)",
                        style = MaterialTheme.typography.bodySmall,
                        color = com.example.ui.theme.TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
fun BatteryTrendChart(logs: List<LocalBatteryLog>) {
    var chartMode by remember { mutableStateOf("linee") } // "linee" o "barre"
    var selectedIndex by remember { mutableStateOf(-1) }
    
    // Mostriamo fino ad un massimo di 25 record storici per rendere il grafico accurato ma leggibile
    val maxPoints = 25
    val displayLogs = remember(logs) {
        if (logs.size > maxPoints) logs.take(maxPoints).reversed() else logs.reversed()
    }

    if (displayLogs.isEmpty()) return

    // Calcolo delle Statistiche Realistiche dell'Applet
    val stats = remember(displayLogs) {
        if (displayLogs.size < 2) null
        else {
            val minLevel = displayLogs.minOf { it.percentage }
            val maxLevel = displayLogs.maxOf { it.percentage }
            
            // Calcolo velocità di scarica/carica media
            val oldest = displayLogs.first()
            val newest = displayLogs.last()
            val timeDiffHrs = (newest.timestamp - oldest.timestamp).toFloat() / (1000f * 60f * 60f)
            val percentageDiff = newest.percentage - oldest.percentage
            
            val speedLabel = if (timeDiffHrs > 0.05f) {
                val rate = percentageDiff / timeDiffHrs
                if (rate > 0) {
                    "Carica media: +${"%.1f".format(rate)}%/ora"
                } else if (rate < 0) {
                    "Scarica media: ${"%.1f".format(rate)}%/ora"
                } else {
                    "Livello stabile"
                }
            } else {
                "Stima in corso..."
            }

            Triple(minLevel, maxLevel, speedLabel)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Grafico Selector Tab (Linee / Barre)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Grafico d'Andamento",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                ),
                color = com.example.ui.theme.TextSecondary
            )
            
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(30.dp))
                    .background(com.example.ui.theme.CardDark)
                    .padding(2.dp)
            ) {
                listOf("linee" to "Linee", "barre" to "Barre").forEach { (mode, title) ->
                    val isSelected = chartMode == mode
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(30.dp))
                            .background(if (isSelected) com.example.ui.theme.ElegantPurple else Color.Transparent)
                            .clickable {
                                chartMode = mode
                                selectedIndex = -1 // reset selection
                            }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                            color = if (isSelected) com.example.ui.theme.BackgroundDark else com.example.ui.theme.TextSecondary
                        )
                    }
                }
            }
        }

        // Area di visualizzazione interattiva del Grafico
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.CardDark),
            border = androidx.compose.foundation.BorderStroke(1.dp, com.example.ui.theme.OutlineDark.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                ) {
                    val width = constraints.maxWidth.toFloat()
                    val height = constraints.maxHeight.toFloat()
                    
                    val leftPadding = 75f
                    val bottomPadding = 40f
                    val graphWidth = width - leftPadding
                    val graphHeight = height - bottomPadding
                    
                    val purpleColor = com.example.ui.theme.ElegantPurple
                    val greenColor = com.example.ui.theme.GreenHealthy
                    val gridColor = com.example.ui.theme.OutlineDark.copy(alpha = 0.3f)
                    val labelPaintColor = android.graphics.Color.argb(
                        (255 * 0.55f).toInt(),
                        147, 143, 153 // TextTertiary
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
                                        selectedIndex = rawIndex.coerceIn(0, displayLogs.size - 1)
                                    }
                                }
                            }
                    ) {
                        // 1. Linee della Griglia Orizzontale (Intervalli del 25%)
                        val levels = listOf(100f, 75f, 50f, 25f, 0f)
                        levels.forEach { level ->
                            val y = (100f - level) / 100f * graphHeight
                            
                            // Griglia tratteggiata
                            drawLine(
                                color = gridColor,
                                start = Offset(leftPadding, y),
                                end = Offset(width, y),
                                strokeWidth = 1.dp.toPx(),
                                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                                    floatArrayOf(12f, 12f), 0f
                                )
                            )
                            
                            // Etichetta Asse Y
                            drawContext.canvas.nativeCanvas.drawText(
                                "${level.toInt()}%",
                                12f,
                                y + 8f,
                                android.graphics.Paint().apply {
                                    color = labelPaintColor
                                    textSize = 24f
                                    typeface = android.graphics.Typeface.create(
                                        android.graphics.Typeface.DEFAULT,
                                        android.graphics.Typeface.BOLD
                                    )
                                }
                            )
                        }

                        // 2. Disegno dei punti di lettura reali
                        if (displayLogs.isNotEmpty()) {
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
                                // Grafico ad Area sfumata + Linea Trend
                                if (points.size > 1) {
                                    // Costruzione del sentiero lineare morbido
                                    val strokePath = Path().apply {
                                        moveTo(points[0].x, points[0].y)
                                        for (i in 1 until points.size) {
                                            val prev = points[i - 1]
                                            val curr = points[i]
                                            // Curve curve morbide
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

                                    // Riempi area con sfumatura
                                    drawPath(
                                        path = fillPath,
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                purpleColor.copy(alpha = 0.3f),
                                                purpleColor.copy(alpha = 0.0f)
                                            ),
                                            startY = 0f,
                                            endY = graphHeight
                                        )
                                    )

                                    // Disegna linea trend
                                    drawPath(
                                        path = strokePath,
                                        color = purpleColor,
                                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                                    )
                                }

                                // Punti sul grafico
                                points.forEachIndexed { index, point ->
                                    val log = displayLogs[index]
                                    val isSelected = index == selectedIndex
                                    
                                    if (isSelected) {
                                        drawCircle(
                                            color = purpleColor.copy(alpha = 0.35f),
                                            radius = 9.dp.toPx(),
                                            center = point
                                        )
                                    }

                                    drawCircle(
                                        color = if (log.isCharging) greenColor else purpleColor,
                                        radius = if (isSelected) 4.5.dp.toPx() else 3.dp.toPx(),
                                        center = point
                                    )
                                }
                            } else {
                                // Istogramma di precisione a barre
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
                            val xIndices = mutableListOf<Int>()
                            if (displayLogs.isNotEmpty()) {
                                xIndices.add(0)
                                if (displayLogs.size > 2) {
                                    xIndices.add(displayLogs.size / 2)
                                }
                                if (displayLogs.size > 1) {
                                    xIndices.add(displayLogs.size - 1)
                                }
                            }

                            xIndices.distinct().forEach { idx ->
                                if (idx in points.indices) {
                                    val pt = points[idx]
                                    val log = displayLogs[idx]
                                    val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(log.timestamp))

                                    drawContext.canvas.nativeCanvas.drawText(
                                        timeStr,
                                        pt.x - 30f,
                                        graphHeight + 32f,
                                        android.graphics.Paint().apply {
                                            color = labelPaintColor
                                            textSize = 21f
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                
                Text(
                    text = "Tocca un punto del grafico per vederne i dettagli temporali.",
                    style = MaterialTheme.typography.labelSmall,
                    color = com.example.ui.theme.TextTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                )
            }
        }

        // Pannello di dettaglio interattivo del punto selezionato
        AnimatedVisibility(
            visible = selectedIndex != -1 && selectedIndex < displayLogs.size,
            enter = fadeIn(animationSpec = tween(120)),
            exit = fadeOut(animationSpec = tween(120))
        ) {
            val selectedLog = displayLogs.getOrNull(selectedIndex)
            if (selectedLog != null) {
                val fullTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(selectedLog.timestamp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.CardDark),
                    border = androidx.compose.foundation.BorderStroke(1.dp, com.example.ui.theme.ElegantPurple.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "DETTAGLIO RECORD SELEZIONATO",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                                color = com.example.ui.theme.ElegantPurple
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Ora: $fullTime • Origine: ${selectedLog.source}",
                                style = MaterialTheme.typography.bodySmall,
                                color = com.example.ui.theme.TextSecondary
                            )
                        }
                        
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${selectedLog.percentage}%",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                color = if (selectedLog.isCharging) com.example.ui.theme.GreenHealthy else com.example.ui.theme.ElegantPurple
                            )
                            Text(
                                text = if (selectedLog.isCharging) "🔌 In Carica" else "🔋 Scarica",
                                style = MaterialTheme.typography.labelSmall,
                                color = com.example.ui.theme.TextTertiary
                            )
                        }
                    }
                }
            }
        }

        // Card Metriche d'Uso Analitiche
        if (stats != null) {
            val (minL, maxL, speed) = stats
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.CardDark),
                border = androidx.compose.foundation.BorderStroke(1.dp, com.example.ui.theme.OutlineDark.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Statistiche di Utilizzo",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = com.example.ui.theme.TextPrimary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Livello Minimo
                        Column {
                            Text(
                                text = "MINIMO",
                                style = MaterialTheme.typography.labelSmall,
                                color = com.example.ui.theme.TextTertiary
                            )
                            Text(
                                text = "$minL%",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                color = com.example.ui.theme.ElegantPurple
                            )
                        }

                        // Livello Massimo
                        Column {
                            Text(
                                text = "MASSIMO",
                                style = MaterialTheme.typography.labelSmall,
                                color = com.example.ui.theme.TextTertiary
                            )
                            Text(
                                text = "$maxL%",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                color = com.example.ui.theme.GreenHealthy
                            )
                        }

                        // Velocità di scarica/carica stimata
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "RATE MEDIO",
                                style = MaterialTheme.typography.labelSmall,
                                color = com.example.ui.theme.TextTertiary
                            )
                            Text(
                                text = speed,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = com.example.ui.theme.TextPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SonoffSettingsSection() {
    val context = LocalContext.current
    val sonoffPrefs = remember {
        context.getSharedPreferences(SonoffController.PREFS_NAME, Context.MODE_PRIVATE)
    }

    var enabled by remember { mutableStateOf(sonoffPrefs.getBoolean(SonoffController.KEY_ENABLED, false)) }
    var deviceId by remember { mutableStateOf(sonoffPrefs.getString(SonoffController.KEY_DEVICE_ID, "") ?: "") }
    var region by remember { mutableStateOf(sonoffPrefs.getString(SonoffController.KEY_REGION, "eu") ?: "eu") }
    var accessToken by remember { mutableStateOf(sonoffPrefs.getString(SonoffController.KEY_ACCESS_TOKEN, "") ?: "") }
    var refreshToken by remember { mutableStateOf(sonoffPrefs.getString(SonoffController.KEY_REFRESH_TOKEN, "") ?: "") }
    var onThreshold by remember { mutableStateOf(sonoffPrefs.getInt(SonoffController.KEY_ON_THRESHOLD, 30)) }
    var offThreshold by remember { mutableStateOf(sonoffPrefs.getInt(SonoffController.KEY_OFF_THRESHOLD, 80)) }
    var expanded by remember { mutableStateOf(false) }
    var tokensVisible by remember { mutableStateOf(false) }
    var manualTestStatus by remember { mutableStateOf("") }
    var lastStatus by remember {
        mutableStateOf(sonoffPrefs.getString(SonoffController.KEY_LAST_STATUS, "In attesa") ?: "In attesa")
    }
    var serverUrl by remember { mutableStateOf(SonoffController.AUTH_SERVER_URL) }
    var email by remember { mutableStateOf("") }
    var authCode by remember { mutableStateOf("") }
    var emailStatus by remember { mutableStateOf("") }
    var showEmailLogin by remember { mutableStateOf(false) }
    var deviceList by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var deviceDropdownExpanded by remember { mutableStateOf(false) }
    var loadingDevices by remember { mutableStateOf(false) }
    var deviceLoadError by remember { mutableStateOf("") }

    LaunchedEffect(enabled) { sonoffPrefs.edit().putBoolean(SonoffController.KEY_ENABLED, enabled).apply() }
    LaunchedEffect(deviceId) { sonoffPrefs.edit().putString(SonoffController.KEY_DEVICE_ID, deviceId).apply() }
    LaunchedEffect(region) { sonoffPrefs.edit().putString(SonoffController.KEY_REGION, region).apply() }
    LaunchedEffect(accessToken) { sonoffPrefs.edit().putString(SonoffController.KEY_ACCESS_TOKEN, accessToken).apply() }
    LaunchedEffect(refreshToken) { sonoffPrefs.edit().putString(SonoffController.KEY_REFRESH_TOKEN, refreshToken).apply() }
    LaunchedEffect(onThreshold) { sonoffPrefs.edit().putInt(SonoffController.KEY_ON_THRESHOLD, onThreshold).apply() }
    LaunchedEffect(offThreshold) { sonoffPrefs.edit().putInt(SonoffController.KEY_OFF_THRESHOLD, offThreshold).apply() }

    LaunchedEffect(Unit) {
        while (true) {
            lastStatus = sonoffPrefs.getString(SonoffController.KEY_LAST_STATUS, "In attesa") ?: "In attesa"
            delay(2000)
        }
    }

    LaunchedEffect(Unit) {
        val savedList = sonoffPrefs.getString(SonoffController.KEY_DEVICE_LIST, "") ?: ""
        if (savedList.isNotEmpty() && deviceList.isEmpty()) {
            try {
                val arr = org.json.JSONArray(savedList)
                val list = mutableListOf<Pair<String, String>>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val id = obj.optString("deviceid", "")
                    val name = obj.optString("name", "Senza nome")
                    if (id.isNotEmpty()) list.add(name to id)
                }
                deviceList = list
            } catch (_: Exception) {}
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.CardDark),
        border = androidx.compose.foundation.BorderStroke(1.dp, com.example.ui.theme.OutlineDark.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Controllo Sonoff",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = com.example.ui.theme.TextPrimary
                    )
                    Text(
                        text = if (enabled) "Attivo - $lastStatus" else "Disabilitato",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (enabled) com.example.ui.theme.GreenHealthy else com.example.ui.theme.TextTertiary
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { enabled = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = com.example.ui.theme.ElegantPurple,
                        checkedTrackColor = com.example.ui.theme.ElegantPurple.copy(alpha = 0.3f)
                    )
                )
            }

            AnimatedVisibility(visible = enabled) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HorizontalDivider(color = com.example.ui.theme.OutlineDark.copy(alpha = 0.4f))

                    TextButton(
                        onClick = { showEmailLogin = !showEmailLogin },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (showEmailLogin) "Nascondi Configurazione Email" else "Configurazione Automatica via Email",
                            color = com.example.ui.theme.ElegantPurple,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    AnimatedVisibility(visible = showEmailLogin) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.CardDark.copy(alpha = 0.7f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Ricevi i token via email",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = com.example.ui.theme.TextPrimary
                                )

                                OutlinedTextField(
                                    value = serverUrl,
                                    onValueChange = { serverUrl = it },
                                    label = { Text("Server URL") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = com.example.ui.theme.ElegantPurple,
                                        unfocusedBorderColor = com.example.ui.theme.OutlineDark,
                                        focusedLabelColor = com.example.ui.theme.ElegantPurple,
                                        cursorColor = com.example.ui.theme.ElegantPurple
                                    )
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = email,
                                        onValueChange = { email = it },
                                        label = { Text("Email") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = com.example.ui.theme.ElegantPurple,
                                            unfocusedBorderColor = com.example.ui.theme.OutlineDark,
                                            focusedLabelColor = com.example.ui.theme.ElegantPurple,
                                            cursorColor = com.example.ui.theme.ElegantPurple
                                        )
                                    )
                                    Button(
                                        onClick = {
                                            emailStatus = "Invio in corso..."
                                            Thread {
                                                try {
                                                    val client = OkHttpClient()
                                                    val json = JSONObject().apply { put("email", email) }
                                                    val body = json.toString().toRequestBody("application/json".toMediaType())
                                                    val request = Request.Builder()
                                                        .url("$serverUrl/request-login")
                                                        .post(body)
                                                        .build()
                                                    val response = client.newCall(request).execute()
                                                    val responseBody = response.body?.string() ?: "{}"
                                                    val result = JSONObject(responseBody)
                                                    if (result.has("code")) {
                                                        val code = result.getString("code")
                                                        authCode = code
                                                        emailStatus = "Codice ricevuto: $code"
                                                    } else {
                                                        val err = result.optString("error", "Errore sconosciuto")
                                                        emailStatus = "Errore: $err"
                                                    }
                                                } catch (e: Exception) {
                                                    emailStatus = "Errore: ${e.message}"
                                                }
                                            }.start()
                                        },
                                        enabled = email.isNotBlank(),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = com.example.ui.theme.ElegantPurple
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.height(56.dp)
                                    ) {
                                        Text("Invia Link", fontWeight = FontWeight.Bold)
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = authCode,
                                        onValueChange = { authCode = it },
                                        label = { Text("Codice 5 cifre") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = com.example.ui.theme.ElegantPurple,
                                            unfocusedBorderColor = com.example.ui.theme.OutlineDark,
                                            focusedLabelColor = com.example.ui.theme.ElegantPurple,
                                            cursorColor = com.example.ui.theme.ElegantPurple
                                        )
                                    )
                                    Button(
                                        onClick = {
                                            emailStatus = "Verifica in corso..."
                                            Thread {
                                                try {
                                                    val client = OkHttpClient()
                                                    val request = Request.Builder()
                                                        .url("$serverUrl/check-code/$authCode")
                                                        .build()
                                                    val response = client.newCall(request).execute()
                                                    val body = response.body?.string() ?: "{}"
                                                    val result = JSONObject(body)
                                                    val status = result.optString("status", "not_found")
                                                    if (status == "completed") {
                                                        val newAccessToken = result.getString("accessToken")
                                                        val newRefreshToken = result.getString("refreshToken")
                                                        val newRegion = result.optString("region", "eu")
                                                        val atExpiry = result.optLong("atExpiryTime", System.currentTimeMillis() + 2592000000L)
                                                        val rtExpiry = result.optLong("rtExpiryTime", System.currentTimeMillis() + 5184000000L)
                                                        emailStatus = "Token ricevuti! Carico dispositivi..."

                                                        val deviceRequest = Request.Builder()
                                                            .url("$serverUrl/devices?accessToken=$newAccessToken&region=$newRegion")
                                                            .build()
                                                        val deviceResponse = client.newCall(deviceRequest).execute()
                                                        val deviceBody = deviceResponse.body?.string() ?: "[]"
                                                        val devices = JSONArray(deviceBody)

                                                        Handler(Looper.getMainLooper()).post {
                                                            accessToken = newAccessToken
                                                            refreshToken = newRefreshToken
                                                            region = newRegion
                                                            sonoffPrefs.edit()
                                                                .putLong(SonoffController.KEY_AT_EXPIRY, atExpiry)
                                                                .putLong(SonoffController.KEY_RT_EXPIRY, rtExpiry)
                                                                .putString(SonoffController.KEY_DEVICE_LIST, deviceBody)
                                                                .apply()
                                                            if (devices.length() > 0) {
                                                                val firstDevice = devices.getJSONObject(0)
                                                                deviceId = firstDevice.optString("deviceid", "")
                                                                emailStatus = "OK: ${devices.length()} dispositivi trovati!"
                                                            } else {
                                                                emailStatus = "Token OK, nessun dispositivo trovato"
                                                            }
                                                            showEmailLogin = false
                                                        }
                                                    } else if (status == "pending") {
                                                        emailStatus = "In attesa... Fai il login nel browser e reinserisci il codice"
                                                    } else {
                                                        emailStatus = "Codice non valido o scaduto"
                                                    }
                                                } catch (e: Exception) {
                                                    emailStatus = "Errore: ${e.message}"
                                                }
                                            }.start()
                                        },
                                        enabled = authCode.length >= 5,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = com.example.ui.theme.GreenHealthy
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.height(56.dp)
                                    ) {
                                        Text("Verifica", fontWeight = FontWeight.Bold)
                                    }
                                }

                                if (emailStatus.isNotBlank()) {
                                    Text(
                                        text = emailStatus,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (emailStatus.startsWith("OK") || emailStatus.startsWith("Codice"))
                                            com.example.ui.theme.GreenHealthy
                                        else if (emailStatus.startsWith("Errore"))
                                            com.example.ui.theme.RedAlert
                                        else
                                            com.example.ui.theme.TextSecondary
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                loadingDevices = true
                                deviceLoadError = ""
                                Thread {
                                    try {
                                        val client = OkHttpClient()
                                        val request = Request.Builder()
                                            .url("$serverUrl/devices?accessToken=$accessToken&region=$region")
                                            .build()
                                        val response = client.newCall(request).execute()
                                        val body = response.body?.string() ?: "[]"
                                        val arr = JSONArray(body)
                                        val list = mutableListOf<Pair<String, String>>()
                                        for (i in 0 until arr.length()) {
                                            val obj = arr.getJSONObject(i)
                                            val id = obj.optString("deviceid", "")
                                            val name = obj.optString("name", "Senza nome")
                                            if (id.isNotEmpty()) list.add(name to id)
                                        }
                                        sonoffPrefs.edit().putString(SonoffController.KEY_DEVICE_LIST, body).apply()
                                        Handler(Looper.getMainLooper()).post {
                                            deviceList = list
                                            loadingDevices = false
                                            deviceDropdownExpanded = list.isNotEmpty()
                                            if (list.isEmpty()) deviceLoadError = "Nessun dispositivo trovato"
                                        }
                                    } catch (e: Exception) {
                                        Handler(Looper.getMainLooper()).post {
                                            loadingDevices = false
                                            deviceLoadError = "Errore: ${e.message}"
                                        }
                                    }
                                }.start()
                            },
                            enabled = accessToken.isNotEmpty() && !loadingDevices,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = com.example.ui.theme.ElegantPurple
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(56.dp)
                        ) {
                            Text(if (loadingDevices) "..." else "Carica", fontSize = 12.sp)
                        }

                        ExposedDropdownMenuBox(
                            expanded = deviceDropdownExpanded,
                            onExpandedChange = { deviceDropdownExpanded = it },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = deviceList.find { it.second == deviceId }?.first ?: deviceId,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Dispositivo") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = deviceDropdownExpanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = com.example.ui.theme.ElegantPurple,
                                    unfocusedBorderColor = com.example.ui.theme.OutlineDark,
                                    focusedLabelColor = com.example.ui.theme.ElegantPurple,
                                    cursorColor = com.example.ui.theme.ElegantPurple
                                )
                            )
                            ExposedDropdownMenu(
                                expanded = deviceDropdownExpanded,
                                onDismissRequest = { deviceDropdownExpanded = false }
                            ) {
                                if (deviceList.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("Nessun dispositivo. Tocca Carica.") },
                                        onClick = { deviceDropdownExpanded = false },
                                        enabled = false
                                    )
                                }
                                deviceList.forEach { (name, id) ->
                                    DropdownMenuItem(
                                        text = { Text("$name  •  $id", fontSize = 13.sp) },
                                        onClick = {
                                            deviceId = id
                                            deviceDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    if (deviceLoadError.isNotBlank()) {
                        Text(
                            text = deviceLoadError,
                            style = MaterialTheme.typography.bodySmall,
                            color = com.example.ui.theme.RedAlert
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Regione:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = com.example.ui.theme.TextSecondary
                        )
                        listOf("eu" to "EU", "us" to "US", "cn" to "CN").forEach { (value, label) ->
                            FilterChip(
                                selected = region == value,
                                onClick = { region = value },
                                label = { Text(label, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = com.example.ui.theme.ElegantPurple.copy(alpha = 0.2f),
                                    selectedLabelColor = com.example.ui.theme.ElegantPurple
                                )
                            )
                        }
                    }

                    OutlinedTextField(
                        value = accessToken,
                        onValueChange = { accessToken = it },
                        label = { Text("Access Token") },
                        placeholder = { Text("Da token.json → accessToken") },
                        singleLine = true,
                        visualTransformation = if (tokensVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            Row {
                                IconButton(onClick = { tokensVisible = !tokensVisible }) {
                                    Icon(
                                        imageVector = if (tokensVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (tokensVisible) "Nascondi" else "Mostra",
                                        tint = com.example.ui.theme.ElegantPurple
                                    )
                                }
                                IconButton(onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("accessToken", accessToken)
                                    clipboard.setPrimaryClip(clip)
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copia",
                                        tint = com.example.ui.theme.ElegantPurple
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = com.example.ui.theme.ElegantPurple,
                            unfocusedBorderColor = com.example.ui.theme.OutlineDark,
                            focusedLabelColor = com.example.ui.theme.ElegantPurple,
                            cursorColor = com.example.ui.theme.ElegantPurple
                        )
                    )

                    OutlinedTextField(
                        value = refreshToken,
                        onValueChange = { refreshToken = it },
                        label = { Text("Refresh Token") },
                        placeholder = { Text("Da token.json → refreshToken") },
                        singleLine = true,
                        visualTransformation = if (tokensVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            Row {
                                IconButton(onClick = { tokensVisible = !tokensVisible }) {
                                    Icon(
                                        imageVector = if (tokensVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (tokensVisible) "Nascondi" else "Mostra",
                                        tint = com.example.ui.theme.ElegantPurple
                                    )
                                }
                                IconButton(onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("refreshToken", refreshToken)
                                    clipboard.setPrimaryClip(clip)
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copia",
                                        tint = com.example.ui.theme.ElegantPurple
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = com.example.ui.theme.ElegantPurple,
                            unfocusedBorderColor = com.example.ui.theme.OutlineDark,
                            focusedLabelColor = com.example.ui.theme.ElegantPurple,
                            cursorColor = com.example.ui.theme.ElegantPurple
                        )
                    )

                    HorizontalDivider(color = com.example.ui.theme.OutlineDark.copy(alpha = 0.4f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                if (deviceId.isEmpty()) {
                                    manualTestStatus = "Nessun dispositivo selezionato"
                                    return@Button
                                }
                                manualTestStatus = "Accensione..."
                                Thread {
                                    try {
                                        val controller = SonoffController(context)
                                        val ok = controller.turnOn(deviceId)
                                        Handler(Looper.getMainLooper()).post {
                                            manualTestStatus = if (ok) "ON inviato!" else "Errore ON"
                                        }
                                    } catch (e: Exception) {
                                        Handler(Looper.getMainLooper()).post {
                                            manualTestStatus = "Errore: ${e.message}"
                                        }
                                    }
                                }.start()
                            },
                            enabled = accessToken.isNotEmpty() && deviceId.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = com.example.ui.theme.GreenHealthy
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Icon(Icons.Default.Power, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ON", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                if (deviceId.isEmpty()) {
                                    manualTestStatus = "Nessun dispositivo selezionato"
                                    return@Button
                                }
                                manualTestStatus = "Spegnimento..."
                                Thread {
                                    try {
                                        val controller = SonoffController(context)
                                        val ok = controller.turnOff(deviceId)
                                        Handler(Looper.getMainLooper()).post {
                                            manualTestStatus = if (ok) "OFF inviato!" else "Errore OFF"
                                        }
                                    } catch (e: Exception) {
                                        Handler(Looper.getMainLooper()).post {
                                            manualTestStatus = "Errore: ${e.message}"
                                        }
                                    }
                                }.start()
                            },
                            enabled = accessToken.isNotEmpty() && deviceId.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = com.example.ui.theme.RedAlert
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Icon(Icons.Default.Power, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("OFF", fontWeight = FontWeight.Bold)
                        }
                    }

                    if (manualTestStatus.isNotBlank()) {
                        Text(
                            text = manualTestStatus,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (manualTestStatus.contains("invio") || manualTestStatus.contains("!"))
                                com.example.ui.theme.GreenHealthy
                            else if (manualTestStatus.contains("Errore"))
                                com.example.ui.theme.RedAlert
                            else com.example.ui.theme.TextSecondary
                        )
                    }

                    HorizontalDivider(color = com.example.ui.theme.OutlineDark.copy(alpha = 0.4f))

                    Text(
                        text = "Soglie Automatiche",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = com.example.ui.theme.TextPrimary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ACCENDI se batteria ≤",
                            style = MaterialTheme.typography.bodySmall,
                            color = com.example.ui.theme.TextSecondary
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(com.example.ui.theme.GreenHealthy.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "$onThreshold%",
                                fontWeight = FontWeight.ExtraBold,
                                color = com.example.ui.theme.GreenHealthy,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    Slider(
                        value = onThreshold.toFloat(),
                        onValueChange = {
                            onThreshold = it.toInt().coerceAtMost(offThreshold - 5)
                        },
                        valueRange = 5f..95f,
                        steps = 90,
                        colors = SliderDefaults.colors(
                            thumbColor = com.example.ui.theme.GreenHealthy,
                            activeTrackColor = com.example.ui.theme.GreenHealthy,
                            inactiveTrackColor = com.example.ui.theme.OutlineDark
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SPEGNI se batteria ≥",
                            style = MaterialTheme.typography.bodySmall,
                            color = com.example.ui.theme.TextSecondary
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(com.example.ui.theme.RedAlert.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "$offThreshold%",
                                fontWeight = FontWeight.ExtraBold,
                                color = com.example.ui.theme.RedAlert,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    Slider(
                        value = offThreshold.toFloat(),
                        onValueChange = {
                            offThreshold = it.toInt().coerceAtLeast(onThreshold + 5)
                        },
                        valueRange = 10f..100f,
                        steps = 90,
                        colors = SliderDefaults.colors(
                            thumbColor = com.example.ui.theme.RedAlert,
                            activeTrackColor = com.example.ui.theme.RedAlert,
                            inactiveTrackColor = com.example.ui.theme.OutlineDark
                        )
                    )

                    Text(
                        text = "Logica: batteria ≤ $onThreshold% → ACCENSIONE • batteria ≥ $offThreshold% → SPEGNIMENTO",
                        style = MaterialTheme.typography.labelSmall,
                        color = com.example.ui.theme.TextTertiary,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                onThreshold = 20
                                offThreshold = 80
                                sonoffPrefs.edit()
                                    .putInt(SonoffController.KEY_ON_THRESHOLD, 20)
                                    .putInt(SonoffController.KEY_OFF_THRESHOLD, 80)
                                    .apply()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = com.example.ui.theme.TextSecondary
                            )
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reset 20/80", fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                sonoffPrefs.edit()
                                    .putInt(SonoffController.KEY_ON_THRESHOLD, onThreshold)
                                    .putInt(SonoffController.KEY_OFF_THRESHOLD, offThreshold)
                                    .apply()
                                Toast.makeText(context, "Soglie salvate: ${onThreshold}% / ${offThreshold}%", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = com.example.ui.theme.ElegantPurple
                            )
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Salva", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}


