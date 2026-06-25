package com.example.playground

import android.content.Context
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material3.FilterChip
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

private const val NODE_W = 184f
private const val NODE_H = 104f

private data class BlockTemplate(
    val kind: String,
    val title: String,
    val subtitle: String,
    val deviceId: String? = null,
    val config: String? = null
)

private data class DragPreview(
    val template: BlockTemplate,
    val position: Offset
)

private data class Section(
    val title: String,
    val blocks: List<BlockTemplate>
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
    if (projects.isEmpty()) {
        projects = listOf(PlaygroundStore.createProject("Progetto 1"))
    }

    var selectedProjectId by remember {
        mutableStateOf(PlaygroundStore.loadSelectedProjectId(context) ?: projects.first().id)
    }
    var renameTarget by remember { mutableStateOf<PlaygroundProject?>(null) }
    var renameValue by remember { mutableStateOf("") }
    var showNewProjectDialog by remember { mutableStateOf(false) }
    var newProjectName by remember { mutableStateOf("") }
    var selectedConnectionSource by remember { mutableStateOf<String?>(null) }
    var canvasBounds by remember { mutableStateOf(Rect.Zero) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }
    var dragPreview by remember { mutableStateOf<DragPreview?>(null) }

    val currentProject = projects.firstOrNull { it.id == selectedProjectId } ?: projects.first()

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

    val sections = remember(isLoggedIn, availableDevices) {
        buildList {
            if (isLoggedIn) {
                add(
                    Section(
                        title = "Dispositivi",
                        blocks = availableDevices.take(8).map { (name, id) ->
                            BlockTemplate("device", name, "Dispositivo eWeLink", deviceId = id)
                        }
                    )
                )
            }
            add(
                Section(
                    title = "Logica",
                    blocks = listOf(
                        BlockTemplate("logic", "AND", "Tutte le condizioni"),
                        BlockTemplate("logic", "OR", "Basta una condizione"),
                        BlockTemplate("logic", "NOT", "Negazione logica")
                    )
                )
            )
            add(
                Section(
                    title = "Condizioni",
                    blocks = listOf(
                        BlockTemplate("condition", "Ora", "Fascia oraria", config = "time"),
                        BlockTemplate("condition", "Stagione", "Estate / inverno", config = "season"),
                        BlockTemplate("condition", "Temperatura", "Soglia termica", config = "temperature"),
                        BlockTemplate("condition", "Luce accesa", "Stato dispositivo", config = "light_state"),
                        BlockTemplate("condition", "Presenza", "Sensore presenza", config = "presence")
                    )
                )
            )
            add(
                Section(
                    title = "Azioni",
                    blocks = listOf(
                        BlockTemplate("action", "Apri finestre", "Azione finale", config = "open_windows"),
                        BlockTemplate("action", "Chiudi finestre", "Azione finale", config = "close_windows"),
                        BlockTemplate("action", "Accendi AC", "Climatizzazione", config = "ac_on"),
                        BlockTemplate("action", "Accendi termosifone", "Riscaldamento", config = "heater_on"),
                        BlockTemplate("action", "Spegni luce", "Azione finale", config = "light_off"),
                        BlockTemplate("action", "Accendi luce", "Azione finale", config = "light_on")
                    )
                )
            )
        }
    }

    fun updateCurrentProject(transform: (PlaygroundProject) -> PlaygroundProject) {
        projects = projects.map { if (it.id == currentProject.id) transform(it) else it }
    }

    fun addProject(name: String) {
        val project = PlaygroundStore.createProject(name)
        projects = projects + project
        selectedProjectId = project.id
    }

    fun addNode(template: BlockTemplate, dropPosition: Offset) {
        val node = PlaygroundNode(
            id = PlaygroundStore.newId("node"),
            kind = template.kind,
            label = template.title,
            x = (dropPosition.x - canvasBounds.left - NODE_W / 2f).coerceIn(10f, (canvasSize.width - NODE_W - 10f).coerceAtLeast(10f)),
            y = (dropPosition.y - canvasBounds.top - NODE_H / 2f).coerceIn(10f, (canvasSize.height - NODE_H - 10f).coerceAtLeast(10f)),
            deviceId = template.deviceId,
            config = template.config
        )
        updateCurrentProject { it.copy(nodes = it.nodes + node) }
    }

    fun moveNode(nodeId: String, newX: Float, newY: Float) {
        updateCurrentProject { project ->
            project.copy(
                nodes = project.nodes.map { node ->
                    if (node.id == nodeId) {
                        node.copy(
                            x = newX.coerceIn(8f, (canvasSize.width - NODE_W - 8f).coerceAtLeast(8f)),
                            y = newY.coerceIn(8f, (canvasSize.height - NODE_H - 8f).coerceAtLeast(8f))
                        )
                    } else {
                        node
                    }
                }
            )
        }
    }

    fun connect(fromId: String, toId: String) {
        if (fromId == toId) return
        updateCurrentProject { project ->
            val exists = project.connections.any { it.fromNodeId == fromId && it.toNodeId == toId }
            if (exists) project else project.copy(connections = project.connections + PlaygroundConnection(fromId, toId))
        }
        selectedConnectionSource = null
    }

    fun applyPreset(kind: String) {
        val (nodes, connections) = when (kind) {
            "estate" -> {
                val a = PlaygroundStore.newId("node")
                val b = PlaygroundStore.newId("node")
                val c = PlaygroundStore.newId("node")
                val d = PlaygroundStore.newId("node")
                val e = PlaygroundStore.newId("node")
                val f = PlaygroundStore.newId("node")
                listOf(
                    PlaygroundNode(a, "device", "Luce cucina", 60f, 92f, config = "light"),
                    PlaygroundNode(b, "condition", "Stagione: estate", 296f, 48f, config = "season=summer"),
                    PlaygroundNode(c, "condition", "Pomeriggio", 296f, 178f, config = "time=14:00-18:00"),
                    PlaygroundNode(d, "logic", "AND", 522f, 104f, config = "and"),
                    PlaygroundNode(e, "action", "Apri finestre", 748f, 48f, config = "open_windows"),
                    PlaygroundNode(f, "action", "Spegni luce", 748f, 180f, config = "light_off")
                ) to listOf(
                    PlaygroundConnection(a, d),
                    PlaygroundConnection(b, d),
                    PlaygroundConnection(c, d),
                    PlaygroundConnection(d, e),
                    PlaygroundConnection(d, f)
                )
            }
            "caldo" -> {
                val a = PlaygroundStore.newId("node")
                val b = PlaygroundStore.newId("node")
                listOf(
                    PlaygroundNode(a, "condition", "Temperatura > 25°C", 88f, 112f, config = "temp_gt_25"),
                    PlaygroundNode(b, "action", "Accendi AC", 392f, 112f, config = "ac_on")
                ) to listOf(PlaygroundConnection(a, b))
            }
            else -> {
                val a = PlaygroundStore.newId("node")
                val b = PlaygroundStore.newId("node")
                listOf(
                    PlaygroundNode(a, "condition", "Temperatura < 15°C", 88f, 112f, config = "temp_lt_15"),
                    PlaygroundNode(b, "action", "Accendi termosifone", 392f, 112f, config = "heater_on")
                ) to listOf(PlaygroundConnection(a, b))
            }
        }
        updateCurrentProject { it.copy(nodes = nodes, connections = connections) }
    }

    LaunchedEffect(projects, selectedProjectId) {
        PlaygroundStore.saveProjects(context, projects)
        PlaygroundStore.saveSelectedProjectId(context, selectedProjectId)
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
                Text("Playground", color = TextPrimary, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = if (isLoggedIn) "Login eWeLink attivo" else "Effettua il login eWeLink per usare i dispositivi",
                    color = if (isLoggedIn) GreenHealthy else TextTertiary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.CardDark),
            border = androidx.compose.foundation.BorderStroke(1.dp, OutlineDark.copy(alpha = 0.35f)),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Progetti", color = TextPrimary, fontWeight = FontWeight.Bold)
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
                        FilterChip(
                            selected = selected,
                            onClick = { selectedProjectId = project.id },
                            label = {
                                Text(
                                    text = project.name,
                                    maxLines = 1,
                                    color = if (selected) TextPrimary else TextSecondary
                                )
                            }
                        )
                    }
                }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { OutlinedButton(onClick = { applyPreset("estate") }) { Text("Preset estate") } }
                    item { OutlinedButton(onClick = { applyPreset("caldo") }) { Text("Preset caldo") } }
                    item { OutlinedButton(onClick = { applyPreset("freddo") }) { Text("Preset freddo") } }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(1.dp, OutlineDark.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                .background(com.example.ui.theme.CardDark.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                .padding(12.dp)
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val compact = maxWidth < 820.dp
                if (compact) {
                    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Sidebar(
                            sections = sections,
                            isLoggedIn = isLoggedIn,
                            modifier = Modifier.height(240.dp)
                        ) { template ->
                            dragPreview = DragPreview(template, Offset.Zero)
                        }
                        Workspace(
                            project = currentProject,
                            selectedConnectionSource = selectedConnectionSource,
                            dragPreview = dragPreview,
                            canvasBounds = canvasBounds,
                            onCanvasBounds = { canvasBounds = it },
                            onCanvasSize = { canvasSize = it },
                            onConnect = { from, to -> connect(from, to) },
                            onMoveNode = { id, x, y -> moveNode(id, x, y) },
                            onSelectSource = { selectedConnectionSource = it },
                            onDropNode = { template, position -> addNode(template, position) },
                            onDragPreview = { preview -> dragPreview = preview },
                            onDragEnd = { dragPreview = null },
                            showLoginGate = !isLoggedIn
                        )
                    }
                } else {
                    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Sidebar(
                            sections = sections,
                            isLoggedIn = isLoggedIn,
                            modifier = Modifier.width(292.dp)
                        ) { template ->
                            dragPreview = DragPreview(template, Offset.Zero)
                        }
                        Workspace(
                            project = currentProject,
                            selectedConnectionSource = selectedConnectionSource,
                            dragPreview = dragPreview,
                            canvasBounds = canvasBounds,
                            onCanvasBounds = { canvasBounds = it },
                            onCanvasSize = { canvasSize = it },
                            onConnect = { from, to -> connect(from, to) },
                            onMoveNode = { id, x, y -> moveNode(id, x, y) },
                            onSelectSource = { selectedConnectionSource = it },
                            onDropNode = { template, position -> addNode(template, position) },
                            onDragPreview = { preview -> dragPreview = preview },
                            onDragEnd = { dragPreview = null },
                            showLoginGate = !isLoggedIn
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
                    addProject(newProjectName.trim().ifBlank { "Progetto ${projects.size + 1}" })
                    newProjectName = ""
                    showNewProjectDialog = false
                }) { Text("Crea") }
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
                    projects = projects.map { if (it.id == target.id) it.copy(name = renameValue.trim().ifBlank { target.name }) else it }
                    renameTarget = null
                }) { Text("Salva") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Annulla") }
            }
        )
    }
}

@Composable
private fun Sidebar(
    sections: List<Section>,
    isLoggedIn: Boolean,
    modifier: Modifier = Modifier,
    onTemplateDragStart: (BlockTemplate) -> Unit
) {
    Card(
        modifier = modifier.fillMaxHeight(),
        colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.CardDark),
        border = androidx.compose.foundation.BorderStroke(1.dp, OutlineDark.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Barra blocchi", color = TextPrimary, fontWeight = FontWeight.Bold)
            if (!isLoggedIn) {
                Text(
                    text = "Login richiesto per usare i dispositivi eWeLink.",
                    color = TextTertiary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(sections, key = { it.title }) { section ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(section.title, color = TextSecondary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        section.blocks.forEach { template ->
                            BlockChip(
                                template = template,
                                enabled = isLoggedIn || template.kind != "device",
                                onDragStart = { onTemplateDragStart(template) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BlockChip(
    template: BlockTemplate,
    enabled: Boolean,
    onDragStart: (BlockTemplate) -> Unit
) {
    val bg = when (template.kind) {
        "device" -> ElegantPurple.copy(alpha = 0.16f)
        "logic" -> Color(0xFF4C8FFF).copy(alpha = 0.16f)
        "action" -> GreenHealthy.copy(alpha = 0.14f)
        else -> Color(0xFFF5A524).copy(alpha = 0.16f)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(12.dp))
            .border(1.dp, OutlineDark.copy(alpha = if (enabled) 0.35f else 0.15f), RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { onDragStart(template) }
            .pointerInput(template.kind, enabled) {
                if (enabled) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { onDragStart(template) },
                        onDrag = { _, _ -> },
                        onDragEnd = { },
                        onDragCancel = { }
                    )
                }
            }
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Column {
            Text(template.title, color = if (enabled) TextPrimary else TextTertiary, fontWeight = FontWeight.Bold)
            Text(template.subtitle, color = TextTertiary, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun Workspace(
    project: PlaygroundProject,
    selectedConnectionSource: String?,
    dragPreview: DragPreview?,
    canvasBounds: Rect,
    onCanvasBounds: (Rect) -> Unit,
    onCanvasSize: (Size) -> Unit,
    onConnect: (String, String) -> Unit,
    onMoveNode: (String, Float, Float) -> Unit,
    onSelectSource: (String?) -> Unit,
    onDropNode: (BlockTemplate, Offset) -> Unit,
    onDragPreview: (DragPreview?) -> Unit,
    onDragEnd: () -> Unit,
    showLoginGate: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned {
                onCanvasBounds(it.boundsInWindow())
                onCanvasSize(Size(it.size.width.toFloat(), it.size.height.toFloat()))
            }
            .background(com.example.ui.theme.BackgroundDark.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            .border(1.dp, OutlineDark.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            project.connections.forEach { connection ->
                val from = project.nodes.firstOrNull { it.id == connection.fromNodeId } ?: return@forEach
                val to = project.nodes.firstOrNull { it.id == connection.toNodeId } ?: return@forEach
                val start = Offset(from.x + NODE_W, from.y + NODE_H / 2f)
                val end = Offset(to.x, to.y + NODE_H / 2f)
                val path = Path().apply {
                    moveTo(start.x, start.y)
                    cubicTo(start.x + 90f, start.y, end.x - 90f, end.y, end.x, end.y)
                }
                drawPath(path, color = ElegantPurple.copy(alpha = 0.92f), style = Stroke(width = 5f, cap = StrokeCap.Round))
            }
        }

        if (project.nodes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Trascina blocchi dalla barra laterale dentro l'area di lavoro",
                    color = TextTertiary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        project.nodes.forEach { node ->
            NodeCard(
                node = node,
                selectedConnectionSource = selectedConnectionSource,
                onMoveNode = onMoveNode,
                onSelectSource = onSelectSource,
                onConnect = onConnect
            )
        }

        dragPreview?.let { preview ->
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (preview.position.x - canvasBounds.left - NODE_W / 2f).roundToInt(),
                            (preview.position.y - canvasBounds.top - NODE_H / 2f).roundToInt()
                        )
                    }
                    .background(com.example.ui.theme.CardDark, RoundedCornerShape(12.dp))
                    .border(1.dp, ElegantPurple, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(preview.template.title, color = TextPrimary, fontWeight = FontWeight.Bold)
            }
        }

        if (showLoginGate) {
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
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Login richiesto", color = TextPrimary, fontWeight = FontWeight.Bold)
                        Text(
                            text = "Accedi con eWeLink per trascinare i dispositivi nel playground.",
                            color = TextSecondary
                        )
                    }
                }
            }
        }

    }
}

@Composable
private fun NodeCard(
    node: PlaygroundNode,
    selectedConnectionSource: String?,
    onMoveNode: (String, Float, Float) -> Unit,
    onSelectSource: (String?) -> Unit,
    onConnect: (String, String) -> Unit
) {
    val nodeColor = when (node.kind) {
        "device" -> ElegantPurple
        "logic" -> Color(0xFF4C8FFF)
        "action" -> GreenHealthy
        else -> Color(0xFFF5A524)
    }
    val isSelected = selectedConnectionSource == node.id

    Box(
        modifier = Modifier
            .offset { IntOffset(node.x.roundToInt(), node.y.roundToInt()) }
            .size(NODE_W.dp, NODE_H.dp)
            .background(com.example.ui.theme.CardDark, RoundedCornerShape(14.dp))
            .border(1.dp, if (isSelected) ElegantPurple else OutlineDark.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .pointerInput(node.id) {
                detectDragGesturesAfterLongPress(
                    onDrag = { _, dragAmount ->
                        onMoveNode(node.id, node.x + dragAmount.x, node.y + dragAmount.y)
                    }
                )
            }
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(10.dp).background(nodeColor, CircleShape))
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
                    node.deviceId != null -> "Device ${node.deviceId.take(8)}"
                    !node.config.isNullOrBlank() -> node.config
                    else -> "configurabile"
                },
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = (-8).dp)
                .size(18.dp)
                .background(ElegantPurple, CircleShape)
                .clickable { onSelectSource(node.id) }
        )

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = 8.dp)
                .size(18.dp)
                .background(nodeColor, CircleShape)
                .clickable { selectedConnectionSource?.let { onConnect(it, node.id) } }
        )
    }
}
