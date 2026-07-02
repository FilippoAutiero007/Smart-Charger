package com.example.smartdisplay

enum class LayoutType(val label: String) {
    CLOCK_SINGLE("Orologio singolo"),
    DUAL_PANEL("Doppio pannello"),
    FOUR_WIDGET("Quattro widget")
}

enum class TransitionEffect(val label: String) {
    FADE("Dissolvenza"),
    SLIDE("Scorrimento"),
    CUT("Taglio netto"),
    ZOOM("Zoom")
}

data class ClockConfig(
    val clockStyleIndex: Int = 0,
    val fontFamily: String = "default",
    val textColorHex: String = "#FFFFFF",
    val accentColorHex: String = "#D0BCFF",
    val secondsColorHex: String = "#FF6B6B",
    val showSeconds: Boolean = true,
    val showDate: Boolean = true,
    val dateFormat: String = "EEEE, d MMMM",
    val backgroundColorHex: String = "#000000",
    val backgroundOpacity: Float = 0.5f,
    val analogTickColorHex: String = "#FFFFFF",
    val analogHandColorHex: String = "#FFFFFF",
    val analogShowNumbers: Boolean = true,
    val autoNightBrightness: Boolean = false,
    val isDimmed: Boolean = false,
    val nightBrightnessPercent: Int = 30,
    val timeFontSizeMultiplier: Float = 1.0f,
    val styleOverrides: Map<Int, Map<String, String>> = emptyMap()
)

data class WeatherConfig(
    val latitude: String = "41.9028",
    val longitude: String = "12.4964",
    val showForecast: Boolean = true,
    val forecastDays: Int = 3,
    val unit: String = "celsius"
)

data class PhotoConfig(
    val photoUris: List<String> = emptyList(),
    val transitionEffect: TransitionEffect = TransitionEffect.FADE,
    val intervalSecs: Int = 10,
    val useAsBackground: Boolean = false
)

data class WidgetConfig(
    val showWeather: Boolean = true,
    val showDate: Boolean = true,
    val showBattery: Boolean = true,
    val showMusic: Boolean = false,
    val showNews: Boolean = false,
    val showCalendar: Boolean = false,
    val showTimer: Boolean = false,
    val showStopwatch: Boolean = false,
    val showCountdown: Boolean = false,
    val showEWeLink: Boolean = false,
    val showPhotos: Boolean = false
)

data class DisplayConfig(
    val layout: LayoutType = LayoutType.CLOCK_SINGLE,
    val clock: ClockConfig = ClockConfig(),
    val weather: WeatherConfig = WeatherConfig(),
    val widgets: WidgetConfig = WidgetConfig(),
    val photo: PhotoConfig = PhotoConfig(),
    val fullScreen: Boolean = true,
    val allowPortrait: Boolean = false,
    val currentPreset: String = "default",
    val backgroundPhotoUri: String? = null,
    val showStatusBar: Boolean = false
)

data class Preset(
    val id: String,
    val name: String,
    val config: DisplayConfig,
    val icon: String = "clock"
)
