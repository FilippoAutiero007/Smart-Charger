package com.example.playground

import android.content.Context
import android.util.Log
import com.example.battery.BatteryState
import com.example.battery.SonoffController
import java.util.Calendar
import java.util.Locale

object PlaygroundAutomation {
    private const val TAG = "PlaygroundAutomation"

    fun handleBatteryState(context: Context, state: BatteryState, source: String) {
        val appContext = context.applicationContext
        val projects = PlaygroundStore.loadProjects(appContext)
        val byId = projects.associateBy { it.id }
        val runningProjects = projects.filter { it.isRunning }
        if (runningProjects.isEmpty()) return

        var changed = false
        val updated = projects.map { project ->
            if (!project.isRunning) return@map project

            val result = evaluateProject(appContext, project, byId, state, source, mutableSetOf())
            val next = project.copy(
                lastRunAt = System.currentTimeMillis(),
                lastRunStatus = result.status
            )
            if (next.lastRunStatus != project.lastRunStatus || next.lastRunAt != project.lastRunAt) {
                changed = true
            }
            next
        }

        if (changed) {
            PlaygroundStore.saveProjects(appContext, updated)
        }
    }

    private data class EvaluationResult(
        val triggered: Boolean,
        val status: String
    )

    private fun evaluateProject(
        context: Context,
        project: PlaygroundProject,
        byId: Map<String, PlaygroundProject>,
        state: BatteryState,
        source: String,
        visited: MutableSet<String>
    ): EvaluationResult {
        if (!visited.add(project.id)) {
            return EvaluationResult(false, "Ricorsione bloccata")
        }

        val conditionResults = mutableListOf<Boolean>()

        project.nodes.filter { it.kind == "condition" }.forEach { node ->
            conditionResults += evaluateCondition(context, node, state)
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
            return EvaluationResult(false, "In attesa delle condizioni")
        }

        val executed = executeActions(context, project, byId, state, source, visited)
        val actionCount = executed.coerceAtLeast(0)
        val status = if (actionCount > 0) {
            "Eseguito: $actionCount azioni"
        } else {
            "Pronto: nessuna azione da eseguire"
        }
        Log.d(TAG, "Project ${project.name}: $status")
        return EvaluationResult(actionCount > 0, status)
    }

    private fun executeActions(
        context: Context,
        project: PlaygroundProject,
        byId: Map<String, PlaygroundProject>,
        state: BatteryState,
        source: String,
        visited: MutableSet<String>
    ): Int {
        var executed = 0
        val controller = SonoffController(context)
        val prefs = context.getSharedPreferences(SonoffController.PREFS_NAME, Context.MODE_PRIVATE)
        val lightIsOn = prefs.getString(SonoffController.KEY_LAST_COMMAND, "") == "on"

        project.nodes.filter { it.kind == "action" }.forEach { node ->
            when (node.config) {
                "light_on" -> {
                    node.deviceId?.let { if (controller.turnOn(it)) executed++ }
                }
                "light_off" -> {
                    node.deviceId?.let { if (controller.turnOff(it)) executed++ }
                }
                "ac_on", "heater_on" -> {
                    node.deviceId?.let { if (controller.turnOn(it)) executed++ }
                }
                "open_windows", "close_windows" -> {
                    Log.d(TAG, "Azione simulata ${node.config} per ${project.name} da $source")
                    executed++
                }
            }
        }

        project.nodes.filter { it.kind == "project" && !it.refProjectId.isNullOrBlank() }.forEach { node ->
            val child = byId[node.refProjectId]
            if (child != null) {
                val childResult = evaluateProject(context, child, byId, state, source, visited)
                if (childResult.triggered) {
                    executed++
                }
            }
        }

        if (project.nodes.any { it.kind == "condition" && it.config == "light_state" }) {
            Log.d(TAG, "light_state attuale=$lightIsOn")
        }

        return executed
    }

    private fun evaluateCondition(
        context: Context,
        node: PlaygroundNode,
        state: BatteryState
    ): Boolean {
        val config = node.config.orEmpty().lowercase(Locale.getDefault())
        val calendar = Calendar.getInstance()
        return when {
            config.startsWith("temp_gt_") -> {
                val threshold = config.removePrefix("temp_gt_").toFloatOrNull() ?: 25f
                state.temperature >= threshold
            }
            config.startsWith("temp_lt_") -> {
                val threshold = config.removePrefix("temp_lt_").toFloatOrNull() ?: 15f
                state.temperature <= threshold
            }
            config == "temperature" -> state.temperature >= 25f
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
            config == "light_state" -> {
                val prefs = context.getSharedPreferences(SonoffController.PREFS_NAME, Context.MODE_PRIVATE)
                prefs.getString(SonoffController.KEY_LAST_COMMAND, "") == "on"
            }
            config == "presence" -> state.percentage > 0
            else -> true
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
}
