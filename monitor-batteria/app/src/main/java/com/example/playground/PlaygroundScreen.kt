package com.example.playground

import android.content.Context
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import com.example.battery.SonoffController
import com.example.ui.theme.ElegantPurple
import com.example.ui.theme.GreenHealthy
import com.example.ui.theme.OutlineDark
import com.example.ui.theme.RedAlert
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import java.util.Locale
import kotlin.math.roundToInt

private const val NODE_W = 176f
private const val NODE_H = 96f

private data class PaletteItem(
    val kind: String,
    val title: String,
    val subtitle: String,
    val deviceId: String? = null,
    val config: String? = null
)

private data class DraggingItem(
    val item: PaletteItem,
    val globalPosition: Offset
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaygroundScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val sonoffPrefs = remember { context.getSharedPreferences(SonoffController.PREFS_NAME, Context.MODE_PRIVATE) }
    val isLoggedIn = remember {
        sonoffPrefs.getString(SonoffController.KEY_ACCESS_TOKEN, "").orEmpty().isNotBlank()
    }

    var projects by remember { mutableStateOf(PlaygroundStore.loadProjects(context)) }
    var selectedProjectId by remember {
        mutableStateOf(
            PlaygroundStore.loadSelectedProjectId(context) ?: projects.firstOrNull()?.id.orEmpty()
        )
    }
    var renameTarget by remember { mutableStateOf<PlaygroundProject?>(null) }
    var renameValue by remember { mutableStateOf("") }
    var showNewProjectDialog by remember { mutableStateOf(false) }
    var newProjectName by remember { mutableStateOf("") }
    var pendingConnectionFrom by remember { mutableStateOf<String?>(null) }
    var rootBounds by remember { mutableStateOf(Rect.Zero) }
    var canvasBounds by remember { mutableStateOf(Rect.Zero) }
    var dragging by remember { mutableStateOf<DraggingItem?>(null) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }

    val availableDevices = remember {
        val raw = sonoffPrefs.getString(SonoffController.KEY_DEVICE_LIST, "") ?: ""
        if (raw.isBlank()) {
            emptyList<Pair<String, String>>()
        } else {
            runCatching {
                val arr = org.json.JSONArray(raw)
                buildList {
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val id = obj.optString("deviceid", "")
                        val name = obj.optString("name", "Senza nome")
                        if (id.isNotBlank()) add(name to id)
                    }
                }
            }.getOrElse { emptyList() }
        }
    }

    val currentProject = projects.firstOrNull { it.id == selectedProjectId } ?: projects.first()

    LaunchedEffect(projects, selectedProjectId) {
        PlaygroundStore.saveProjects(context, projects)
        PlaygroundStore.saveSelectedProjectId(context, selectedProjectId)
    }

    fun updateCurrentProject(transform: (PlaygroundProject) -> PlaygroundProject) {
        projects = projects.map { if (it.id == currentProject.id) transform(it) else it }
    }

    fun addProject(name: String) {
        val project = PlaygroundStore.createProject(name)
        projects = projects + project
        selectedProjectId = project.id
    }

    fun addNodeAt(item: PaletteItem, localX: Float, localY: Float) {
        val node = PlaygroundNode(
            id = PlaygroundStore.newId("node"),
            kind = item.kind,
            label = item.title,
            x = localX.coerceIn(16f, (canvasSize.width - NODE_W - 16f).coerceAtLeast(16f)),
            y = localY.coerceIn(16f, (canvasSize.height - NODE_H - 16f).coerceAtLeast(16f)),
            deviceId = item.deviceId,
            config = item.config
        )
        updateCurrentProject { it.copy(nodes = it.nodes + node) }
    }

    fun updateNodePosition(nodeId: String, newX: Float, newY: Float) {
        updateCurrentProject { project ->
            project.copy(
                nodes = project.nodes.map { node ->
                    if (node.id == nodeId) {
                        node.copy(
                            x = newX.coerceIn(8f, (canvasSize.width - NODE_W - 8f).coerceAtLeast(8f)),
                            y = newY.coerceIn(8f, (canvasSize.height - NODE_H - 8f).coerceAtLeast(8f))
                        )
                    } else node
                }
            )
        }
    }

    fun connectNodes(fromId: String, toId: String) {
        if (fromId == toId) return
        updateCurrentProject { project ->
            val connection = PlaygroundConnection(fromNodeId = fromId, toNodeId = toId)
            val exists = project.connections.any { it.fromNodeId == fromId && it.toNodeId == toId }
            if (exists) project else project.copy(connections = project.connections + connection)
        }
        pendingConnectionFrom = null
    }

    fun templateNodes(kind: String): Pair<List<PlaygroundNode>, List<PlaygroundConnection>> {
        val nodes = when (kind) {
            "estate-luce" -> listOf(
                PlaygroundNode(PlaygroundStore.newId("node"), "device", "Luce cucina", 48f, 96f, config = "light"),
                PlaygroundNode(PlaygroundStore.newId("node"), "condition", "Stagione: estate", 260f, 54f, config = "season=summer"),
                PlaygroundNode(PlaygroundStore.newId("node"), "condition", "Fascia: pomeriggio", 260f, 180f, config = "time=14:00-18:00"),
                PlaygroundNode(PlaygroundStore.newId("node"), "logic", "AND", 470f, 100f, config = "and"),
                PlaygroundNode(PlaygroundStore.newId("node"), "action", "Apri finestre", 690f, 46f, config = "open_windows"),
                PlaygroundNode(PlaygroundStore.newId("node"), "action", "Spegni luce", 690f, 172f, config = "turn_off_light")
            )
            "caldo" -> listOf(
                PlaygroundNode(PlaygroundStore.newId("node"), "condition", "Temperatura > 25°C", 70f, 100f, config = "temp_gt_25"),
                PlaygroundNode(PlaygroundStore.newId("node"), "action", "Accendi condizionatori", 340f, 100f, config = "ac_on")
            )
            else -> listOf(
                PlaygroundNode(PlaygroundStore.newId("node"), "condition", "Temperatura < 15°C", 70f, 100f, config = "temp_lt_15"),
                PlaygroundNode(PlaygroundStore.newId("node"), "action", "Accendi termosifone", 340f, 100f, config = "heater_on")
            )
        }

        val connections = when (kind) {
            "estate-luce" -> listOf(
                PlaygroundConnection(nodes[0].id, nodes[3].id),
                PlaygroundConnection(nodes[1].id, nodes[3].id),
                PlaygroundConnection(nodes[2].id, nodes[3].id),
                PlaygroundConnection(nodes[3].id, nodes[4].id),
                PlaygroundConnection(nodes[3].id, nodes[5].id)
            )
            else -> listOf(PlaygroundConnection(nodes[0].id, nodes[1].id))
        }
        return nodes to connections
    }

    fun applyTemplate(kind: String) {
        val (nodes, connections) = templateNodes(kind)
        updateCurrentProject { it.copy(nodes = nodes, connections = connections) }
    }

    val paletteItems = buildList {
        if (isLoggedIn) {
            availableDevices.take(6).forEach { (name, id) ->
                add(PaletteItem("device", name, "Dispositivo eWeLink", deviceId = id))
            }
        }
        add(PaletteItem("condition", "Ora", "Fascia oraria", config = "time"))
        add(PaletteItem("condition", "Stagione", "Estate / inverno", config = "season"))
        add(PaletteItem("condition", "Temperatura", "Sensore clima", config = "temperature"))
        add(PaletteItem("logic", "AND", "Tutte le condizioni", config = "and"))
        add(PaletteItem("logic", "OR", "Una condizione basta", config = "or"))
        add(PaletteItem("action", "Azione", "Comando finale", config = "action"))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(com.example.ui.theme.BackgroundDark)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
                    IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro", tint = TextPrimary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Playground",
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = if (isLoggedIn) "Login eWeLink attivo" else "Effettua il login eWeLink per sbloccare i dispositivi",
                    color = if (isLoggedIn) GreenHealthy else TextTertiary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.CardDark),
            border = androidx.compose.foundation.BorderStroke(1.dp, OutlineDark.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Progetti", fontWeight = FontWeight.Bold, color = TextPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = { showNewProjectDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Nuovo progetto", tint = ElegantPurple)
                        }
                        IconButton(
                            onClick = {
                                renameTarget = currentProject
                                renameValue = currentProject.name
                            },
                            enabled = projects.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Rinomina progetto", tint = ElegantPurple)
                        }
                    }
                }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(projects, key = { it.id }) { project ->
                        val selected = project.id == selectedProjectId
                        ChipProject(
                            title = project.name,
                            selected = selected,
                            onClick = { selectedProjectId = project.id }
                        )
                    }
                }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        OutlinedButton(onClick = { applyTemplate("estate-luce") }) {
                            Text("Esempio estate")
                        }
                    }
                    item {
                        OutlinedButton(onClick = { applyTemplate("caldo") }) {
                            Text("Esempio caldo")
                        }
                    }
                    item {
                        OutlinedButton(onClick = { applyTemplate("freddo") }) {
                            Text("Esempio freddo")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .onGloballyPositioned {
                    rootBounds = it.boundsInWindow()
                }
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned {
                        canvasBounds = it.boundsInWindow()
                        canvasSize = Size(it.size.width.toFloat(), it.size.height.toFloat())
                    }
                    .background(com.example.ui.theme.CardDark.copy(alpha = 0.45f))
                    .border(1.dp, OutlineDark.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                    .padding(10.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    currentProject.connections.forEach { connection ->
                        val from = currentProject.nodes.firstOrNull { it.id == connection.fromNodeId } ?: return@forEach
                        val to = currentProject.nodes.firstOrNull { it.id == connection.toNodeId } ?: return@forEach
                        val start = Offset(from.x + NODE_W, from.y + NODE_H / 2f)
                        val end = Offset(to.x, to.y + NODE_H / 2f)
                        val path = Path().apply {
                            moveTo(start.x, start.y)
                            cubicTo(
                                start.x + 80f, start.y,
                                end.x - 80f, end.y,
                                end.x, end.y
                            )
                        }
                        drawPath(path, color = ElegantPurple.copy(alpha = 0.85f), style = Stroke(width = 5f, cap = StrokeCap.Round))
                    }
                }

                if (currentProject.nodes.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Trascina dispositivi o blocchi qui dentro",
                            color = TextTertiary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                currentProject.nodes.forEach { node ->
                    val color = when (node.kind) {
                        "device" -> ElegantPurple
                        "action" -> GreenHealthy
                        "logic" -> Color(0xFF4C8FFF)
                        else -> Color(0xFFF5A524)
                    }

                    val isConnectionSource = pendingConnectionFrom == node.id
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(node.x.roundToInt(), node.y.roundToInt()) }
                            .width(176.dp)
                            .height(96.dp)
                            .background(com.example.ui.theme.CardDark, RoundedCornerShape(14.dp))
                            .border(
                                width = if (isConnectionSource) 2.dp else 1.dp,
                                color = if (isConnectionSource) ElegantPurple else OutlineDark.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable {
                                if (pendingConnectionFrom == null) {
                                    pendingConnectionFrom = node.id
                                }
                            }
                            .pointerInput(node.id) {
                                detectDragGesturesAfterLongPress(
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        updateNodePosition(node.id, node.x + dragAmount.x, node.y + dragAmount.y)
                                    }
                                )
                            }
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(color, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(node.label, color = TextPrimary, fontWeight = FontWeight.Bold, maxLines = 1)
                                    Text(
                                        text = node.kind.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },
                                        color = TextTertiary,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }

                            Text(
                                text = when {
                                    node.deviceId != null -> "device: ${node.deviceId.take(10)}..."
                                    !node.config.isNullOrBlank() -> node.config
                                    else -> "configurabile"
                                },
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .offset(x = (-8).dp)
                                .size(16.dp)
                                .background(ElegantPurple, CircleShape)
                                .clickable {
                                    pendingConnectionFrom?.let { connectNodes(it, node.id) }
                                }
                        )

                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .offset(x = 8.dp)
                                .size(16.dp)
                                .background(color, CircleShape)
                                .clickable {
                                    pendingConnectionFrom = node.id
                                }
                        )
                    }
                }

                dragging?.let { item ->
                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    (item.globalPosition.x - rootBounds.left - 88f).roundToInt(),
                                    (item.globalPosition.y - rootBounds.top - 24f).roundToInt()
                                )
                            }
                            .background(com.example.ui.theme.CardDark, RoundedCornerShape(12.dp))
                            .border(1.dp, ElegantPurple, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(item.item.title, color = TextPrimary, fontWeight = FontWeight.Bold)
                    }
                }

                if (!isLoggedIn) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.CardDark),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Login richiesto", color = TextPrimary, fontWeight = FontWeight.Bold)
                                Text(
                                    "Accedi con eWeLink per vedere e trascinare i dispositivi nel Playground.",
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.CardDark),
            border = androidx.compose.foundation.BorderStroke(1.dp, OutlineDark.copy(alpha = 0.35f)),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Barra dispositivi", fontWeight = FontWeight.Bold, color = TextPrimary)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(paletteItems, key = { "${it.kind}:${it.title}:${it.deviceId ?: it.config ?: ""}" }) { item ->
                        PaletteChip(
                            item = item,
                            enabled = isLoggedIn || item.kind != "device",
                            canvasBounds = canvasBounds,
                            rootBounds = rootBounds,
                            onDrop = { dropped ->
                                val local = Offset(
                                    dropped.x - canvasBounds.left - NODE_W / 2f,
                                    dropped.y - canvasBounds.top - NODE_H / 2f
                                )
                                addNodeAt(item, local.x, local.y)
                            },
                            onDragPreview = { position ->
                                dragging = DraggingItem(item, position)
                            },
                            onDragPreviewMove = { position ->
                                dragging = dragging?.copy(globalPosition = position)
                            },
                            onDragPreviewEnd = {
                                dragging = null
                            }
                        )
                    }
                }
            }
        }
    }

    if (showNewProjectDialog) {
        AlertDialog(
            onDismissRequest = { showNewProjectDialog = false },
            title = { Text("Nuovo progetto") },
            text = {
                OutlinedTextField(
                    value = newProjectName,
                    onValueChange = { newProjectName = it },
                    label = { Text("Nome progetto") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = newProjectName.trim().ifBlank { "Progetto ${projects.size + 1}" }
                    addProject(name)
                    newProjectName = ""
                    showNewProjectDialog = false
                }) {
                    Text("Crea")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewProjectDialog = false }) { Text("Annulla") }
            }
        )
    }

    if (renameTarget != null) {
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rinomina progetto") },
            text = {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    label = { Text("Nome progetto") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = renameTarget ?: return@TextButton
                    val trimmed = renameValue.trim().ifBlank { target.name }
                    projects = projects.map { if (it.id == target.id) it.copy(name = trimmed) else it }
                    selectedProjectId = target.id
                    renameTarget = null
                }) {
                    Text("Salva")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Annulla") }
            }
        )
    }
}

@Composable
private fun ChipProject(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (selected) ElegantPurple.copy(alpha = 0.2f) else com.example.ui.theme.BackgroundDark
    val border = if (selected) ElegantPurple else OutlineDark.copy(alpha = 0.35f)
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(999.dp))
            .border(1.dp, border, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = title,
            color = if (selected) ElegantPurple else TextSecondary,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
private fun PaletteChip(
    item: PaletteItem,
    enabled: Boolean,
    canvasBounds: Rect,
    rootBounds: Rect,
    onDrop: (Offset) -> Unit,
    onDragPreview: (Offset) -> Unit,
    onDragPreviewMove: (Offset) -> Unit,
    onDragPreviewEnd: () -> Unit
) {
    var itemBounds by remember { mutableStateOf(Rect.Zero) }
    var dragPosition by remember { mutableStateOf<Offset?>(null) }
    val bg = when (item.kind) {
        "device" -> ElegantPurple.copy(alpha = 0.18f)
        "action" -> GreenHealthy.copy(alpha = 0.16f)
        "logic" -> Color(0xFF4C8FFF).copy(alpha = 0.16f)
        else -> Color(0xFFF5A524).copy(alpha = 0.16f)
    }

    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(12.dp))
            .border(1.dp, if (enabled) OutlineDark.copy(alpha = 0.35f) else OutlineDark.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .onGloballyPositioned { itemBounds = it.boundsInWindow() }
            .then(
                if (enabled) {
                    Modifier.pointerInput(itemBounds, canvasBounds, rootBounds) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { localStart ->
                                val start = itemBounds.topLeft + localStart
                                dragPosition = start
                                onDragPreview(start)
                            },
                            onDrag = { change, dragAmount ->
                                val current = (dragPosition ?: itemBounds.topLeft) + dragAmount
                                dragPosition = current
                                onDragPreviewMove(current)
                            },
                            onDragEnd = {
                                val dropPosition = dragPosition
                                if (dropPosition != null && canvasBounds.contains(dropPosition)) {
                                    onDrop(dropPosition)
                                }
                                dragPosition = null
                                onDragPreviewEnd()
                            },
                            onDragCancel = {
                                dragPosition = null
                                onDragPreviewEnd()
                            }
                        )
                    }
                } else {
                    Modifier
                }
            )
    ) {
        Column {
            Text(item.title, color = if (enabled) TextPrimary else TextTertiary, fontWeight = FontWeight.Bold)
            Text(item.subtitle, color = TextTertiary, style = MaterialTheme.typography.labelSmall)
        }
    }
}
