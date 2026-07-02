package com.example.smartdisplay

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object DisplayStore {
    private const val PREFS_NAME = "smart_display"
    private const val KEY_CONFIG = "display_config"
    private const val KEY_PRESETS = "presets_json"
    private const val KEY_ACTIVE_PRESET = "active_preset"

    fun loadConfig(context: Context): DisplayConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_CONFIG, null) ?: return DisplayConfig()
        return try { parseConfig(JSONObject(raw)) } catch (_: Exception) { DisplayConfig() }
    }

    fun saveConfig(context: Context, config: DisplayConfig) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_CONFIG, serializeConfig(config).toString()).apply()
    }

    fun loadPresets(context: Context): List<Preset> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_PRESETS, null) ?: return defaultPresets()
        return try {
            val arr = JSONArray(raw)
            buildList { for (i in 0 until arr.length()) add(parsePreset(arr.getJSONObject(i))) }
                .ifEmpty { defaultPresets() }
        } catch (_: Exception) { defaultPresets() }
    }

    fun savePresets(context: Context, presets: List<Preset>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = JSONArray()
        presets.forEach { arr.put(serializePreset(it)) }
        prefs.edit().putString(KEY_PRESETS, arr.toString()).apply()
    }

    fun loadActivePresetId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_ACTIVE_PRESET, "") ?: ""
    }

    fun saveActivePresetId(context: Context, presetId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_ACTIVE_PRESET, presetId).apply()
    }

    fun defaultPresets(): List<Preset> = listOf(
        Preset("default", "Default", DisplayConfig()),
        Preset("nightstand", "Comodino notturno", DisplayConfig(
            layout = LayoutType.CLOCK_SINGLE,
            clock = ClockConfig(clockStyleIndex = 30, showSeconds = false),
            widgets = WidgetConfig(showWeather = false, showMusic = false, showBattery = false)
        ), icon = "nightlight"),
        Preset("desk", "Scrivania", DisplayConfig(
            layout = LayoutType.DUAL_PANEL,
            clock = ClockConfig(clockStyleIndex = 0, showSeconds = true),
            widgets = WidgetConfig(showWeather = true, showBattery = true, showDate = true)
        ), icon = "desk"),
        Preset("music", "Musica", DisplayConfig(
            layout = LayoutType.DUAL_PANEL,
            clock = ClockConfig(clockStyleIndex = 95, showSeconds = false),
            widgets = WidgetConfig(showMusic = true, showBattery = true, showDate = true, showWeather = false)
        ), icon = "music_note")
    )

    private fun serializeConfig(c: DisplayConfig): JSONObject = JSONObject().apply {
        put("layout", c.layout.name)
        put("fullScreen", c.fullScreen)
        put("allowPortrait", c.allowPortrait)
        put("currentPreset", c.currentPreset)
        c.backgroundPhotoUri?.let { put("backgroundPhotoUri", it) }
        put("showStatusBar", c.showStatusBar)

        put("photo", JSONObject().apply {
            put("photoUris", JSONArray(c.photo.photoUris))
            put("transitionEffect", c.photo.transitionEffect.name)
            put("intervalSecs", c.photo.intervalSecs)
            put("useAsBackground", c.photo.useAsBackground)
        })

        put("clock", JSONObject().apply {
            put("clockStyleIndex", c.clock.clockStyleIndex)
            put("fontFamily", c.clock.fontFamily)
            put("textColorHex", c.clock.textColorHex)
            put("accentColorHex", c.clock.accentColorHex)
            put("secondsColorHex", c.clock.secondsColorHex)
            put("showSeconds", c.clock.showSeconds)
            put("showDate", c.clock.showDate)
            put("dateFormat", c.clock.dateFormat)
            put("backgroundColorHex", c.clock.backgroundColorHex)
            put("backgroundOpacity", c.clock.backgroundOpacity)
            put("analogTickColorHex", c.clock.analogTickColorHex)
            put("analogHandColorHex", c.clock.analogHandColorHex)
            put("analogShowNumbers", c.clock.analogShowNumbers)
            put("autoNightBrightness", c.clock.autoNightBrightness)
            put("isDimmed", c.clock.isDimmed)
            put("nightBrightnessPercent", c.clock.nightBrightnessPercent)
            put("timeFontSizeMultiplier", c.clock.timeFontSizeMultiplier)
            if (c.clock.styleOverrides.isNotEmpty()) {
                put("styleOverrides", JSONObject().apply {
                    c.clock.styleOverrides.forEach { (idx, map) ->
                        put(idx.toString(), JSONObject().apply {
                            map.forEach { (k, v) -> put(k, v) }
                        })
                    }
                })
            }
        })

        put("weather", JSONObject().apply {
            put("latitude", c.weather.latitude)
            put("longitude", c.weather.longitude)
            put("showForecast", c.weather.showForecast)
            put("forecastDays", c.weather.forecastDays)
            put("unit", c.weather.unit)
        })

        put("widgets", JSONObject().apply {
            put("showWeather", c.widgets.showWeather)
            put("showDate", c.widgets.showDate)
            put("showBattery", c.widgets.showBattery)
            put("showMusic", c.widgets.showMusic)
            put("showNews", c.widgets.showNews)
            put("showCalendar", c.widgets.showCalendar)
            put("showTimer", c.widgets.showTimer)
            put("showStopwatch", c.widgets.showStopwatch)
            put("showCountdown", c.widgets.showCountdown)
            put("showEWeLink", c.widgets.showEWeLink)
            put("showPhotos", c.widgets.showPhotos)
        })
    }

    private fun parseConfig(obj: JSONObject): DisplayConfig {
        val clockObj = obj.optJSONObject("clock") ?: JSONObject()
        val weatherObj = obj.optJSONObject("weather") ?: JSONObject()
        val widgetsObj = obj.optJSONObject("widgets") ?: JSONObject()
        val photoObj = obj.optJSONObject("photo") ?: JSONObject()

        val overridesObj = clockObj.optJSONObject("styleOverrides")
        val styleOverrides = if (overridesObj != null) {
            buildMap {
                for (key in overridesObj.keys()) {
                    val inner = overridesObj.optJSONObject(key) ?: continue
                    val map = buildMap {
                        for (k in inner.keys()) {
                            put(k, inner.optString(k, ""))
                        }
                    }
                    put(key.toIntOrNull() ?: continue, map)
                }
            }
        } else emptyMap()

        val photoUrisArr = photoObj.optJSONArray("photoUris")
        val photoUris = if (photoUrisArr != null) {
            buildList { for (i in 0 until photoUrisArr.length()) add(photoUrisArr.optString(i, "")) }
                .filter { it.isNotBlank() }
        } else emptyList()

        return DisplayConfig(
            layout = try { LayoutType.valueOf(obj.optString("layout", "CLOCK_SINGLE")) } catch (_: Exception) { LayoutType.CLOCK_SINGLE },
            fullScreen = obj.optBoolean("fullScreen", true),
            allowPortrait = obj.optBoolean("allowPortrait", false),
            currentPreset = obj.optString("currentPreset", "default"),
            backgroundPhotoUri = obj.optString("backgroundPhotoUri", "").ifBlank { null },
            showStatusBar = obj.optBoolean("showStatusBar", false),
            photo = PhotoConfig(
                photoUris = photoUris,
                transitionEffect = try { TransitionEffect.valueOf(photoObj.optString("transitionEffect", "FADE")) } catch (_: Exception) { TransitionEffect.FADE },
                intervalSecs = photoObj.optInt("intervalSecs", 10),
                useAsBackground = photoObj.optBoolean("useAsBackground", false)
            ),
            clock = ClockConfig(
                clockStyleIndex = clockObj.optInt("clockStyleIndex", 0),
                fontFamily = clockObj.optString("fontFamily", "default"),
                textColorHex = clockObj.optString("textColorHex", "#FFFFFF"),
                accentColorHex = clockObj.optString("accentColorHex", "#D0BCFF"),
                secondsColorHex = clockObj.optString("secondsColorHex", "#FF6B6B"),
                showSeconds = clockObj.optBoolean("showSeconds", true),
                showDate = clockObj.optBoolean("showDate", true),
                dateFormat = clockObj.optString("dateFormat", "EEEE, d MMMM"),
                backgroundColorHex = clockObj.optString("backgroundColorHex", "#000000"),
                backgroundOpacity = clockObj.optDouble("backgroundOpacity", 0.5).toFloat(),
                analogTickColorHex = clockObj.optString("analogTickColorHex", "#FFFFFF"),
                analogHandColorHex = clockObj.optString("analogHandColorHex", "#FFFFFF"),
                analogShowNumbers = clockObj.optBoolean("analogShowNumbers", true),
                autoNightBrightness = clockObj.optBoolean("autoNightBrightness", false),
                isDimmed = clockObj.optBoolean("isDimmed", false),
                nightBrightnessPercent = clockObj.optInt("nightBrightnessPercent", 30),
                timeFontSizeMultiplier = clockObj.optDouble("timeFontSizeMultiplier", 1.0).toFloat(),
                styleOverrides = styleOverrides
            ),
            weather = WeatherConfig(
                latitude = weatherObj.optString("latitude", "41.9028"),
                longitude = weatherObj.optString("longitude", "12.4964"),
                showForecast = weatherObj.optBoolean("showForecast", true),
                forecastDays = weatherObj.optInt("forecastDays", 3),
                unit = weatherObj.optString("unit", "celsius")
            ),
            widgets = WidgetConfig(
                showWeather = widgetsObj.optBoolean("showWeather", true),
                showDate = widgetsObj.optBoolean("showDate", true),
                showBattery = widgetsObj.optBoolean("showBattery", true),
                showMusic = widgetsObj.optBoolean("showMusic", false),
                showCalendar = widgetsObj.optBoolean("showCalendar", false),
                showTimer = widgetsObj.optBoolean("showTimer", false),
                showStopwatch = widgetsObj.optBoolean("showStopwatch", false),
                showCountdown = widgetsObj.optBoolean("showCountdown", false),
                showEWeLink = widgetsObj.optBoolean("showEWeLink", false),
                showPhotos = widgetsObj.optBoolean("showPhotos", false)
            )
        )
    }

    private fun serializePreset(p: Preset): JSONObject = JSONObject().apply {
        put("id", p.id)
        put("name", p.name)
        put("icon", p.icon)
        put("config", serializeConfig(p.config))
    }

    private fun parsePreset(obj: JSONObject): Preset = Preset(
        id = obj.getString("id"),
        name = obj.getString("name"),
        icon = obj.optString("icon", "clock"),
        config = parseConfig(obj.optJSONObject("config") ?: JSONObject())
    )
}
