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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextAlign
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
    val config: String? = null,
    val refProjectId: String? = null,
    val helpKey: String = "$kind:${config ?: ""}"
)

private data class DragPreview(
    val template: BlockTemplate,
    val position: Offset
)

private data class Section(
    val title: String,
    val blocks: List<BlockTemplate>
)

private data class ExampleProject(
    val name: String,
    val create: (String) -> PlaygroundProject
)

private val helpTexts = mapOf(
    "http_request:" to "Chiama qualsiasi API esterna e salva il risultato in una variabile.\n\n" +
        "Configura URL, metodo (GET/POST) e un \"jsonPath\" per estrarre un valore specifico.\n" +
        "Il risultato viene salvato in una variabile (es. \$meteo, \$temp) che puoi usare\n" +
        "in un blocco \"Confronto variabile\".\n\n" +
        "Esempio: API meteo → \$meteo, poi condizione \$meteo contiene \"Rain\".",
    "http_request:weather" to "Chiama wttr.in per ottenere il meteo e salva la descrizione in \$meteo.\n" +
        "Poi usa un blocco \"Confronto variabile\" per decidere cosa fare.",
    "logic:" to "I blocchi logici combinano più condizioni:\n\n" +
        "• AND: VERO solo quando TUTTE le condizioni collegate sono vere\n" +
        "• OR: VERO quando ALMENO UNA condizione collegata è vera\n" +
        "• NOT: Inverte il risultato (vero diventa falso e viceversa)\n\n" +
        "Collega le condizioni a sinistra e le azioni a destra.",
    "condition:" to "I blocchi condizione verificano se una situazione è vera o falsa.\n\n" +
        "Sono l'\"interruttore\" del tuo progetto: se la condizione è vera,\n" +
        "l'automazione prosegue verso le azioni.\n\n" +
        "Collega l'uscita a un blocco logico (AND/OR) o direttamente a un'azione.",
    "condition:time" to "Attiva l'automazione solo in una fascia oraria specifica.\n\n" +
        "Configura ora inizio e ora fine (es. 14:00-18:00).\n" +
        "Il blocco è VERO solo se l'orario corrente rientra nella fascia.",
    "condition:season" to "Attiva l'automazione in una stagione specifica.\n\n" +
        "Configura estate, inverno, primavera o autunno.\n" +
        "Il blocco è VERO solo se la data corrente corrisponde alla stagione scelta.",
    "condition:temperature" to "Confronta la temperatura attuale con una soglia.\n\n" +
        "Usa la temperatura della batteria del telefono. Puoi impostare\n" +
        "una soglia (es. > 25°C) per decidere se attivare l'automazione.",
    "condition:device_state" to "Controlla lo stato del tuo dispositivo Sonoff.\n\n" +
        "Il blocco è VERO se il dispositivo è acceso, FALSO se spento.\n" +
        "Utile per creare automazioni che reagiscono allo stato della presa.",
    "condition:presence" to "Rileva se il telefono è acceso e operativo.\n\n" +
        "Il blocco è VERO se la batteria è presente (percentuale > 0%).\n" +
        "Utile per attivare automazioni solo quando il telefono è attivo.",
    "confronto:" to "Confronta una variabile con un valore usando un operatore.\n\n" +
        "Operatori disponibili: ==, !=, >, <, >=, <=, contains, matches\n\n" +
        "Esempi:\n" +
        "• \$meteo contains \"Rain\" → vero se piove\n" +
        "• \$temp > 30 → vero se temperatura oltre 30\n" +
        "• \$battery_percentage <= 20 → vero se batteria scarica",
    "delay:" to "Aggiunge un ritardo prima di eseguire l'azione successiva.\n\n" +
        "Configura i minuti di attesa (es. 30 minuti).\n" +
        "Utile per: accendi luce → aspetta 30 min → spegni luce",
    "action_webhook:" to "Invia una richiesta HTTP a qualsiasi URL.\n\n" +
        "Perfetto per integrare IFTTT, Telegram, Home Assistant,\n" +
        "o il tuo server personale. Supporta GET e POST con body JSON.\n\n" +
        "Puoi usare variabili nel body con {variabile} o \${variabile}.",
    "action:" to "I blocchi azione eseguono comandi reali o simulati.\n\n" +
        "• Apri/Chiudi finestre → azione simulata (solo log)\n" +
        "• Accendi AC/Termosifone → accende dispositivo Sonoff\n" +
        "• Accendi/Spegni luce → comando Sonoff\n\n" +
        "Collega un'azione dopo una condizione o un blocco logico.",
    "action:open_windows" to "Azione simulata: registra nel log l'apertura finestre.\n\n" +
        "Nessun dispositivo reale necessario. Usala per testare\n" +
        "la logica del tuo progetto prima di collegare dispositivi veri.",
    "action:close_windows" to "Azione simulata: registra nel log la chiusura finestre.\n\n" +
        "Nessun dispositivo reale necessario. Usala per testare\n" +
        "la logica del tuo progetto prima di collegare dispositivi veri.",
    "action:ac_on" to "Accende il dispositivo Sonoff collegato.\n\n" +
        "Devi aver configurato un dispositivo Sonoff e fatto\n" +
        "il login con eWeLink. Il dispositivo specificato verrà acceso.",
    "action:heater_on" to "Accende il dispositivo Sonoff collegato.\n\n" +
        "Devi aver configurato un dispositivo Sonoff e fatto\n" +
        "il login con eWeLink. Il dispositivo specificato verrà acceso.",
    "action:light_on" to "Accende il dispositivo Sonoff collegato.\n\n" +
        "Devi aver configurato un dispositivo Sonoff e fatto\n" +
        "il login con eWeLink. Il dispositivo specificato verrà acceso.",
    "action:light_off" to "Spegne il dispositivo Sonoff collegato.\n\n" +
        "Devi aver configurato un dispositivo Sonoff e fatto\n" +
        "il login con eWeLink. Il dispositivo specificato verrà spento.",
    "device:" to "Rappresenta un dispositivo Sonoff/eWeLink.\n\n" +
        "Trascinalo nel canvas e collegalo ad azioni o blocchi logici.\n" +
        "Serve il login eWeLink per vedere i tuoi dispositivi.",
    "project:" to "Richiama un altro progetto salvato.\n\n" +
        "Permette di creare automazioni riutilizzabili e combinarle.\n" +
        "Utile per creare routine complesse da pezzi più semplici.\n\n" +
        "Attenzione: evita richiami circolari (A→B→A).",
    "battery:" to "Blocchi per monitorare lo stato della batteria.\n\n" +
        "Funzionano solo se il progetto ha trigger \"Batteria\".\n" +
        "Variabili disponibili: \$battery_percentage, \$battery_temp,\n" +
        "\$battery_charging, \$battery_voltage, \$battery_health.",
    "battery:battery_low" to "VERO quando la batteria è al 20% o meno.\n\n" +
        "Utile per attivare azioni quando il telefono è scarico.",
    "battery:battery_charging" to "VERO quando il telefono è in carica.\n\n" +
        "Utile per attivare azioni solo durante la ricarica.",
    "battery:battery<=" to "VERO quando la batteria è sotto o uguale alla soglia.\n\n" +
        "Configura una percentuale personalizzata (es. 30%).",
    "battery:battery>=" to "VERO quando la batteria è sopra o uguale alla soglia.\n\n" +
        "Configura una percentuale personalizzata (es. 80%).",
    "trigger:timer" to "Il progetto viene valutato automaticamente ogni minuto.\n\n" +
        "Ideale per automazioni basate su tempo, meteo, o sensori\n" +
        "che non dipendono dallo stato della batteria.",
    "trigger:orario" to "Il progetto viene eseguito a un orario specifico.\n\n" +
        "Configura ora e minuto (es. 07:00 o 23:00).\n" +
        "Ideale per routine giornaliere fisse.",
    "trigger:battery" to "Il progetto viene valutato ogni volta che la batteria cambia.\n\n" +
        "Le variabili \$battery_percentage, \$battery_temp,\n" +
        "\$battery_charging sono automaticamente disponibili.",
    "trigger:manual" to "Il progetto viene eseguito solo quando premi \"Esegui\".\n\n" +
        "Ideale per testare la logica o per azioni che vuoi\n" +
        "controllare tu manualmente."
)

private fun getHelpText(helpKey: String): String {
    val exact = helpTexts[helpKey]
    if (exact != null) return exact
    val prefix = helpKey.split(":").let { it[0] + ":" }
    return helpTexts[prefix] ?: helpTexts["condition:"] ?: "Usa questo blocco per creare la tua automazione."
}

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
    var helpDialogKey by remember { mutableStateOf<String?>(null) }

    val currentProject = projects.firstOrNull { it.id == selectedProjectId } ?: projects.first()
    val otherProjects = projects.filterNot { it.id == currentProject.id }

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
                    title = "API",
                    blocks = listOf(
                        BlockTemplate("http_request", "HTTP Request", "Chiama API esterna", config = "weather")
                    )
                )
            )
            add(
                Section(
                    title = "Batteria",
                    blocks = listOf(
                        BlockTemplate("condition", "Batteria <=", "Soglia personalizzata", config = "battery<="),
                        BlockTemplate("condition", "Batteria >=", "Soglia personalizzata", config = "battery>="),
                        BlockTemplate("condition", "Batteria scarica", "≤ 20%", config = "battery_low"),
                        BlockTemplate("condition", "In carica", "Stato ricarica", config = "battery_charging")
                    )
                )
            )
            add(
                Section(
                    title = "Logica",
                    blocks = listOf(
                        BlockTemplate("logic", "AND", "Tutte le condizioni"),
                        BlockTemplate("logic", "OR", "Basta una condizione"),
                        BlockTemplate("logic", "NOT", "Negazione logica"),
                        BlockTemplate("delay", "Delay", "Attesa temporizzata")
                    )
                )
            )
            add(
                Section(
                    title = "Condizioni",
                    blocks = listOf(
                        BlockTemplate("condition", "Ora", "Fascia oraria", config = "time"),
                        BlockTemplate("condition", "Stagione", "Estate / inverno", config = "season"),
                        BlockTemplate("condition", "Temperatura", "Soglia termica (batteria)", config = "temperature"),
                        BlockTemplate("condition", "Dispositivo", "Stato acceso/spento", config = "device_state"),
                        BlockTemplate("condition", "Presenza", "Telefono attivo", config = "presence"),
                        BlockTemplate("condition", "Confronto variabile", "Operatore + valore", config = "variable")
                    )
                )
            )
            add(
                Section(
                    title = "Azioni",
                    blocks = listOf(
                        BlockTemplate("action", "Apri finestre", "Azione simulata", config = "open_windows"),
                        BlockTemplate("action", "Chiudi finestre", "Azione simulata", config = "close_windows"),
                        BlockTemplate("action", "Accendi AC", "Climatizzazione", config = "ac_on"),
                        BlockTemplate("action", "Accendi termosifone", "Riscaldamento", config = "heater_on"),
                        BlockTemplate("action", "Spegni luce", "Dispositivo Sonoff", config = "light_off"),
                        BlockTemplate("action", "Accendi luce", "Dispositivo Sonoff", config = "light_on"),
                        BlockTemplate("action_webhook", "Webhook", "Richiesta HTTP")
                    )
                )
            )
            if (otherProjects.isNotEmpty()) {
                add(
                    Section(
                        title = "Libreria progetti",
                        blocks = otherProjects.map { project ->
                            BlockTemplate(
                                kind = "project",
                                title = project.name,
                                subtitle = if (project.isRunning) "Richiama progetto attivo" else "Richiama progetto salvato",
                                config = "project:${project.id}",
                                refProjectId = project.id
                            )
                        }
                    )
                )
            }
        }
    }

    fun updateCurrentProject(transform: (PlaygroundProject) -> PlaygroundProject) {
        projects = projects.map { if (it.id == currentProject.id) transform(it) else it }
    }

    fun updateProject(projectId: String, transform: (PlaygroundProject) -> PlaygroundProject) {
        projects = projects.map { if (it.id == projectId) transform(it) else it }
        if (selectedProjectId == projectId) {
            selectedProjectId = projectId
        }
    }

    fun addProject(name: String) {
        val project = PlaygroundStore.createProject(name)
        projects = projects + project
        selectedProjectId = project.id
    }

    fun addExampleProject(example: ExampleProject) {
        val project = example.create(PlaygroundStore.newId("project"))
        projects = projects + project
        selectedProjectId = project.id
    }

    fun addNode(template: BlockTemplate, dropPosition: Offset) {
        val configJson = when (template.kind) {
            "http_request" -> """{"url":"https://wttr.in/London?format=j1","method":"GET","jsonPath":"$.current_condition[0].weatherDesc[0].value","outputVar":"meteo"}"""
            "action_webhook" -> """{"url":"https://example.com/webhook","method":"POST","body":"{}"}"""
            "delay" -> """{"minutes":5}"""
            "condition" -> when (template.config) {
                "variable" -> """{"left":"{variabile}","op":"==","right":"valore"}"""
                else -> null
            }
            else -> null
        }
        val label = when {
            template.kind == "condition" && template.config == "variable" -> "Confronto"
            template.kind == "http_request" -> "HTTP Request"
            template.kind == "action_webhook" -> "Webhook"
            template.kind == "delay" -> "Delay"
            else -> template.title
        }
        val kind = when (template.kind) {
            "delay" -> "delay"
            "action_webhook" -> "action_webhook"
            else -> template.kind
        }
        val node = PlaygroundNode(
            id = PlaygroundStore.newId("node"),
            kind = kind,
            label = label,
            x = (dropPosition.x - canvasBounds.left - NODE_W / 2f).coerceIn(10f, (canvasSize.width - NODE_W - 10f).coerceAtLeast(10f)),
            y = (dropPosition.y - canvasBounds.top - NODE_H / 2f).coerceIn(10f, (canvasSize.height - NODE_H - 10f).coerceAtLeast(10f)),
            deviceId = template.deviceId,
            config = template.config,
            configJson = configJson,
            refProjectId = template.refProjectId
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
                    text = if (currentProject.isRunning) {
                        "Progetto attivo in background"
                    } else if (isLoggedIn) {
                        "Login eWeLink attivo"
                    } else {
                        "Effettua il login eWeLink per usare i dispositivi"
                    },
                    color = if (currentProject.isRunning) GreenHealthy else if (isLoggedIn) GreenHealthy else TextTertiary,
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

                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(projects, key = { it.id }) { project ->
                        val selected = project.id == selectedProjectId
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = selected,
                                onClick = { selectedProjectId = project.id },
                                label = {
                                    Column {
                                        Text(
                                            text = project.name,
                                            maxLines = 1,
                                            color = if (selected) TextPrimary else TextSecondary
                                        )
                                        if (project.isRunning) {
                                            Text(
                                                text = project.triggerType.let { t ->
                                                    when (t) {
                                                        "timer" -> "Timer ogni minuto"
                                                        "orario" -> "Orario fisso"
                                                        "battery" -> "Trigger batteria"
                                                        else -> "In esecuzione"
                                                    }
                                                },
                                                color = GreenHealthy,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    }
                                }
                            )
                            IconButton(
                                onClick = {
                                    updateProject(project.id) {
                                        it.copy(
                                            isRunning = true,
                                            lastRunAt = System.currentTimeMillis(),
                                            lastRunStatus = "Avviato manualmente"
                                        )
                                    }
                                },
                                enabled = !project.isRunning
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Esegui progetto", tint = ElegantPurple)
                            }
                            IconButton(
                                onClick = {
                                    updateProject(project.id) {
                                        it.copy(
                                            isRunning = false,
                                            lastRunAt = System.currentTimeMillis(),
                                            lastRunStatus = "Fermato manualmente"
                                        )
                                    }
                                },
                                enabled = project.isRunning
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = "Stop progetto", tint = RedAlert)
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { OutlinedButton(onClick = { applyPreset("estate") }) { Text("Preset estate") } }
                        item { OutlinedButton(onClick = { applyPreset("caldo") }) { Text("Preset caldo") } }
                        item { OutlinedButton(onClick = { applyPreset("freddo") }) { Text("Preset freddo") } }
                    }
                }

                HorizontalDivider(color = OutlineDark.copy(alpha = 0.25f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Trigger:", color = TextSecondary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    listOf("manual", "battery", "timer", "orario").forEach { t ->
                        FilterChip(
                            selected = currentProject.triggerType == t,
                            onClick = {
                                updateCurrentProject { it.copy(triggerType = t) }
                            },
                            label = { Text(
                                when (t) {
                                    "manual" -> "Manuale"
                                    "battery" -> "Batteria"
                                    "timer" -> "Timer"
                                    "orario" -> "Orario"
                                    else -> t
                                },
                                fontSize = 11.sp
                            ) },
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }
                if (currentProject.triggerType == "orario") {
                    val currentCfg = currentProject.triggerConfig ?: ""
                    OutlinedTextField(
                        value = currentCfg,
                        onValueChange = { newVal ->
                            updateCurrentProject { p -> p.copy(triggerConfig = newVal) }
                        },
                        label = { Text("Orario (HH:MM)", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall
                    )
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
                            onHelpClick = { helpDialogKey = it },
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
                            onHelpClick = { helpDialogKey = it },
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

    helpDialogKey?.let { key ->
        HelpDialog(
            helpKey = key,
            exampleProject = getExampleForHelpKey(key),
            onDismiss = { helpDialogKey = null },
            onCreateExample = { example ->
                addExampleProject(example)
                helpDialogKey = null
            }
        )
    }
}

@Composable
private fun HelpDialog(
    helpKey: String,
    exampleProject: ExampleProject?,
    onDismiss: () -> Unit,
    onCreateExample: (ExampleProject) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Come si usa",
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = getHelpText(helpKey),
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (exampleProject != null) {
                    Text(
                        text = "Clicca sotto per creare un nuovo progetto con questo blocco già configurato.",
                        color = TextTertiary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(
                        onClick = { onCreateExample(exampleProject) },
                        colors = ButtonDefaults.buttonColors(containerColor = ElegantPurple),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Crea progetto di esempio: ${exampleProject.name}")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Chiudi", color = ElegantPurple)
            }
        }
    )
}

private fun getExampleForHelpKey(helpKey: String): ExampleProject? {
    return exampleProjects[helpKey]
}

private val exampleProjects = mapOf(
    "http_request:" to ExampleProject("HTTP Request meteo") { id ->
        val n1 = PlaygroundNode(PlaygroundStore.newId("node"), "http_request", "HTTP Request", 60f, 112f,
            config = "weather",
            configJson = """{"url":"https://wttr.in/London?format=j1","method":"GET","jsonPath":"$.current_condition[0].weatherDesc[0].value","outputVar":"meteo"}""")
        val n2 = PlaygroundNode(PlaygroundStore.newId("node"), "condition", "Confronto", 320f, 112f,
            config = "variable",
            configJson = """{"left":"{meteo}","op":"contains","right":"Sunny"}""")
        val n3 = PlaygroundNode(PlaygroundStore.newId("node"), "action", "Apri finestre", 580f, 112f,
            config = "open_windows")
        PlaygroundProject(id = id, name = "Esempio: HTTP Request", nodes = listOf(n1, n2, n3),
            connections = listOf(PlaygroundConnection(n1.id, n2.id), PlaygroundConnection(n2.id, n3.id)))
    },
    "condition:time" to ExampleProject("Ora fissa") { id ->
        val n1 = PlaygroundNode(PlaygroundStore.newId("node"), "condition", "Ora: 22:00", 100f, 112f, config = "time=22:00-23:00")
        val n2 = PlaygroundNode(PlaygroundStore.newId("node"), "action", "Spegni luce", 360f, 112f, config = "light_off")
        PlaygroundProject(id = id, name = "Esempio: Fascia oraria", nodes = listOf(n1, n2),
            connections = listOf(PlaygroundConnection(n1.id, n2.id)))
    },
    "condition:season" to ExampleProject("Stagione inverno") { id ->
        val n1 = PlaygroundNode(PlaygroundStore.newId("node"), "condition", "Inverno", 100f, 112f, config = "season=winter")
        val n2 = PlaygroundNode(PlaygroundStore.newId("node"), "action", "Accendi termosifone", 360f, 112f, config = "heater_on")
        PlaygroundProject(id = id, name = "Esempio: Stagione", nodes = listOf(n1, n2),
            connections = listOf(PlaygroundConnection(n1.id, n2.id)))
    },
    "condition:temperature" to ExampleProject("Caldo") { id ->
        val n1 = PlaygroundNode(PlaygroundStore.newId("node"), "condition", "Temp > 25°C", 100f, 112f, config = "temp_gt_25")
        val n2 = PlaygroundNode(PlaygroundStore.newId("node"), "action", "Accendi AC", 360f, 112f, config = "ac_on")
        PlaygroundProject(id = id, name = "Esempio: Temperatura", nodes = listOf(n1, n2),
            connections = listOf(PlaygroundConnection(n1.id, n2.id)))
    },
    "delay:" to ExampleProject("Luce temporizzata") { id ->
        val n1 = PlaygroundNode(PlaygroundStore.newId("node"), "condition", "Luce accesa", 60f, 80f, config = "device_state")
        val n2 = PlaygroundNode(PlaygroundStore.newId("node"), "delay", "Delay 30 min", 280f, 80f, configJson = """{"minutes":30}""")
        val n3 = PlaygroundNode(PlaygroundStore.newId("node"), "action", "Spegni luce", 500f, 80f, config = "light_off")
        PlaygroundProject(id = id, name = "Esempio: Delay", nodes = listOf(n1, n2, n3),
            connections = listOf(PlaygroundConnection(n1.id, n2.id), PlaygroundConnection(n2.id, n3.id)))
    },
    "action_webhook:" to ExampleProject("Webhook notifica") { id ->
        val n1 = PlaygroundNode(PlaygroundStore.newId("node"), "condition", "Ora: 23:00", 80f, 112f, config = "time=23:00-23:30")
        val n2 = PlaygroundNode(PlaygroundStore.newId("node"), "action_webhook", "Webhook", 340f, 112f,
            configJson = """{"url":"https://maker.ifttt.com/trigger/bedtime/json","method":"POST","body":"{\"event\":\"buonanotte\"}"}""")
        PlaygroundProject(id = id, name = "Esempio: Webhook", nodes = listOf(n1, n2),
            connections = listOf(PlaygroundConnection(n1.id, n2.id)))
    },
    "condition:presence" to ExampleProject("Presenza accendi luce") { id ->
        val n1 = PlaygroundNode(PlaygroundStore.newId("node"), "condition", "Presenza", 100f, 112f, config = "presence")
        val n2 = PlaygroundNode(PlaygroundStore.newId("node"), "action", "Accendi luce", 360f, 112f, config = "light_on")
        PlaygroundProject(id = id, name = "Esempio: Presenza", nodes = listOf(n1, n2),
            connections = listOf(PlaygroundConnection(n1.id, n2.id)))
    },
    "condition:device_state" to ExampleProject("Dispositivo acceso") { id ->
        val n1 = PlaygroundNode(PlaygroundStore.newId("node"), "condition", "Dispositivo acceso", 100f, 112f, config = "device_state")
        val n2 = PlaygroundNode(PlaygroundStore.newId("node"), "action", "Spegni luce", 360f, 112f, config = "light_off")
        PlaygroundProject(id = id, name = "Esempio: Dispositivo", nodes = listOf(n1, n2),
            connections = listOf(PlaygroundConnection(n1.id, n2.id)))
    },
    "logic:" to ExampleProject("AND logico") { id ->
        val n1 = PlaygroundNode(PlaygroundStore.newId("node"), "condition", "Ora: 14-18", 60f, 48f, config = "time=14:00-18:00")
        val n2 = PlaygroundNode(PlaygroundStore.newId("node"), "condition", "Estate", 60f, 178f, config = "season=summer")
        val n3 = PlaygroundNode(PlaygroundStore.newId("node"), "logic", "AND", 296f, 104f, config = "and")
        val n4 = PlaygroundNode(PlaygroundStore.newId("node"), "action", "Apri finestre", 532f, 104f, config = "open_windows")
        PlaygroundProject(id = id, name = "Esempio: AND", nodes = listOf(n1, n2, n3, n4),
            connections = listOf(PlaygroundConnection(n1.id, n3.id), PlaygroundConnection(n2.id, n3.id), PlaygroundConnection(n3.id, n4.id)))
    },
    "confronto:" to ExampleProject("Confronto variabile") { id ->
        val n1 = PlaygroundNode(PlaygroundStore.newId("node"), "http_request", "HTTP Request", 60f, 112f,
            config = "weather",
            configJson = """{"url":"https://wttr.in/London?format=j1","method":"GET","jsonPath":"$.current_condition[0].weatherDesc[0].value","outputVar":"meteo"}""")
        val n2 = PlaygroundNode(PlaygroundStore.newId("node"), "condition", "\$meteo contiene Sole", 320f, 112f,
            config = "variable",
            configJson = """{"left":"{meteo}","op":"contains","right":"Sunny"}""")
        val n3 = PlaygroundNode(PlaygroundStore.newId("node"), "action", "Apri finestre", 580f, 112f, config = "open_windows")
        PlaygroundProject(id = id, name = "Esempio: Confronto", nodes = listOf(n1, n2, n3),
            connections = listOf(PlaygroundConnection(n1.id, n2.id), PlaygroundConnection(n2.id, n3.id)))
    },
    "battery:battery_low" to ExampleProject("Batteria scarica accendi presa") { id ->
        val n1 = PlaygroundNode(PlaygroundStore.newId("node"), "condition", "Batteria ≤ 20%", 100f, 112f, config = "battery_low")
        val n2 = PlaygroundNode(PlaygroundStore.newId("node"), "action", "Accendi luce", 360f, 112f, config = "light_on")
        PlaygroundProject(id = id, name = "Esempio: Batteria scarica", nodes = listOf(n1, n2),
            connections = listOf(PlaygroundConnection(n1.id, n2.id)), triggerType = "battery")
    },
    "battery:battery_charging" to ExampleProject("In carica spegni presa") { id ->
        val n1 = PlaygroundNode(PlaygroundStore.newId("node"), "condition", "In carica", 100f, 112f, config = "battery_charging")
        val n2 = PlaygroundNode(PlaygroundStore.newId("node"), "action", "Spegni luce", 360f, 112f, config = "light_off")
        PlaygroundProject(id = id, name = "Esempio: In carica", nodes = listOf(n1, n2),
            connections = listOf(PlaygroundConnection(n1.id, n2.id)), triggerType = "battery")
    },
    "trigger:timer" to ExampleProject("Timer ogni minuto") { id ->
        val n1 = PlaygroundNode(PlaygroundStore.newId("node"), "condition", "Ora: 14-16", 100f, 112f, config = "time=14:00-16:00")
        val n2 = PlaygroundNode(PlaygroundStore.newId("node"), "action", "Apri finestre", 360f, 112f, config = "open_windows")
        PlaygroundProject(id = id, name = "Esempio: Timer", nodes = listOf(n1, n2),
            connections = listOf(PlaygroundConnection(n1.id, n2.id)), triggerType = "timer")
    },
    "trigger:orario" to ExampleProject("Orario fisso") { id ->
        val n1 = PlaygroundNode(PlaygroundStore.newId("node"), "action", "Chiudi finestre", 100f, 112f, config = "close_windows")
        PlaygroundProject(id = id, name = "Esempio: Orario fisso", nodes = listOf(n1),
            triggerType = "orario", triggerConfig = "23:00")
    }
)

@Composable
private fun Sidebar(
    sections: List<Section>,
    isLoggedIn: Boolean,
    onHelpClick: (String) -> Unit,
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
                                onDragStart = { onTemplateDragStart(template) },
                                onHelpClick = { onHelpClick(template.helpKey) }
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
    onDragStart: (BlockTemplate) -> Unit,
    onHelpClick: () -> Unit
) {
    val bg = when (template.kind) {
        "device" -> ElegantPurple.copy(alpha = 0.16f)
        "logic" -> Color(0xFF4C8FFF).copy(alpha = 0.16f)
        "delay" -> Color(0xFF9C27B0).copy(alpha = 0.16f)
        "action", "action_webhook" -> GreenHealthy.copy(alpha = 0.14f)
        "http_request" -> Color(0xFFFF9800).copy(alpha = 0.16f)
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(template.title, color = if (enabled) TextPrimary else TextTertiary, fontWeight = FontWeight.Bold)
                Text(template.subtitle, color = TextTertiary, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(modifier = Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(ElegantPurple.copy(alpha = 0.2f), CircleShape)
                    .clickable { onHelpClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = "Aiuto",
                    tint = ElegantPurple,
                    modifier = Modifier.size(14.dp)
                )
            }
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
        "delay" -> Color(0xFF9C27B0)
        "action", "action_webhook" -> GreenHealthy
        "http_request" -> Color(0xFFFF9800)
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
                        text = when (node.kind) {
                            "delay" -> "Attesa"
                            "http_request" -> "HTTP Request"
                            "action_webhook" -> "Webhook"
                            else -> node.kind.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                        },
                        color = TextTertiary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            Text(
                text = when {
                    node.deviceId != null -> "Device ${node.deviceId.take(8)}"
                    node.refProjectId != null -> "Progetto ${node.refProjectId.take(8)}"
                    node.configJson != null -> {
                        val cfg = try { org.json.JSONObject(node.configJson) } catch (_: Exception) { null }
                        when (node.kind) {
                            "http_request" -> cfg?.optString("url", "").orEmpty().take(30)
                            "delay" -> "${cfg?.optInt("minutes", 5) ?: 5} min"
                            "action_webhook" -> cfg?.optString("url", "").orEmpty().take(30)
                            else -> "configurabile"
                        }
                    }
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
