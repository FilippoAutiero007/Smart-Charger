package com.example.playground

import android.content.Context
import android.util.Log
import java.util.Calendar

object PlaygroundEngine {
    private const val TAG = "PlaygroundEngine"

    fun evaluateAll(
        context: Context,
        extraVariables: Map<String, Any> = emptyMap()
    ) {
        val appContext = context.applicationContext
        val projects = PlaygroundStore.loadProjects(appContext)
        val byId = projects.associateBy { it.id }
        val runningProjects = projects.filter { it.isRunning }
        if (runningProjects.isEmpty()) return

        var changed = false
        val updated = projects.map { project ->
            if (!project.isRunning) return@map project
            val result = PlaygroundAutomation.evaluateProject(
                appContext, project, byId, extraVariables, mutableSetOf()
            )
            if (result != project) changed = true
            result
        }

        if (changed) {
            PlaygroundStore.saveProjects(appContext, updated)
            Log.d(TAG, "Saved ${updated.size} projects, ${runningProjects.size} running")
        }
    }

    fun evaluateByTrigger(
        context: Context,
        triggerType: String,
        extraVariables: Map<String, Any> = emptyMap()
    ) {
        val appContext = context.applicationContext
        val projects = PlaygroundStore.loadProjects(appContext)
        val byId = projects.associateBy { it.id }
        val matching = projects.filter { it.isRunning && it.triggerType == triggerType }
        if (matching.isEmpty()) return

        var changed = false
        val updated = projects.map { project ->
            if (!project.isRunning || project.triggerType != triggerType) return@map project
            val result = PlaygroundAutomation.evaluateProject(
                appContext, project, byId, extraVariables, mutableSetOf()
            )
            if (result != project) changed = true
            result
        }

        if (changed) {
            PlaygroundStore.saveProjects(appContext, updated)
        }
    }

    fun evaluateTimerProjects(context: Context) {
        val appContext = context.applicationContext
        val projects = PlaygroundStore.loadProjects(appContext)
        val byId = projects.associateBy { it.id }
        val cal = Calendar.getInstance()
        val nowMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

        val timerProjects = projects.filter { it.isRunning && it.triggerType == "timer" }
        val timeProjects = projects.filter { it.isRunning && it.triggerType == "orario" }

        val toEvaluate = mutableSetOf<PlaygroundProject>()
        toEvaluate.addAll(timerProjects)
        timeProjects.forEach { p ->
            val parts = (p.triggerConfig ?: "").split(":")
            if (parts.size == 2) {
                val h = parts[0].toIntOrNull()
                val m = parts[1].toIntOrNull()
                if (h != null && m != null && h * 60 + m == nowMinutes) {
                    toEvaluate.add(p)
                }
            }
        }

        if (toEvaluate.isEmpty()) return
        var changed = false
        val updated = projects.map { project ->
            if (project !in toEvaluate) return@map project
            val result = PlaygroundAutomation.evaluateProject(
                appContext, project, byId, emptyMap(), mutableSetOf()
            )
            if (result != project) changed = true
            result
        }

        if (changed) {
            PlaygroundStore.saveProjects(appContext, updated)
        }
    }
}
