package win.qiangge.comfydroid

import android.os.Bundle
import android.widget.Toast
import android.util.Log
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import win.qiangge.comfydroid.model.*
import win.qiangge.comfydroid.network.NetworkClient
import win.qiangge.comfydroid.network.PromptRequest
import win.qiangge.comfydroid.network.WorkflowEngine
import win.qiangge.comfydroid.network.WebSocketManager
import win.qiangge.comfydroid.service.PollingService
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody

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
    
    // 启动 HTTP 轮询 (用于最终结果确认)
    LaunchedEffect(Unit) {
        val service = PollingService(dao)
        service.startPolling()
    }

    val prefs = remember { context.getSharedPreferences("comfydroid_prefs", Context.MODE_PRIVATE) }
    var serverConfigured by remember { mutableStateOf(false) }
    var serverIp by remember { mutableStateOf(prefs.getString("last_ip", "") ?: "") }
    var serverPort by remember { mutableStateOf(prefs.getString("last_port", "8188") ?: "8188") }
    
    // 获取或生成持久化的 Client ID
    var clientId by remember { mutableStateOf(prefs.getString("client_id", "") ?: "") }
    if (clientId.isEmpty()) {
        clientId = UUID.randomUUID().toString()
        prefs.edit().putString("client_id", clientId).apply()
    }

    // WebSocket 管理器
    val wsManager = remember { WebSocketManager(dao) }

    // 当连接配置好后，启动 WebSocket
    LaunchedEffect(serverConfigured, serverIp, serverPort) {
        if (serverConfigured && serverIp.isNotEmpty()) {
            wsManager.connect(serverIp, serverPort, clientId)
        }
    }

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

    Scaffold(
        bottomBar = {
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
    ) {
        Box(modifier = Modifier.padding(it)) {
            when (currentScreen) {
                Screen.Generator -> {
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
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(wf.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(wf.description, style = MaterialTheme.typography.bodyMedium)
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
                    LibraryScreen(results = libraryResults, serverUrl = "http://$ip:$port", onDelete = { /* TODO */ })
                }
            }
        }
    }
}

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

    Column(modifier = modifier.fillMaxSize().padding(16.dp).verticalScroll(scrollState)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onBack) { Text("Back") }
            Spacer(modifier = Modifier.width(16.dp))
            Text(workflow.name, style = MaterialTheme.typography.titleLarge)
        }
        Spacer(modifier = Modifier.height(16.dp))
        workflow.inputs.forEach { input ->
            when (input) {
                is TextInput -> {
                    TextField(value = inputStates[input.id] as? String ?: "", onValueChange = { inputStates[input.id] = it },
                        label = { Text(input.label) }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        minLines = if (input.multiline) 3 else 1, enabled = !isGenerating)
                }
                is NumberInput -> {
                    TextField(value = inputStates[input.id]?.toString() ?: "", 
                        onValueChange = { val filtered = it.filter { char -> char.isDigit() || char == '.' }
                            inputStates[input.id] = if (input.isInteger) filtered.toLongOrNull() ?: 0L else filtered.toFloatOrNull() ?: 0f },
                        label = { Text(input.label) }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), enabled = !isGenerating)
                }
                is ImageArrayInput -> {
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(input.label, style = MaterialTheme.typography.labelMedium)
                            Text("Image Array (0 to ${input.maxCount} images)", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                else -> {}
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
        if (isGenerating) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else {
            Button(onClick = { 
                isGenerating = true
                scope.launch {
                    try {
                        val promptInput = inputStates["prompt"] as? String ?: ""
                        val seedRaw = inputStates["seed"]
                        val seed = when(seedRaw) {
                            is Long -> seedRaw
                            is Float -> seedRaw.toLong()
                            is Int -> seedRaw.toLong()
                            else -> 0L
                        }
                        
                        val workflowJson = engine.buildFluxWorkflow(promptInput, seed)
                        // 使用全局 clientId
                        val finalJson = "{\"client_id\": \"$clientId\", \"prompt\": $workflowJson}"
                        
                        val mediaType = "application/json".toMediaTypeOrNull()
                        val body = RequestBody.create(mediaType, finalJson)
                        val response = NetworkClient.getApiService().queuePromptRaw(body)
                        
                        val newRecord = GenerationResult(
                            promptId = response.prompt_id, workflowName = workflow.name,
                            promptText = promptInput, timestamp = System.currentTimeMillis(),
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