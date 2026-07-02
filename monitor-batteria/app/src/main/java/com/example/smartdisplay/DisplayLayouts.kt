package com.example.smartdisplay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

@Composable
private fun photoWidget(config: DisplayConfig, textColor: Color, modifier: Modifier = Modifier) {
    if (config.widgets.showPhotos && config.photo.photoUris.isNotEmpty()) {
        PhotoWidgetContent(
            photoUris = config.photo.photoUris,
            effect = config.photo.transitionEffect,
            intervalSecs = config.photo.intervalSecs,
            textColor = textColor,
            modifier = modifier
        )
    }
}

@Composable
fun ClockSingleLayout(
    config: DisplayConfig,
    weatherData: WeatherData?,
    trackInfo: TrackInfo,
    textColor: Color,
    newsData: NewsData?,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit
) {
    val isLandscape = LocalConfiguration.current.screenWidthDp > LocalConfiguration.current.screenHeightDp

    if (isLandscape) {
        Row(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ClockWidget(
                config = config.clock,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            Column(
                modifier = Modifier.weight(0.6f).fillMaxHeight().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (config.widgets.showWeather && weatherData != null) {
                    WeatherWidgetContent(weatherData = weatherData, textColor = textColor,
                        modifier = Modifier.padding(vertical = 8.dp))
                }
                if (config.widgets.showMusic) {
                    MusicWidgetContent(track = trackInfo, onPlayPause = onPlayPause,
                        onNext = onNext, onPrev = onPrev, textColor = textColor)
                }
                if (config.widgets.showDate) {
                    DateWidget(textColor = textColor, dateFormat = config.clock.dateFormat)
                }
                if (config.widgets.showBattery) {
                    BatteryWidget(textColor = textColor)
                }
                if (config.widgets.showNews) {
                    NewsWidgetContent(newsData = newsData, textColor = textColor,
                        modifier = Modifier.padding(vertical = 4.dp))
                }
                if (config.widgets.showTimer) {
                    TimerWidgetContent(textColor = textColor,
                        modifier = Modifier.padding(vertical = 4.dp))
                }
                if (config.widgets.showStopwatch) {
                    StopwatchWidgetContent(textColor = textColor,
                        modifier = Modifier.padding(vertical = 4.dp))
                }
                if (config.widgets.showCountdown) {
                    CountdownWidgetContent(textColor = textColor,
                        modifier = Modifier.padding(vertical = 4.dp))
                }
                if (config.widgets.showEWeLink) {
                    EWeLinkWidgetContent(textColor = textColor,
                        modifier = Modifier.padding(vertical = 4.dp))
                }
                photoWidget(config, textColor, Modifier.padding(vertical = 4.dp))
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            ClockWidget(
                config = config.clock,
                modifier = Modifier.fillMaxSize()
            )

            if (config.widgets.showWeather && weatherData != null) {
                WeatherWidgetContent(
                    weatherData = weatherData,
                    textColor = textColor,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
                )
            }

            if (config.widgets.showTimer || config.widgets.showStopwatch || config.widgets.showCountdown || config.widgets.showEWeLink) {
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .padding(end = 8.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    if (config.widgets.showTimer) {
                        TimerWidgetContent(textColor = textColor)
                    }
                    if (config.widgets.showStopwatch) {
                        StopwatchWidgetContent(textColor = textColor,
                            modifier = Modifier.padding(top = 8.dp))
                    }
                    if (config.widgets.showCountdown) {
                        CountdownWidgetContent(textColor = textColor,
                            modifier = Modifier.padding(top = 8.dp))
                    }
                    if (config.widgets.showEWeLink) {
                        EWeLinkWidgetContent(textColor = textColor,
                            modifier = Modifier.padding(top = 8.dp))
                    }
                    if (config.widgets.showPhotos && config.photo.photoUris.isNotEmpty()) {
                        PhotoWidgetContent(
                            photoUris = config.photo.photoUris,
                            effect = config.photo.transitionEffect,
                            intervalSecs = config.photo.intervalSecs,
                            textColor = textColor,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (config.widgets.showDate) {
                    DateWidget(textColor = textColor, dateFormat = config.clock.dateFormat,
                        modifier = Modifier.weight(1f))
                }
                if (config.widgets.showBattery) {
                    BatteryWidget(textColor = textColor, modifier = Modifier.weight(1f))
                }
                if (config.widgets.showMusic) {
                    MusicWidgetContent(track = trackInfo, onPlayPause = onPlayPause,
                        onNext = onNext, onPrev = onPrev, textColor = textColor,
                        modifier = Modifier.weight(1f))
                }
                if (config.widgets.showNews) {
                    NewsWidgetContent(newsData = newsData, textColor = textColor,
                        modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun DualPanelLayout(
    config: DisplayConfig,
    weatherData: WeatherData?,
    trackInfo: TrackInfo,
    textColor: Color,
    newsData: NewsData?,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ClockWidget(
            config = config.clock,
            modifier = Modifier.weight(1f).fillMaxHeight()
        )

        Column(
            modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (config.widgets.showDate) {
                DateWidget(textColor = textColor, dateFormat = config.clock.dateFormat)
            }
            if (config.widgets.showWeather) {
                WeatherWidgetContent(weatherData = weatherData, textColor = textColor,
                    modifier = Modifier.padding(vertical = 8.dp))
            }
            if (config.widgets.showMusic) {
                MusicWidgetContent(track = trackInfo, onPlayPause = onPlayPause,
                    onNext = onNext, onPrev = onPrev, textColor = textColor)
            }
            if (config.widgets.showBattery) {
                BatteryWidget(textColor = textColor)
            }
            if (config.widgets.showNews) {
                NewsWidgetContent(newsData = newsData, textColor = textColor,
                    modifier = Modifier.padding(vertical = 4.dp))
            }
            if (config.widgets.showTimer) {
                TimerWidgetContent(textColor = textColor,
                    modifier = Modifier.padding(vertical = 4.dp))
            }
            if (config.widgets.showStopwatch) {
                StopwatchWidgetContent(textColor = textColor,
                    modifier = Modifier.padding(vertical = 4.dp))
            }
            if (config.widgets.showCountdown) {
                CountdownWidgetContent(textColor = textColor,
                    modifier = Modifier.padding(vertical = 4.dp))
            }
            if (config.widgets.showEWeLink) {
                EWeLinkWidgetContent(textColor = textColor,
                    modifier = Modifier.padding(vertical = 4.dp))
            }
            photoWidget(config, textColor, Modifier.padding(vertical = 4.dp))
        }
    }
}

@Composable
fun FourWidgetLayout(
    config: DisplayConfig,
    weatherData: WeatherData?,
    trackInfo: TrackInfo,
    textColor: Color,
    newsData: NewsData?,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit
) {
    val isLandscape = LocalConfiguration.current.screenWidthDp > LocalConfiguration.current.screenHeightDp

    if (isLandscape) {
        Row(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(6.dp)) {
                ClockWidget(config = config.clock)
            }
            Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(6.dp)) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (config.widgets.showDate) DateWidget(textColor = textColor)
                    if (config.widgets.showWeather) WeatherWidgetContent(
                        weatherData = weatherData, textColor = textColor)
                }
            }
            Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(6.dp)) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (config.widgets.showMusic) MusicWidgetContent(
                        track = trackInfo, onPlayPause = onPlayPause,
                        onNext = onNext, onPrev = onPrev, textColor = textColor)
                    if (config.widgets.showNews) NewsWidgetContent(
                        newsData = newsData, textColor = textColor)
                    if (config.widgets.showTimer) TimerWidgetContent(textColor = textColor)
                    if (config.widgets.showCountdown) CountdownWidgetContent(textColor = textColor)
                    if (config.widgets.showEWeLink) EWeLinkWidgetContent(textColor = textColor)
                    photoWidget(config, textColor)
                }
            }
            Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(6.dp)) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (config.widgets.showBattery) BatteryWidget(textColor = textColor)
                    if (config.widgets.showStopwatch) StopwatchWidgetContent(textColor = textColor)
                }
            }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Box(modifier = Modifier.weight(1f).padding(6.dp)) {
                    ClockWidget(config = config.clock)
                }
                Box(modifier = Modifier.weight(1f).padding(6.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (config.widgets.showDate) DateWidget(textColor = textColor)
                        if (config.widgets.showWeather) WeatherWidgetContent(
                            weatherData = weatherData, textColor = textColor)
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Box(modifier = Modifier.weight(1f).padding(6.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (config.widgets.showMusic) MusicWidgetContent(
                            track = trackInfo, onPlayPause = onPlayPause,
                            onNext = onNext, onPrev = onPrev, textColor = textColor)
                        if (config.widgets.showTimer) TimerWidgetContent(textColor = textColor)
                        if (config.widgets.showEWeLink) EWeLinkWidgetContent(textColor = textColor)
                        photoWidget(config, textColor)
                    }
                }
                Box(modifier = Modifier.weight(1f).padding(6.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (config.widgets.showNews) NewsWidgetContent(
                            newsData = newsData, textColor = textColor)
                        if (config.widgets.showBattery) BatteryWidget(textColor = textColor)
                        if (config.widgets.showStopwatch) StopwatchWidgetContent(textColor = textColor)
                        if (config.widgets.showCountdown) CountdownWidgetContent(textColor = textColor)
                        if (config.widgets.showEWeLink) EWeLinkWidgetContent(textColor = textColor)
                    }
                }
            }
        }
    }
}
