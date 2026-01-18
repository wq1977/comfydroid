package win.qiangge.comfydroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import win.qiangge.comfydroid.model.*


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ComfyDroidApp()
        }
    }
}

import win.qiangge.comfydroid.model.GenerationDao
import win.qiangge.comfydroid.model.GenerationResult
import win.qiangge.comfydroid.model.AppDatabase

import win.qiangge.comfydroid.service.PollingService

@Composable
fun ComfyDroidApp() {
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    val dao = db.generationDao()
    
    // 启动轮询服务 (只启动一次)
    LaunchedEffect(Unit) {
        val service = PollingService(dao)
        service.startPolling()
    }

    var serverConfigured by remember { mutableStateOf(false) }

    var serverIp by remember { mutableStateOf("") }
    var serverPort by remember { mutableStateOf("8188") }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (!serverConfigured) {
            ConnectionScreen(
                onConnect = { ip, port ->
                    serverIp = ip
                    serverPort = port
                    serverConfigured = true
                }
            )
        } else {
            MainScreen(serverIp, serverPort, dao)
        }
    }
}


import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import kotlinx.coroutines.launch
import win.qiangge.comfydroid.network.NetworkClient

@Composable
fun ConnectionScreen(onConnect: (String, String) -> Unit) {
    var ip by remember { mutableStateOf("192.168.1.100") }
    var port by remember { mutableStateOf("8188") }
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
        TextField(
            value = ip, 
            onValueChange = { ip = it }, 
            label = { Text("Server IP") },
            enabled = !isLoading
        )
        Spacer(modifier = Modifier.height(8.dp))
        TextField(
            value = port, 
            onValueChange = { port = it }, 
            label = { Text("Port") },
            enabled = !isLoading
        )
        Spacer(modifier = Modifier.height(32.dp))
        
        if (isLoading) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = { 
                    isLoading = true
                    scope.launch {
                        try {
                            NetworkClient.initialize(ip, port)
                            // 尝试调用 API
                            NetworkClient.getApiService().getSystemStats()
                            // 如果成功，回调
                            onConnect(ip, port)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Connect Failed: ${e.message}", Toast.LENGTH_LONG).show()
                        } finally {
                            isLoading = false
                        }
                    }
                }
            ) {
                Text("Connect")
            }
        }
    }
}


import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

// 定义导航项
sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Generator : Screen("generator", "Create", Icons.Default.Home)
    object Library : Screen("library", "Library", Icons.Default.List)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(ip: String, port: String, dao: GenerationDao) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Generator) }
    val workflows = WorkflowRegistry.workflows
    var selectedWorkflow by remember { mutableStateOf<SuperWorkflow?>(null) }
    
    // 从数据库读取历史记录
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
                            // 切换 tab 时重置详情页
                            if (screen == Screen.Library) selectedWorkflow = null
                        }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (currentScreen) {
                Screen.Generator -> {
                    if (selectedWorkflow == null) {
                        LazyColumn {
                            item {
                                Text(
                                    text = "Connected to $ip:$port",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                            items(workflows) { wf ->
                                ListItem(
                                    headlineContent = { Text(wf.name) },
                                    supportingContent = { Text(wf.description) },
                                    modifier = Modifier.clickable { selectedWorkflow = wf }
                                )
                                Divider()
                            }
                        }
                    } else {
                        WorkflowExecutorScreen(
                            workflow = selectedWorkflow!!,
                            onBack = { selectedWorkflow = null },
                            dao = dao
                        )
                    }
                }
                Screen.Library -> {
                    LibraryScreen(
                        results = libraryResults,
                        serverUrl = "http://$ip:$port",
                        onDelete = { /* TODO */ }
                    )
                }
            }
        }
    }
}



import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType

import win.qiangge.comfydroid.network.PromptRequest
import win.qiangge.comfydroid.network.WorkflowEngine
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@Composable
fun WorkflowExecutorScreen(
    workflow: SuperWorkflow, 
    onBack: () -> Unit,
    dao: GenerationDao,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
// ...
                            // 3. 发送请求
                            val response = NetworkClient.getApiService().queuePrompt(PromptRequest(workflowMap))
                            
                            // 4. 写入数据库
                            val newRecord = GenerationResult(
                                promptId = response.prompt_id,
                                workflowName = workflow.name,
                                promptText = prompt,
                                timestamp = System.currentTimeMillis(),
                                outputType = "IMAGE",
                                outputFiles = "", // 暂时为空，等待轮询结果更新
                                status = "PENDING"
                            )
                            dao.insert(newRecord)

                            Toast.makeText(context, "Queued: ${response.prompt_id}", Toast.LENGTH_SHORT).show()
                            
                            // 返回列表页
                            onBack()
                            
                        } catch (e: Exception) {
                            e.printStackTrace()
                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                        } finally {
                            isGenerating = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large
            ) {
                Text("GENERATE", style = MaterialTheme.typography.labelLarge)
            }
        }
        
        Spacer(modifier = Modifier.height(100.dp))
    }
}



