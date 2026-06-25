package com.example.playground

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class PlaygroundConnection(
    val fromNodeId: String,
    val toNodeId: String
)

data class PlaygroundNode(
    val id: String,
    val kind: String,
    val label: String,
    val x: Float,
    val y: Float,
    val deviceId: String? = null,
    val config: String? = null
)

data class PlaygroundProject(
    val id: String,
    val name: String,
    val nodes: List<PlaygroundNode> = emptyList(),
    val connections: List<PlaygroundConnection> = emptyList()
)

object PlaygroundStore {
    private const val PREFS_NAME = "playground_store"
    private const val KEY_PROJECTS = "projects_json"
    private const val KEY_SELECTED = "selected_project_id"

    fun loadProjects(context: Context): List<PlaygroundProject> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_PROJECTS, null).orEmpty()
        if (raw.isBlank()) {
            return listOf(createProject("Progetto 1"))
        }

        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    add(parseProject(arr.getJSONObject(i)))
                }
            }.ifEmpty { listOf(createProject("Progetto 1")) }
        } catch (_: Exception) {
            listOf(createProject("Progetto 1"))
        }
    }

    fun saveProjects(context: Context, projects: List<PlaygroundProject>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = JSONArray()
        projects.forEach { arr.put(serializeProject(it)) }
        prefs.edit().putString(KEY_PROJECTS, arr.toString()).apply()
    }

    fun loadSelectedProjectId(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_SELECTED, null)
    }

    fun saveSelectedProjectId(context: Context, projectId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SELECTED, projectId).apply()
    }

    fun createProject(name: String): PlaygroundProject {
        return PlaygroundProject(
            id = newId("project"),
            name = name
        )
    }

    fun newId(prefix: String): String = "${prefix}_${UUID.randomUUID().toString().take(8)}"

    private fun parseProject(obj: JSONObject): PlaygroundProject {
        val nodes = buildList {
            val nodeArr = obj.optJSONArray("nodes") ?: JSONArray()
            for (i in 0 until nodeArr.length()) {
                val n = nodeArr.getJSONObject(i)
                val deviceId = n.optString("deviceId", "").ifBlank { null }
                val config = n.optString("config", "").ifBlank { null }
                add(
                    PlaygroundNode(
                        id = n.getString("id"),
                        kind = n.getString("kind"),
                        label = n.getString("label"),
                        x = n.getDouble("x").toFloat(),
                        y = n.getDouble("y").toFloat(),
                        deviceId = deviceId,
                        config = config
                    )
                )
            }
        }

        val connections = buildList {
            val connArr = obj.optJSONArray("connections") ?: JSONArray()
            for (i in 0 until connArr.length()) {
                val c = connArr.getJSONObject(i)
                add(
                    PlaygroundConnection(
                        fromNodeId = c.getString("fromNodeId"),
                        toNodeId = c.getString("toNodeId")
                    )
                )
            }
        }

        return PlaygroundProject(
            id = obj.getString("id"),
            name = obj.getString("name"),
            nodes = nodes,
            connections = connections
        )
    }

    private fun serializeProject(project: PlaygroundProject): JSONObject {
        val nodes = JSONArray()
        project.nodes.forEach { node ->
            nodes.put(
                JSONObject().apply {
                    put("id", node.id)
                    put("kind", node.kind)
                    put("label", node.label)
                    put("x", node.x)
                    put("y", node.y)
                    node.deviceId?.let { put("deviceId", it) }
                    node.config?.let { put("config", it) }
                }
            )
        }

        val connections = JSONArray()
        project.connections.forEach { conn ->
            connections.put(
                JSONObject().apply {
                    put("fromNodeId", conn.fromNodeId)
                    put("toNodeId", conn.toNodeId)
                }
            )
        }

        return JSONObject().apply {
            put("id", project.id)
            put("name", project.name)
            put("nodes", nodes)
            put("connections", connections)
        }
    }
}
