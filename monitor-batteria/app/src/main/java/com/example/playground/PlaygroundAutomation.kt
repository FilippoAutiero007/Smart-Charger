package com.example.playground

import android.content.Context
import android.util.Log
import com.example.battery.SonoffController
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

object PlaygroundAutomation {
    private const val TAG = "PlaygroundAutomation"
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private data class EvaluationResult(
        val triggered: Boolean,
        val status: String,
        val variables: Map<String, String> = emptyMap()
    )

    fun evaluateProject(
        context: Context,
        project: PlaygroundProject,
        byId: Map<String, PlaygroundProject>,
        extraVariables: Map<String, Any>,
        visited: MutableSet<String>
    ): PlaygroundProject {
        if (!visited.add(project.id)) {
            return project.copy(lastRunStatus = "Ricorsione bloccata")
        }

        var variables = mutableMapOf<String, String>()
        extraVariables.forEach { (k, v) -> variables[k] = v.toString() }
        project.variables.forEach { (k, v) -> if (!variables.containsKey(k)) variables[k] = v }

        variables["now"] = System.currentTimeMillis().toString()
        val cal = Calendar.getInstance()
        variables["time_h"] = cal.get(Calendar.HOUR_OF_DAY).toString()
        variables["time_m"] = cal.get(Calendar.MINUTE).toString()

        executeHttpRequests(context, project, variables)
        executeDelays(project, variables)

        val conditionResults = mutableListOf<Boolean>()
        project.nodes.filter { it.kind == "condition" }.forEach { node ->
            conditionResults += evaluateCondition(context, node, variables)
        }

        val hasOr = project.nodes.any { it.kind == "logic" && it.label.equals("OR", ignoreCase = true) }
        val hasNot = project.nodes.any { it.kind == "logic" && it.label.equals("NOT", ignoreCase = true) }
        val conditionsMet = when {
            conditionResults.isEmpty() -> true
            hasOr -> conditionResults.any { it }
            else -> conditionResults.all { it }
        }
        val finalCondition = if (hasNot) !conditionsMet else conditionsMet

        if (!finalCondition) {
            return project.copy(
                lastRunAt = System.currentTimeMillis(),
                lastRunStatus = "In attesa delle condizioni",
                variables = variables
            )
        }

        val executed = executeActions(context, project, byId, extraVariables, visited)
        val status = if (executed > 0) {
            "Eseguito: $executed azioni"
        } else {
            "Pronto: nessuna azione da eseguire"
        }

        return project.copy(
            lastRunAt = System.currentTimeMillis(),
            lastRunStatus = status,
            variables = variables
        )
    }

    private fun executeHttpRequests(context: Context, project: PlaygroundProject, variables: MutableMap<String, String>) {
        project.nodes.filter { it.kind == "http_request" }.forEach { node ->
            try {
                val config = node.configJson?.let { JSONObject(it) } ?: return@forEach
                val url = config.optString("url", "") ?: ""
                if (url.isBlank()) return@forEach
                val method = config.optString("method", "GET").uppercase(Locale.US)
                val jsonPath = config.optString("jsonPath", "")
                val outputVar = config.optString("outputVar", "")

                val resolvedUrl = resolveVariables(url, variables)

                val request = Request.Builder().url(resolvedUrl)
                if (method == "POST" || method == "PUT") {
                    val body = config.optString("body", "")
                    val resolvedBody = resolveVariables(body, variables)
                    request.method(method, resolvedBody.toRequestBody("application/json".toMediaType()))
                }
                val response = httpClient.newCall(request.build()).execute()
                val body = response.body?.string() ?: ""

                if (jsonPath.isNotBlank()) {
                    val extracted = jsonPathQuery(body, jsonPath)
                    if (outputVar.isNotBlank()) {
                        variables[outputVar] = extracted
                    }
                } else if (outputVar.isNotBlank()) {
                    variables[outputVar] = body
                }
            } catch (e: Exception) {
                Log.e(TAG, "HTTP request failed for ${node.label}", e)
            }
        }
    }

    private fun executeDelays(project: PlaygroundProject, variables: MutableMap<String, String>) {
        val delayNodes = project.nodes.filter { it.kind == "delay" }
        if (delayNodes.isEmpty()) return
        val firstDelay = delayNodes.first()
        val config = firstDelay.configJson?.let { JSONObject(it) }
        val minutes = config?.optInt("minutes", 5) ?: 5
        variables["delay_remaining"] = minutes.toString()
    }

    private fun evaluateCondition(context: Context, node: PlaygroundNode, variables: Map<String, String>): Boolean {
        val config = node.config.orEmpty().lowercase(Locale.getDefault())
        val configJson = node.configJson?.let { try { JSONObject(it) } catch (_: Exception) { null } }

        if (configJson != null) {
            val left = resolveVariables(configJson.optString("left", ""), variables)
            val op = configJson.optString("op", "==")
            val right = resolveVariables(configJson.optString("right", ""), variables)
            return evaluateOperator(left, op, right)
        }

        val calendar = Calendar.getInstance()
        return when {
            config.startsWith("temp_gt_") -> {
                val threshold = config.removePrefix("temp_gt_").toFloatOrNull() ?: 25f
                val temp = variables["battery_temp"]?.toFloatOrNull() ?: return true
                temp >= threshold
            }
            config.startsWith("temp_lt_") -> {
                val threshold = config.removePrefix("temp_lt_").toFloatOrNull() ?: 15f
                val temp = variables["battery_temp"]?.toFloatOrNull() ?: return true
                temp <= threshold
            }
            config == "temperature" -> {
                val temp = variables["battery_temp"]?.toFloatOrNull() ?: return true
                temp >= 25f
            }
            config == "season" -> {
                val month = calendar.get(Calendar.MONTH)
                month in Calendar.JUNE..Calendar.AUGUST
            }
            config.startsWith("season=") -> {
                val month = calendar.get(Calendar.MONTH)
                when (config.substringAfter('=')) {
                    "summer" -> month in Calendar.JUNE..Calendar.AUGUST
                    "winter" -> month == Calendar.DECEMBER || month in Calendar.JANUARY..Calendar.FEBRUARY
                    "spring" -> month in Calendar.MARCH..Calendar.MAY
                    "autumn", "fall" -> month in Calendar.SEPTEMBER..Calendar.NOVEMBER
                    else -> true
                }
            }
            config == "time" -> true
            config.startsWith("time=") -> evaluateTimeWindow(config.substringAfter('='))
            config == "device_state" -> {
                val prefs = context.getSharedPreferences(SonoffController.PREFS_NAME, Context.MODE_PRIVATE)
                prefs.getString(SonoffController.KEY_LAST_COMMAND, "") == "on"
            }
            config == "presence" -> {
                val pct = variables["battery_percentage"]?.toIntOrNull() ?: 0
                pct > 0
            }
            config == "battery_low" -> {
                val pct = variables["battery_percentage"]?.toIntOrNull() ?: return true
                pct <= 20
            }
            config == "battery_charging" -> {
                variables["battery_charging"]?.toBoolean() ?: true
            }
            config.startsWith("battery<=") -> {
                val threshold = config.removePrefix("battery<=").toIntOrNull() ?: 20
                val pct = variables["battery_percentage"]?.toIntOrNull() ?: return true
                pct <= threshold
            }
            config.startsWith("battery>=") -> {
                val threshold = config.removePrefix("battery>=").toIntOrNull() ?: 80
                val pct = variables["battery_percentage"]?.toIntOrNull() ?: return true
                pct >= threshold
            }
            else -> true
        }
    }

    private fun evaluateOperator(left: String, op: String, right: String): Boolean {
        val l = left.trim()
        val r = right.trim()
        return when (op) {
            "==" -> l == r
            "!=" -> l != r
            ">" -> (l.toFloatOrNull() ?: return l > r) > (r.toFloatOrNull() ?: return false)
            "<" -> (l.toFloatOrNull() ?: return l < r) < (r.toFloatOrNull() ?: return false)
            ">=" -> (l.toFloatOrNull() ?: return l >= r) >= (r.toFloatOrNull() ?: return false)
            "<=" -> (l.toFloatOrNull() ?: return l <= r) <= (r.toFloatOrNull() ?: return false)
            "contains" -> l.contains(r, ignoreCase = true)
            "matches" -> l.matches(r.toRegex())
            else -> l == r
        }
    }

    private fun executeActions(
        context: Context,
        project: PlaygroundProject,
        byId: Map<String, PlaygroundProject>,
        extraVariables: Map<String, Any>,
        visited: MutableSet<String>
    ): Int {
        var executed = 0
        val controller = SonoffController(context)
        val prefs = context.getSharedPreferences(SonoffController.PREFS_NAME, Context.MODE_PRIVATE)

        project.nodes.filter { it.kind == "action" }.forEach { node ->
            when (node.config) {
                "light_on", "ac_on", "heater_on" -> {
                    node.deviceId?.let { if (controller.turnOn(it)) executed++ }
                }
                "light_off" -> {
                    node.deviceId?.let { if (controller.turnOff(it)) executed++ }
                }
                "open_windows", "close_windows" -> {
                    Log.d(TAG, "Azione simulata ${node.config} per ${project.name}")
                    executed++
                }
            }
            if (node.configJson != null) {
                try {
                    val cfg = JSONObject(node.configJson)
                    if (cfg.optString("type", "") == "webhook") {
                        if (executeWebhook(cfg)) executed++
                    }
                } catch (_: Exception) {}
            }
        }

        project.nodes.filter { it.kind == "action_webhook" }.forEach { node ->
            val cfg = node.configJson?.let { try { JSONObject(it) } catch (_: Exception) { null } } ?: return@forEach
            if (executeWebhook(cfg)) executed++
        }

        project.nodes.filter { it.kind == "project" && !it.refProjectId.isNullOrBlank() }.forEach { node ->
            val child = byId[node.refProjectId]
            if (child != null) {
                val result = evaluateProject(context, child, byId, extraVariables, visited)
                if (result.lastRunStatus?.startsWith("Eseguito") == true) {
                    executed++
                }
            }
        }

        return executed
    }

    private fun executeWebhook(cfg: JSONObject): Boolean {
        return try {
            val url = cfg.optString("url", "") ?: ""
            if (url.isBlank()) return false
            val method = cfg.optString("method", "POST").uppercase(Locale.US)
            val body = cfg.optString("body", "")

            val request = Request.Builder().url(url)
            if (method == "POST" || method == "PUT") {
                request.method(method, body.toRequestBody("application/json".toMediaType()))
            } else {
                request.method(method, null)
            }
            val response = httpClient.newCall(request.build()).execute()
            Log.d(TAG, "Webhook $method $url -> ${response.code}")
            response.isSuccessful
        } catch (e: Exception) {
            Log.e(TAG, "Webhook failed", e)
            false
        }
    }

    private fun evaluateTimeWindow(range: String): Boolean {
        val parts = range.split("-")
        if (parts.size != 2) return true
        val start = parseMinutes(parts[0]) ?: return true
        val end = parseMinutes(parts[1]) ?: return true
        val now = Calendar.getInstance()
        val current = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        return current in start..end
    }

    private fun parseMinutes(raw: String): Int? {
        val segments = raw.trim().split(":")
        if (segments.size != 2) return null
        val hour = segments[0].toIntOrNull() ?: return null
        val minute = segments[1].toIntOrNull() ?: return null
        return hour * 60 + minute
    }

    fun jsonPathQuery(json: String, path: String): String {
        if (json.isBlank()) return ""
        return try {
            val trimmed = path.trim().removePrefix("$.")
            val parts = trimmed.split(".")
            var current: Any = if (json.trimStart().startsWith("[")) JSONArray(json) else JSONObject(json)
            for (part in parts) {
                when {
                    part.contains("[") -> {
                        val name = part.substringBefore("[")
                        val idx = part.substringAfter("[").substringBefore("]").toIntOrNull() ?: 0
                        current = if (name.isNotBlank()) {
                            (current as? JSONObject)?.opt(name) as? JSONArray
                        } else {
                            current as? JSONArray
                        }?.opt(idx) ?: ""
                    }
                    part == "*" -> {
                        val arr = current as? JSONArray ?: return ""
                        current = arr.opt(0) ?: ""
                    }
                    else -> {
                        current = (current as? JSONObject)?.opt(part) ?: ""
                    }
                }
            }
            current.toString()
        } catch (_: Exception) {
            ""
        }
    }

    fun resolveVariables(template: String, variables: Map<String, String>): String {
        var result = template
        variables.forEach { (key, value) ->
            result = result.replace("\${$key}", value)
            result = result.replace("{$key}", value)
        }
        return result
    }
}
