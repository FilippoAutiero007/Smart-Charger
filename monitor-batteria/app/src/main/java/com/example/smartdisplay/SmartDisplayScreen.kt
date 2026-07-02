package com.example.smartdisplay

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CardDark
import com.example.ui.theme.ElegantPurple
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import kotlinx.coroutines.delay

private fun getStyleOverride(config: DisplayConfig, key: String): String? {
    return config.clock.styleOverrides[config.clock.clockStyleIndex]?.get(key)
}

private fun setStyleOverride(config: DisplayConfig, key: String, value: String, onConfigChange: (DisplayConfig) -> Unit) {
    val idx = config.clock.clockStyleIndex
    val current = config.clock.styleOverrides[idx] ?: emptyMap()
    onConfigChange(config.copy(clock = config.clock.copy(
        styleOverrides = config.clock.styleOverrides + (idx to (current + (key to value)))
    )))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartDisplayScreen(
    onNavigateToDashboard: () -> Unit
) {
    val context = LocalContext.current
    var config by remember { mutableStateOf(DisplayStore.loadConfig(context)) }
    var showSettings by remember { mutableStateOf(false) }
    var weatherData by remember { mutableStateOf<WeatherData?>(null) }
    var trackInfo by remember { mutableStateOf(TrackInfo()) }
    var newsData by remember { mutableStateOf<NewsData?>(null) }
    val isLandscape = LocalConfiguration.current.screenWidthDp > LocalConfiguration.current.screenHeightDp

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        val urisStr = uris.map { it.toString() }
        if (urisStr.isNotEmpty()) {
            val updated = config.photo.photoUris + urisStr
            config = config.copy(photo = config.photo.copy(photoUris = updated))
            DisplayStore.saveConfig(context, config)
        }
    }

    LaunchedEffect(Unit) {
        val cached = WeatherService.getCached()
        if (cached != null) weatherData = cached
        WeatherService.fetchWeather(config.weather.latitude, config.weather.longitude)?.let {
            weatherData = it
        }
    }

    LaunchedEffect(Unit) {
        val cached = NewsService.getCached()
        if (cached != null) newsData = cached
        NewsService.fetchTopHeadlines()?.let { newsData = it }
    }

    LaunchedEffect(Unit) {
        while (true) {
            trackInfo = MusicController.getCurrentTrack(context)
            delay(2000)
        }
    }

    val currentStyle = ClockStyles.all.getOrElse(config.clock.clockStyleIndex) { ClockStyles.all[0] }
    val textColor = Color(android.graphics.Color.parseColor(
        config.clock.styleOverrides[config.clock.clockStyleIndex]?.get("textColorHex")
            ?: currentStyle.textColorHex
    ))
    val bgHex = getStyleOverride(config, "backgroundColorHex") ?: currentStyle.backgroundColorHex
    val bgAlpha = (getStyleOverride(config, "backgroundOpacity")?.toFloatOrNull() ?: currentStyle.backgroundOpacity).coerceIn(0f, 1f)
    val bgColor = Color(android.graphics.Color.parseColor(bgHex)).copy(alpha = bgAlpha)

    val window = (context as? Activity)?.window
    LaunchedEffect(config.clock.isDimmed, config.clock.nightBrightnessPercent) {
        window?.let {
            val lp = it.attributes
            lp.screenBrightness = if (config.clock.isDimmed) {
                (config.clock.nightBrightnessPercent / 100f).coerceIn(0.01f, 1f)
            } else {
                WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
            it.attributes = lp
        }
    }

    var dragAccumulator by remember { mutableStateOf(0f) }
    val usePhotoBg = config.photo.useAsBackground && config.photo.photoUris.isNotEmpty()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (usePhotoBg) Modifier
                else Modifier.background(bgColor)
            )
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        val threshold = 80f
                        if (dragAccumulator > threshold) {
                            val newIdx = ((config.clock.clockStyleIndex - 1) + 100) % 100
                            config = config.copy(clock = config.clock.copy(clockStyleIndex = newIdx))
                            DisplayStore.saveConfig(context, config)
                        } else if (dragAccumulator < -threshold) {
                            val newIdx = (config.clock.clockStyleIndex + 1) % 100
                            config = config.copy(clock = config.clock.copy(clockStyleIndex = newIdx))
                            DisplayStore.saveConfig(context, config)
                        }
                        dragAccumulator = 0f
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        dragAccumulator += dragAmount
                    }
                )
            }
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = { showSettings = !showSettings }
            )
    ) {
        if (usePhotoBg) {
            Box(modifier = Modifier.fillMaxSize()) {
                PhotoCarousel(
                    photoUris = config.photo.photoUris,
                    effect = config.photo.transitionEffect,
                    intervalSecs = config.photo.intervalSecs,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(bgColor)
                )
            }
        }

        when (config.layout) {
            LayoutType.CLOCK_SINGLE -> ClockSingleLayout(
                config, weatherData, trackInfo, textColor, newsData,
                onPlayPause = { MusicController.sendMediaAction(context, if (trackInfo.isPlaying) "pause" else "play") },
                onNext = { MusicController.sendMediaAction(context, "next") },
                onPrev = { MusicController.sendMediaAction(context, "prev") }
            )
            LayoutType.DUAL_PANEL -> DualPanelLayout(
                config, weatherData, trackInfo, textColor, newsData,
                onPlayPause = { MusicController.sendMediaAction(context, if (trackInfo.isPlaying) "pause" else "play") },
                onNext = { MusicController.sendMediaAction(context, "next") },
                onPrev = { MusicController.sendMediaAction(context, "prev") }
            )
            LayoutType.FOUR_WIDGET -> FourWidgetLayout(
                config, weatherData, trackInfo, textColor, newsData,
                onPlayPause = { MusicController.sendMediaAction(context, if (trackInfo.isPlaying) "pause" else "play") },
                onNext = { MusicController.sendMediaAction(context, "next") },
                onPrev = { MusicController.sendMediaAction(context, "prev") }
            )
        }

        AnimatedVisibility(
            visible = showSettings,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            SettingsOverlay(
                config = config,
                onConfigChange = { config = it; DisplayStore.saveConfig(context, it) },
                onDismiss = { showSettings = false },
                onNavigateToDashboard = onNavigateToDashboard,
                photoPickerLauncher = photoPickerLauncher,
                onRefreshWeather = {
                    WeatherService.fetchWeather(config.weather.latitude, config.weather.longitude)?.let {
                        weatherData = it
                    }
                },
                isLandscape = isLandscape
            )
        }
    }
}

private val tabTitles = listOf("Orologio", "Widget", "Sfondo", "Notte", "Scene")
private val tabIcons = listOf("\u23F0", "\uD83D\uDCCB", "\uD83C\uDFA8", "\uD83C\uDF19", "\uD83C\uDFAC")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsOverlay(
    config: DisplayConfig,
    onConfigChange: (DisplayConfig) -> Unit,
    onDismiss: () -> Unit,
    onNavigateToDashboard: () -> Unit,
    photoPickerLauncher: ActivityResultLauncher<String>,
    onRefreshWeather: () -> Unit,
    isLandscape: Boolean
) {
    val context = LocalContext.current
    var presets by remember { mutableStateOf(DisplayStore.loadPresets(context)) }
    val currentStyle = ClockStyles.all.getOrElse(config.clock.clockStyleIndex) { ClockStyles.all[0] }
    var selectedTab by remember { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
    ) {
        val cardModifier = if (isLandscape) {
            Modifier
                .width(380.dp)
                .align(Alignment.CenterEnd)
                .padding(16.dp)
        } else {
            Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        }

        Card(
            modifier = cardModifier
                .clickable(enabled = false) { },
            colors = CardDefaults.cardColors(containerColor = CardDark),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Smart Display", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Row {
                        IconButton(onClick = onNavigateToDashboard) {
                            Icon(Icons.Default.Home, contentDescription = "Dashboard", tint = TextSecondary)
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Chiudi", tint = TextSecondary)
                        }
                    }
                }

                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = ElegantPurple,
                    divider = {}
                ) {
                    tabTitles.forEachIndexed { idx, title ->
                        Tab(
                            selected = selectedTab == idx,
                            onClick = { selectedTab = idx },
                            text = {
                                Text(
                                    "${tabIcons[idx]} $title",
                                    fontSize = 10.sp,
                                    fontWeight = if (selectedTab == idx) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (selectedTab) {
                        0 -> ClockTab(config, onConfigChange, currentStyle)
                        1 -> WidgetTab(config, onConfigChange)
                        2 -> BackgroundTab(config, onConfigChange, photoPickerLauncher, currentStyle)
                        3 -> NightTab(config, onConfigChange, onRefreshWeather)
                        4 -> SceneTab(config, onConfigChange, presets)
                    }
                }
            }
        }
    }
}

@Composable
private fun ClockTab(config: DisplayConfig, onConfigChange: (DisplayConfig) -> Unit, currentStyle: ClockStyleDef) {
    SectionHeader("Layout")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        LayoutType.entries.forEach { lt ->
            FilterChip(
                selected = config.layout == lt,
                onClick = { onConfigChange(config.copy(layout = lt)) },
                label = { Text(lt.label, fontSize = 11.sp) }
            )
        }
    }

    SectionHeader("Stile")
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(ElegantPurple.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        Column {
            Text(
                text = "${currentStyle.category} \u2022 ${currentStyle.name}",
                color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium
            )
            Text(
                text = currentStyle.description,
                color = TextTertiary, fontSize = 10.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = {
                        val newIdx = ((config.clock.clockStyleIndex - 1) + 100) % 100
                        onConfigChange(config.copy(clock = config.clock.copy(clockStyleIndex = newIdx)))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElegantPurple),
                    modifier = Modifier.weight(1f)
                ) { Text("\u25C0 Prec", fontSize = 11.sp) }
                Button(
                    onClick = {
                        val newIdx = (config.clock.clockStyleIndex + 1) % 100
                        onConfigChange(config.copy(clock = config.clock.copy(clockStyleIndex = newIdx)))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElegantPurple),
                    modifier = Modifier.weight(1f)
                ) { Text("Succ \u25B6", fontSize = 11.sp) }
            }
        }
    }

    SectionHeader("Personalizza")
    if (!currentStyle.isAnalog) {
        ColorChipRow("Font:", listOf(
            "default" to "Base", "serif" to "Serif", "monospace" to "Mono"
        ), getStyleOverride(config, "fontFamily")) {
            setStyleOverride(config, "fontFamily", it, onConfigChange)
        }
        ColorChipRow("Testo:", listOf(
            "#FFFFFF" to "Bianco", "#4FC3F7" to "Azzurro", "#CE93D8" to "Viola",
            "#81C784" to "Verde", "#FFB74D" to "Oro", "#EF5350" to "Rosso", "#FF6B6B" to "Rosa"
        ), getStyleOverride(config, "textColorHex")) {
            onConfigChange(config.copy(clock = config.clock.copy(
                styleOverrides = config.clock.styleOverrides + (config.clock.clockStyleIndex to
                    ((config.clock.styleOverrides[config.clock.clockStyleIndex] ?: emptyMap()) + ("textColorHex" to it)))
            )))
        }
        ColorChipRow("Accento:", listOf(
            "#D0BCFF" to "Default", "#1DB954" to "Verde", "#FF4081" to "Rosa",
            "#FFD740" to "Giallo", "#00E5FF" to "Ciano", "#FF6E40" to "Aranc"
        ), getStyleOverride(config, "accentColorHex")) {
            onConfigChange(config.copy(clock = config.clock.copy(
                styleOverrides = config.clock.styleOverrides + (config.clock.clockStyleIndex to
                    ((config.clock.styleOverrides[config.clock.clockStyleIndex] ?: emptyMap()) + ("accentColorHex" to it)))
            )))
        }
    } else {
        ColorChipRow("Lancette:", listOf(
            "#FFFFFF" to "Bianco", "#4FC3F7" to "Azzurro", "#FFD700" to "Oro",
            "#EF5350" to "Rosso", "#66BB6A" to "Verde"
        ), getStyleOverride(config, "analogHandColorHex")) {
            onConfigChange(config.copy(clock = config.clock.copy(
                styleOverrides = config.clock.styleOverrides + (config.clock.clockStyleIndex to
                    ((config.clock.styleOverrides[config.clock.clockStyleIndex] ?: emptyMap()) + mapOf("analogHandColorHex" to it, "analogTickColorHex" to it)))
            )))
        }
    }

    SizeSlider("Dimensione:", config, "timeFontMultiplier", currentStyle.timeFontMultiplier, 0.3f, 2.5f, 21, onConfigChange = onConfigChange)
    SizeSlider("Spessore:", config, "fontWeight", currentStyle.fontWeight.toFloat(), 100f, 900f, 7, isInt = true, onConfigChange = onConfigChange)

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Secondi" to "showSeconds", "Data" to "showDate").forEach { (label, key) ->
            val ov = getStyleOverride(config, key)
            val enabled = when {
                ov == "true" -> true
                ov == "false" -> false
                key == "showSeconds" -> currentStyle.showSeconds
                key == "showDate" -> currentStyle.showDate
                else -> true
            }
            FilterChip(
                selected = enabled,
                onClick = {
                    onConfigChange(config.copy(clock = config.clock.copy(
                        styleOverrides = config.clock.styleOverrides + (config.clock.clockStyleIndex to
                            ((config.clock.styleOverrides[config.clock.clockStyleIndex] ?: emptyMap()) + (key to (!enabled).toString())))
                    )))
                },
                label = { Text("Mostra $label", fontSize = 10.sp) }
            )
        }
    }
}

@Composable
private fun WidgetTab(config: DisplayConfig, onConfigChange: (DisplayConfig) -> Unit) {
    SectionHeader("Widget attivi")
    val widgets = listOf(
        "Data" to "showDate", "Meteo" to "showWeather", "Batteria" to "showBattery",
        "Musica" to "showMusic", "Notizie" to "showNews", "Timer" to "showTimer",
        "Cronometro" to "showStopwatch", "Conto alla rovescia" to "showCountdown",
        "eWeLink" to "showEWeLink", "Foto" to "showPhotos"
    )
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        widgets.forEach { (label, key) ->
            val enabled = when (key) {
                "showDate" -> config.widgets.showDate
                "showWeather" -> config.widgets.showWeather
                "showBattery" -> config.widgets.showBattery
                "showMusic" -> config.widgets.showMusic
                "showNews" -> config.widgets.showNews
                "showTimer" -> config.widgets.showTimer
                "showStopwatch" -> config.widgets.showStopwatch
                "showCountdown" -> config.widgets.showCountdown
                "showEWeLink" -> config.widgets.showEWeLink
                "showPhotos" -> config.widgets.showPhotos
                else -> false
            }
            FilterChip(
                selected = enabled,
                onClick = {
                    val w = config.widgets
                    onConfigChange(config.copy(widgets = when (key) {
                        "showDate" -> w.copy(showDate = !w.showDate)
                        "showWeather" -> w.copy(showWeather = !w.showWeather)
                        "showBattery" -> w.copy(showBattery = !w.showBattery)
                        "showMusic" -> w.copy(showMusic = !w.showMusic)
                        "showNews" -> w.copy(showNews = !w.showNews)
                        "showTimer" -> w.copy(showTimer = !w.showTimer)
                        "showStopwatch" -> w.copy(showStopwatch = !w.showStopwatch)
                        "showCountdown" -> w.copy(showCountdown = !w.showCountdown)
                        "showEWeLink" -> w.copy(showEWeLink = !w.showEWeLink)
                        "showPhotos" -> w.copy(showPhotos = !w.showPhotos)
                        else -> w
                    }))
                },
                label = { Text(label, fontSize = 10.sp) }
            )
        }
    }
}

@Composable
private fun BackgroundTab(
    config: DisplayConfig,
    onConfigChange: (DisplayConfig) -> Unit,
    photoPickerLauncher: ActivityResultLauncher<String>,
    currentStyle: ClockStyleDef
) {
    SectionHeader("Colore sfondo")
    ColorChipRow("Colore:", listOf(
        "#000000" to "Nero", "#1A1A2E" to "Blu scuro", "#2C1A0E" to "Marrone",
        "#1B3B1B" to "Verde scuro", "#3E2723" to "Legno", "#0A1628" to "Notte",
        "#1A0033" to "Viola scuro"
    ), getStyleOverride(config, "backgroundColorHex") ?: currentStyle.backgroundColorHex) {
        setStyleOverride(config, "backgroundColorHex", it, onConfigChange)
    }

    SizeSlider("Opacita:", config, "backgroundOpacity", currentStyle.backgroundOpacity, 0f, 1f, 19, onConfigChange = onConfigChange)

    Spacer(Modifier.height(4.dp))
    SectionHeader("Foto galleria")
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Button(
            onClick = { photoPickerLauncher.launch("image/*") },
            colors = ButtonDefaults.buttonColors(containerColor = ElegantPurple),
            modifier = Modifier.weight(1f)
        ) { Text("\uD83D\uDDBC Seleziona foto", fontSize = 10.sp) }
        if (config.photo.photoUris.isNotEmpty()) {
            Button(
                onClick = { onConfigChange(config.copy(photo = config.photo.copy(photoUris = emptyList()))) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF555555)),
                modifier = Modifier.weight(0.4f)
            ) { Text("\uD83D\uDDD1 Cancella", fontSize = 10.sp) }
        }
    }
    if (config.photo.photoUris.isNotEmpty()) {
        Text("${config.photo.photoUris.size} foto selezionate", color = TextTertiary, fontSize = 11.sp)

        ColorChipRow("Effetto:", TransitionEffect.entries.map { it.name to it.label },
            config.photo.transitionEffect.name) {
            onConfigChange(config.copy(photo = config.photo.copy(transitionEffect = TransitionEffect.valueOf(it))))
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Intervallo:", color = TextTertiary, fontSize = 11.sp, modifier = Modifier.weight(0.35f))
            var sliderVal by remember { mutableFloatStateOf(config.photo.intervalSecs.toFloat()) }
            Text("${sliderVal.toInt()}s", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(0.15f))
            Slider(
                value = sliderVal,
                onValueChange = { sliderVal = it },
                onValueChangeFinished = {
                    onConfigChange(config.copy(photo = config.photo.copy(intervalSecs = sliderVal.toInt().coerceIn(3, 120))))
                },
                valueRange = 3f..120f, steps = 38,
                modifier = Modifier.weight(0.5f).height(24.dp),
                colors = SliderDefaults.colors(thumbColor = ElegantPurple, activeTrackColor = ElegantPurple)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Usa come sfondo", color = TextPrimary, fontSize = 12.sp)
            Switch(
                checked = config.photo.useAsBackground,
                onCheckedChange = { onConfigChange(config.copy(photo = config.photo.copy(useAsBackground = it))) },
                colors = SwitchDefaults.colors(checkedThumbColor = ElegantPurple, checkedTrackColor = ElegantPurple.copy(alpha = 0.4f))
            )
        }
    }
}

@Composable
private fun NightTab(config: DisplayConfig, onConfigChange: (DisplayConfig) -> Unit, onRefreshWeather: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("\uD83C\uDF19", fontSize = 20.sp)
            Spacer(Modifier.width(8.dp))
            Text("Modalita notte", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        Switch(
            checked = config.clock.isDimmed,
            onCheckedChange = { onConfigChange(config.copy(clock = config.clock.copy(isDimmed = it))) },
            colors = SwitchDefaults.colors(checkedThumbColor = ElegantPurple, checkedTrackColor = ElegantPurple.copy(alpha = 0.4f))
        )
    }

    if (config.clock.isDimmed) {
        AnimatedVisibility(visible = true, enter = expandVertically(), exit = shrinkVertically()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Luminosita: ${config.clock.nightBrightnessPercent}%",
                    color = TextTertiary, fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
                )
                Slider(
                    value = config.clock.nightBrightnessPercent.toFloat(),
                    onValueChange = { onConfigChange(config.copy(clock = config.clock.copy(nightBrightnessPercent = it.toInt()))) },
                    valueRange = 5f..60f, steps = 10,
                    colors = SliderDefaults.colors(thumbColor = ElegantPurple, activeTrackColor = ElegantPurple)
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("5%", color = TextTertiary, fontSize = 10.sp)
                    Text("60%", color = TextTertiary, fontSize = 10.sp)
                }
            }
        }
    }

    Spacer(Modifier.height(8.dp))
    SectionHeader("Altro")
    Button(
        onClick = onRefreshWeather,
        colors = ButtonDefaults.buttonColors(containerColor = ElegantPurple),
        modifier = Modifier.fillMaxWidth()
    ) { Text("\uD83C\uDF26\uFE0F Aggiorna meteo", fontSize = 12.sp) }
}

@Composable
private fun SceneTab(
    config: DisplayConfig,
    onConfigChange: (DisplayConfig) -> Unit,
    presets: List<Preset>
) {
    val context = LocalContext.current
    SectionHeader("Scene predefinite")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        presets.forEach { preset ->
            FilterChip(
                selected = config.currentPreset == preset.id,
                onClick = {
                    onConfigChange(preset.config)
                    DisplayStore.saveActivePresetId(context, preset.id)
                },
                label = { Text(preset.name, fontSize = 10.sp) }
            )
        }
    }
}

@Composable
private fun ColorChipRow(
    label: String,
    items: List<Pair<String, String>>,
    selectedValue: String?,
    onSelect: (String) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TextTertiary, fontSize = 11.sp, modifier = Modifier.weight(0.3f))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(0.7f)) {
            items.forEach { (value, display) ->
                FilterChip(
                    selected = selectedValue == value,
                    onClick = { onSelect(value) },
                    label = { Text(display, fontSize = 8.sp) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SizeSlider(
    label: String,
    config: DisplayConfig,
    key: String,
    defaultVal: Float,
    rangeMin: Float,
    rangeMax: Float,
    steps: Int,
    isInt: Boolean = false,
    onConfigChange: (DisplayConfig) -> Unit = {}
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TextTertiary, fontSize = 11.sp, modifier = Modifier.weight(0.3f))
        var sliderVal by remember(config.clock.clockStyleIndex, key) {
            mutableFloatStateOf(getStyleOverride(config, key)?.toFloatOrNull() ?: defaultVal)
        }
        Slider(
            value = sliderVal,
            onValueChange = { sliderVal = it },
            onValueChangeFinished = {
                val formatted = if (isInt) sliderVal.toInt().toString()
                    else String.format("%.1f", sliderVal)
                setStyleOverride(config, key, formatted, onConfigChange)
            },
            valueRange = rangeMin..rangeMax,
            steps = steps,
            modifier = Modifier.weight(0.7f).height(24.dp),
            colors = SliderDefaults.colors(thumbColor = ElegantPurple, activeTrackColor = ElegantPurple)
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(title, color = ElegantPurple, fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
        letterSpacing = 1.sp)
}
