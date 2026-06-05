package com.example.battery

import android.content.Context
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SonoffController(private val context: Context) {

    companion object {
        private const val TAG = "SonoffController"
        const val PREFS_NAME = "sonoff_prefs"
        const val KEY_ENABLED = "sonoff_enabled"
        const val KEY_DEVICE_ID = "sonoff_device_id"
        const val KEY_REGION = "sonoff_region"
        const val KEY_ACCESS_TOKEN = "sonoff_access_token"
        const val KEY_REFRESH_TOKEN = "sonoff_refresh_token"
        const val KEY_AT_EXPIRY = "sonoff_at_expiry"
        const val KEY_RT_EXPIRY = "sonoff_rt_expiry"
        const val KEY_ON_THRESHOLD = "sonoff_on_threshold"
        const val KEY_OFF_THRESHOLD = "sonoff_off_threshold"
        const val KEY_LAST_COMMAND = "sonoff_last_command"
        const val KEY_LAST_STATUS = "sonoff_last_status"
        const val KEY_DEVICE_LIST = "sonoff_device_list"

        private const val APP_ID = "lYPkZywzOtbxsMRNWJvhgCyXBDptIjOo"
        const val AUTH_SERVER_URL = "https://auth-server-hnlj.onrender.com"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val regionUrls = mapOf(
        "eu" to "https://eu-apia.coolkit.cc",
        "us" to "https://us-apia.coolkit.cc",
        "cn" to "https://cn-apia.coolkit.cc"
    )

    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)
    fun getDeviceId(): String = prefs.getString(KEY_DEVICE_ID, "") ?: ""
    fun getOnThreshold(): Int = prefs.getInt(KEY_ON_THRESHOLD, 30)
    fun getOffThreshold(): Int = prefs.getInt(KEY_OFF_THRESHOLD, 80)
    fun getLastCommand(): String = prefs.getString(KEY_LAST_COMMAND, "") ?: ""
    fun getLastStatus(): String = prefs.getString(KEY_LAST_STATUS, "In attesa") ?: "In attesa"

    private fun getBaseUrl(): String {
        val region = prefs.getString(KEY_REGION, "eu") ?: "eu"
        return regionUrls[region] ?: regionUrls["eu"]!!
    }

    private fun getAccessToken(): String = prefs.getString(KEY_ACCESS_TOKEN, "") ?: ""
    private fun getRefreshToken(): String = prefs.getString(KEY_REFRESH_TOKEN, "") ?: ""

    fun hasValidCredentials(): Boolean {
        val token = getAccessToken()
        val deviceId = getDeviceId()
        return token.isNotEmpty() && deviceId.isNotEmpty()
    }

    fun refreshTokenIfNeeded(): Boolean {
        val now = System.currentTimeMillis()
        val atExpiry = prefs.getLong(KEY_AT_EXPIRY, 0)
        val rtExpiry = prefs.getLong(KEY_RT_EXPIRY, 0)

        Log.d(TAG, "refreshTokenIfNeeded: now=$now, atExpiry=$atExpiry, rtExpiry=$rtExpiry, valid=${atExpiry > now}")

        if (atExpiry > now) {
            Log.d(TAG, "refreshTokenIfNeeded: AT ancora valido, skip refresh")
            return true
        }

        if (rtExpiry <= now) {
            Log.e(TAG, "refreshTokenIfNeeded: RT scaduto (rtExpiry=$rtExpiry <= now=$now), serve re-login")
            prefs.edit().putString(KEY_LAST_STATUS, "Token scaduto").apply()
            return false
        }

        Log.d(TAG, "refreshTokenIfNeeded: AT scaduto ma RT valido, refresh in corso...")
        val body = JSONObject().apply { put("rt", getRefreshToken()) }
        val result = apiPost("/v2/user/refresh", body, getAccessToken())

        if (result != null && result.optInt("error") == 0) {
            val data = result.getJSONObject("data")
            val newAt = data.getString("at")
            val newRt = data.getString("rt")
            prefs.edit()
                .putString(KEY_ACCESS_TOKEN, newAt)
                .putString(KEY_REFRESH_TOKEN, newRt)
                .putLong(KEY_AT_EXPIRY, now + 2592000000L)
                .putLong(KEY_RT_EXPIRY, now + 5184000000L)
                .putString(KEY_LAST_STATUS, "Token rinnovato")
                .apply()
            Log.d(TAG, "refreshTokenIfNeeded: token aggiornato con successo, newAt len=${newAt.length}")
            return true
        }

        val errCode = result?.optInt("error", -1) ?: -1
        val errMsg = result?.optString("msg", "") ?: ""
        Log.e(TAG, "refreshTokenIfNeeded: refresh fallito, error=$errCode msg=$errMsg")
        return false
    }

    fun turnOn(deviceId: String): Boolean {
        val lastCmd = prefs.getString(KEY_LAST_COMMAND, "") ?: ""
        Log.d(TAG, "turnOn called: deviceId=$deviceId, lastCommand=$lastCmd")
        prefs.edit().putString(KEY_LAST_STATUS, "Accensione...").apply()
        val ok = sendCommand(deviceId, "on")
        if (ok) {
            prefs.edit()
                .putString(KEY_LAST_COMMAND, "on")
                .putString(KEY_LAST_STATUS, "Ultimo comando: ON")
                .apply()
            Log.d(TAG, "turnOn completed: deviceId=$deviceId, lastCommand=off -> on")
        } else {
            prefs.edit().putString(KEY_LAST_STATUS, "Errore ON").apply()
            Log.e(TAG, "turnOn failed: deviceId=$deviceId, lastCommand rimane=$lastCmd")
        }
        return ok
    }

    fun turnOff(deviceId: String): Boolean {
        val lastCmd = prefs.getString(KEY_LAST_COMMAND, "") ?: ""
        Log.d(TAG, "turnOff called: deviceId=$deviceId, lastCommand=$lastCmd")
        prefs.edit().putString(KEY_LAST_STATUS, "Spegnimento...").apply()
        val ok = sendCommand(deviceId, "off")
        if (ok) {
            prefs.edit()
                .putString(KEY_LAST_COMMAND, "off")
                .putString(KEY_LAST_STATUS, "Ultimo comando: OFF")
                .apply()
            Log.d(TAG, "turnOff completed: deviceId=$deviceId, lastCommand=on -> off")
        } else {
            prefs.edit().putString(KEY_LAST_STATUS, "Errore OFF").apply()
            Log.e(TAG, "turnOff failed: deviceId=$deviceId, lastCommand rimane=$lastCmd")
        }
        return ok
    }

    private fun sendCommand(deviceId: String, cmd: String): Boolean {
        if (!refreshTokenIfNeeded()) {
            Log.e(TAG, "sendCommand: refreshTokenIfNeeded ha fallito, comando $cmd NON inviato")
            return false
        }

        try {
            val body = JSONObject().apply {
                put("type", 1)
                put("id", deviceId)
                put("params", JSONObject().apply { put("switch", cmd) })
            }
            val baseUrl = getBaseUrl()
            val url = baseUrl + "/v2/device/thing/status"
            Log.d(TAG, "sendCommand: POST $url body=$body")
            val result = apiPost("/v2/device/thing/status", body, getAccessToken())
            if (result != null && result.optInt("error") == 0) {
                Log.d(TAG, "sendCommand OK: cmd=$cmd deviceId=$deviceId response=$result")
                return true
            }
            val errCode = result?.optInt("error", -1) ?: -1
            val errMsg = result?.optString("msg", "") ?: ""
            Log.e(TAG, "sendCommand FALLITO: cmd=$cmd deviceId=$deviceId error=$errCode msg=$errMsg result=$result")
        } catch (e: Exception) {
            Log.e(TAG, "sendCommand ECCEZIONE: cmd=$cmd deviceId=$deviceId", e)
        }
        return false
    }

    data class SonoffDevice(
        val deviceId: String,
        val name: String,
        val uiid: String
    )

    fun listDevices(): List<SonoffDevice> {
        if (!refreshTokenIfNeeded()) return emptyList()

        try {
            val family = apiGet("/v2/family", getAccessToken())
            if (family != null && family.optInt("error") == 0) {
                val familyList = family.getJSONObject("data").getJSONArray("familyList")
                if (familyList.length() > 0) {
                    val familyId = familyList.getJSONObject(0).getString("id")
                    val devs = apiGet("/v2/device/thing?familyid=$familyId&num=0", getAccessToken())
                    if (devs != null && devs.optInt("error") == 0) {
                        val thingList = devs.getJSONObject("data").optJSONArray("thingList") ?: return emptyList()
                        val list = mutableListOf<SonoffDevice>()
                        for (i in 0 until thingList.length()) {
                            val item = thingList.getJSONObject(i).getJSONObject("itemData")
                            list.add(SonoffDevice(
                                deviceId = item.optString("deviceid", ""),
                                name = item.optString("name", "Senza nome"),
                                uiid = item.optJSONObject("extra")?.optString("uiid", "?") ?: "?"
                            ))
                        }
                        return list
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Errore lista dispositivi", e)
        }
        return emptyList()
    }

    private fun apiPost(path: String, body: JSONObject, token: String?): JSONObject? {
        val url = getBaseUrl() + path
        try {
            val requestBody = body.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(url).post(requestBody)
                .addHeader("Content-Type", "application/json")
                .addHeader("X-CK-Appid", APP_ID)
                .apply { if (token != null) addHeader("Authorization", "Bearer $token") }
                .build()
            Log.d(TAG, "apiPost: $url tokenLen=${token?.length ?: 0}")
            val response = client.newCall(request).execute()
            val bodyStr = response.body?.string()
            val code = response.code
            Log.d(TAG, "apiPost response: code=$code bodyLen=${bodyStr?.length ?: 0}")
            return if (bodyStr != null) JSONObject(bodyStr) else null
        } catch (e: Exception) {
            Log.e(TAG, "apiPost ECCEZIONE per $url", e)
            return null
        }
    }

    private fun apiGet(path: String, token: String): JSONObject? {
        val url = getBaseUrl() + path
        try {
            val request = Request.Builder().url(url).get()
                .addHeader("Content-Type", "application/json")
                .addHeader("X-CK-Appid", APP_ID)
                .addHeader("Authorization", "Bearer $token")
                .build()
            Log.d(TAG, "apiGet: $url tokenLen=${token.length}")
            val response = client.newCall(request).execute()
            val bodyStr = response.body?.string()
            val code = response.code
            Log.d(TAG, "apiGet response: code=$code bodyLen=${bodyStr?.length ?: 0}")
            return if (bodyStr != null) JSONObject(bodyStr) else null
        } catch (e: Exception) {
            Log.e(TAG, "apiGet ECCEZIONE per $url", e)
            return null
        }
    }
}
