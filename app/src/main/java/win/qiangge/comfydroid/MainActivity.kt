package win.qiangge.comfydroid

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import win.qiangge.comfydroid.model.*
import win.qiangge.comfydroid.network.NetworkClient
import win.qiangge.comfydroid.network.PromptRequest
import win.qiangge.comfydroid.network.WorkflowEngine
import win.qiangge.comfydroid.service.PollingService
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ComfyDroidApp()
        }
    }
}

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Generator : Screen("generator", "Create", Icons.Default.Home)
    object Library : Screen("library", "Library", Icons.Default.List)
}

@Composable
fun ComfyDroidApp() {
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    val dao = db.generationDao()
    
    LaunchedEffect(Unit) {
        val service = PollingService(dao)
        service.startPolling()
    }

    val prefs = remember { context.getSharedPreferences("comfydroid_prefs", Context.MODE_PRIVATE) }
    var serverConfigured by remember { mutableStateOf(false) }
    var serverIp by remember { mutableStateOf(prefs.getString("last_ip", "") ?: "") }
    var serverPort by remember { mutableStateOf(prefs.getString("last_port", "8188") ?: "8188") }
    
    var clientId by remember { mutableStateOf(prefs.getString("client_id", "") ?: "") }
    if (clientId.isEmpty()) {
        clientId = UUID.randomUUID().toString()
        prefs.edit().putString("client_id", clientId).apply()
    }

    // WebSocket (如果需要可启用)
    // val wsManager = remember { WebSocketManager(dao) }
    // LaunchedEffect(serverConfigured, serverIp, serverPort) {
    //    if (serverConfigured && serverIp.isNotEmpty()) {
    //        wsManager.connect(serverIp, serverPort, clientId)
    //    }
    // }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (!serverConfigured) {
            ConnectionScreen(
                initialIp = serverIp,
                initialPort = serverPort,
                onConnect = { ip, port ->
                    serverIp = ip
                    serverPort = port
                    prefs.edit().putString("last_ip", ip).putString("last_port", port).apply()
                    serverConfigured = true
                }
            )
        } else {
            MainScreen(serverIp, serverPort, clientId, dao)
        }
    }
}

@Composable
fun ConnectionScreen(initialIp: String, initialPort: String, onConnect: (String, String) -> Unit) {
    var ip by remember { mutableStateOf(initialIp) }
    var port by remember { mutableStateOf(initialPort) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("ComfyDroid", style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(32.dp))
        TextField(value = ip, onValueChange = { ip = it }, label = { Text("Server IP") }, enabled = !isLoading)
        Spacer(modifier = Modifier.height(8.dp))
        TextField(value = port, onValueChange = { port = it }, label = { Text("Port") }, enabled = !isLoading)
        Spacer(modifier = Modifier.height(32.dp))
        if (isLoading) {
            CircularProgressIndicator()
        } else {
            Button(onClick = { 
                isLoading = true
                scope.launch {
                    try {
                        NetworkClient.initialize(ip, port)
                        NetworkClient.getApiService().getSystemStats()
                        onConnect(ip, port)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Connect Failed: ${e.message}", Toast.LENGTH_LONG).show()
                    } finally {
                        isLoading = false
                    }
                }
            }) { Text("Connect") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(ip: String, port: String, clientId: String, dao: GenerationDao) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Generator) }
    val workflows = WorkflowRegistry.workflows
    var selectedWorkflow by remember { mutableStateOf<SuperWorkflow?>(null) }
    val libraryResults by dao.getAll().collectAsState(initial = emptyList())
    
    // 全屏预览状态
    var fullScreenResult by remember { mutableStateOf<GenerationResult?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                if (fullScreenResult == null) { // 全屏时隐藏 BottomBar
                    NavigationBar {
                        val items = listOf(Screen.Generator, Screen.Library)
                        items.forEach { screen ->
                            NavigationBarItem(
                                icon = { Icon(screen.icon, contentDescription = screen.label) },
                                label = { Text(screen.label) },
                                selected = currentScreen == screen,
                                onClick = { 
                                    currentScreen = screen 
                                    if (screen == Screen.Library) selectedWorkflow = null
                                }
                            )
                        }
                    }
                }
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                when (currentScreen) {
                    Screen.Generator -> {
                        // ... (Generator logic unchanged)
                        if (selectedWorkflow == null) {
                            LazyColumn(
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                item { 
                                    Text("Workflows", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = 8.dp)) 
                                }
                                items(workflows) { wf ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth().clickable { selectedWorkflow = wf },
                                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text(wf.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(wf.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        } else {
                            WorkflowExecutorScreen(
                                workflow = selectedWorkflow!!, 
                                onBack = { selectedWorkflow = null },
                                onSuccess = {
                                    selectedWorkflow = null
                                    currentScreen = Screen.Library
                                },
                                dao = dao,
                                clientId = clientId
                            )
                        }
                    }
                    Screen.Library -> {
                        LibraryScreen(
                            results = libraryResults, 
                            serverUrl = "http://$ip:$port", 
                            onDelete = { /* TODO */ },
                            onImageClick = { fullScreenResult = it } // 点击触发全屏
                        )
                    }
                }
            }
        }

        // 全屏覆盖层
        if (fullScreenResult != null) {
            FullScreenImageViewer(
                result = fullScreenResult!!,
                serverUrl = "http://$ip:$port",
                onDismiss = { fullScreenResult = null }
            )
        }
    }
}

@Composable
fun FullScreenImageViewer(
    result: GenerationResult,
    serverUrl: String,
    onDismiss: () -> Unit
) {
    val firstFile = result.outputFiles.split(",").firstOrNull()?.trim()
    val imageUrl = if (!firstFile.isNullOrEmpty()) "$serverUrl/view?filename=$firstFile&type=output" else null
    val context = LocalContext.current
    
    // 隐藏系统栏
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        if (window != null) {
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose {
            val window = (context as? Activity)?.window
            if (window != null) {
                val controller = WindowInsetsControllerCompat(window, window.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { onDismiss() }
            .padding(0.dp), // 确保无边距
        contentAlignment = Alignment.Center
    ) {
        if (imageUrl != null) {
            var scale by remember { mutableStateOf(1f) }
            var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(imageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = result.promptText,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            offset = if (scale == 1f) androidx.compose.ui.geometry.Offset.Zero else offset + pan
                        }
                    }
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    ),
                contentScale = ContentScale.Fit
            )
        }
    }
}

// ... (WorkflowExecutorScreen and other components) 

@Composable
fun WorkflowExecutorScreen(
    workflow: SuperWorkflow, 
    onBack: () -> Unit,
    onSuccess: () -> Unit,
    dao: GenerationDao, 
    clientId: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val engine = remember { WorkflowEngine(context) }
    
    // 初始化状态
    val inputStates = remember { 
        mutableStateMapOf<String, Any>().apply {
            workflow.inputs.forEach { input ->
                when (input) {
                    is TextInput -> put(input.id, input.defaultValue)
                    is NumberInput -> put(input.id, input.defaultValue)
                    else -> {} 
                }
            }
        }
    }
    
    var isGenerating by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    
    // 区分基础和高级输入
    val basicInputs = workflow.inputs.filter { it.id == "prompt" || it is ImageArrayInput }
    val advancedInputs = workflow.inputs.filter { it.id != "prompt" && it !is ImageArrayInput }
    var showAdvanced by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize().padding(16.dp).verticalScroll(scrollState)) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onBack) { Text("Back") }
            Spacer(modifier = Modifier.width(16.dp))
            Text(workflow.name, style = MaterialTheme.typography.titleLarge)
        }
        Spacer(modifier = Modifier.height(16.dp))

        // --- Basic Area ---
        basicInputs.forEach { input ->
            InputControl(input, inputStates, isGenerating)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- Advanced Area ---
        if (advancedInputs.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth().clickable { showAdvanced = !showAdvanced },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Advanced Settings", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Icon(
                        if (showAdvanced) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Toggle Advanced"
                    )
                }
            }
            
            AnimatedVisibility(visible = showAdvanced, enter = expandVertically(), exit = shrinkVertically()) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    // Aspect Ratio Shortcuts
                    AspectRatioSelector(
                        onSelect = { w, h ->
                            inputStates["width"] = w
                            inputStates["height"] = h
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    advancedInputs.forEach { input ->
                        InputControl(input, inputStates, isGenerating)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        
        // Generate Button
        if (isGenerating) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else {
            Button(onClick = { 
                isGenerating = true
                scope.launch {
                    try {
                        val promptText = inputStates["prompt"] as? String ?: ""
                        // 收集所有参数传给 Engine
                        val workflowJson = engine.buildFluxWorkflow(inputStates)
                        val finalJson = "{\"client_id\": \"$clientId\", \"prompt\": $workflowJson}"
                        
                        Log.d("ComfyDroid", "Submitting Modified JSON: $finalJson")

                        val mediaType = "application/json".toMediaTypeOrNull()
                        val body = RequestBody.create(mediaType, finalJson)
                        val response = NetworkClient.getApiService().queuePromptRaw(body)
                        
                        val newRecord = GenerationResult(
                            promptId = response.prompt_id, workflowName = workflow.name,
                            promptText = promptText, timestamp = System.currentTimeMillis(),
                            outputType = "IMAGE", outputFiles = "", status = "PENDING"
                        )
                        dao.insert(newRecord)
                        onSuccess()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    } finally {
                        isGenerating = false
                    }
                }
            }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) { 
                Text("GENERATE", style = MaterialTheme.typography.labelLarge)
            }
        }
        Spacer(modifier = Modifier.height(100.dp))
    }
}

@Composable
fun InputControl(input: WorkflowInput, inputStates: MutableMap<String, Any>, isGenerating: Boolean) {
    when (input) {
        is TextInput -> {
            TextField(
                value = inputStates[input.id] as? String ?: "",
                onValueChange = { inputStates[input.id] = it },
                label = { Text(input.label) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                minLines = if (input.multiline) 3 else 1,
                enabled = !isGenerating
            )
        }
        is NumberInput -> {
            if (input.id == "seed") {
                // Seed input with Dice button
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    TextField(
                        value = inputStates[input.id]?.toString() ?: "",
                        onValueChange = { 
                            val filtered = it.filter { char -> char.isDigit() || char == '.' }
                            inputStates[input.id] = if (input.isInteger) filtered.toLongOrNull() ?: 0L else filtered.toFloatOrNull() ?: 0f
                        },
                        label = { Text(input.label) },
                        modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        enabled = !isGenerating
                    )
                    IconButton(onClick = { 
                        inputStates[input.id] = (0..Long.MAX_VALUE).random() 
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Randomize Seed")
                    }
                }
            } else {
                TextField(
                    value = inputStates[input.id]?.toString() ?: "",
                    onValueChange = { 
                        val filtered = it.filter { char -> char.isDigit() || char == '.' }
                        inputStates[input.id] = if (input.isInteger) filtered.toLongOrNull() ?: 0L else filtered.toFloatOrNull() ?: 0f
                    },
                    label = { Text(input.label) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = !isGenerating
                )
            }
        }
        is ImageArrayInput -> {
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(input.label, style = MaterialTheme.typography.labelMedium)
                    Text("Image Array (0 to ${input.maxCount} images)", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        is ImageInput -> {
            Text("Single Image Input (Not implemented)", style = MaterialTheme.typography.bodySmall)
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AspectRatioSelector(onSelect: (Int, Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        AssistChip(
            onClick = { onSelect(1024, 1024) },
            label = { Text("1:1") }
        )
        AssistChip(
            onClick = { onSelect(832, 1216) }, // ~2:3 Portrait
            label = { Text("Port.") }
        )
        AssistChip(
            onClick = { onSelect(1216, 832) }, // ~3:2 Land
            label = { Text("Land.") }
        )
    }
}