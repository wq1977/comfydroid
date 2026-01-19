package win.qiangge.comfydroid

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.exifinterface.media.ExifInterface
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import me.saket.telephoto.zoomable.coil.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import win.qiangge.comfydroid.model.*
import win.qiangge.comfydroid.network.NetworkClient
import win.qiangge.comfydroid.network.PromptRequest
import win.qiangge.comfydroid.network.WorkflowEngine
import win.qiangge.comfydroid.network.WebSocketManager
import win.qiangge.comfydroid.service.PollingService
import java.io.File
import java.util.UUID
import kotlin.math.min

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
    var serverIp by remember { mutableStateOf(prefs.getString("last_ip", "") ?: "") }
    var serverPort by remember { mutableStateOf(prefs.getString("last_port", "8188") ?: "8188") }
    
    // 如果有保存的 IP，默认认为已配置，直接进入 MainScreen
    // 我们会在后台静默校验，如果失败再退回
    var serverConfigured by remember { mutableStateOf(serverIp.isNotEmpty()) }
    
    var clientId by remember { mutableStateOf(prefs.getString("client_id", "") ?: "") }
    if (clientId.isEmpty()) {
        clientId = UUID.randomUUID().toString()
        prefs.edit().putString("client_id", clientId).apply()
    }

    val wsManager = remember { WebSocketManager(dao) }
    
    // 统一处理初始化连接、校验和 WebSocket 启动
    // 将 clientId 加入 keys 确保其更新时重新连接
    LaunchedEffect(serverConfigured, serverIp, serverPort, clientId) {
        if (serverConfigured && serverIp.isNotEmpty()) {
            try {
                // NetworkClient 初始化是幂等的
                NetworkClient.initialize(serverIp, serverPort)
                // 静默校验，如果失败则让用户重新连接
                // NetworkClient.getApiService().getSystemStats()
                wsManager.connect(serverIp, serverPort, clientId)
            } catch (e: Exception) {
                Log.e("ComfyDroid", "Auto-connect failed", e)
                // 暂时不强制弹回 ConnectionScreen，除非是手动连接失败
            }
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
            MainScreen(serverIp, serverPort, clientId, dao, wsManager)
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
fun MainScreen(ip: String, port: String, clientId: String, dao: GenerationDao, wsManager: WebSocketManager) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Generator) }
    val workflows = WorkflowRegistry.workflows
    var selectedWorkflow by remember { mutableStateOf<SuperWorkflow?>(null) }
    val libraryResults by dao.getAll().collectAsState(initial = emptyList())
    var fullScreenIndex by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()

    // 处理返回键：如果在 Library 界面且没有全屏预览，返回 Generator
    BackHandler(enabled = currentScreen == Screen.Library && fullScreenIndex == null) {
        currentScreen = Screen.Generator
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                if (fullScreenIndex == null) {
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
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                when (currentScreen) {
                    Screen.Generator -> {
                        if (selectedWorkflow == null) {
                            LazyColumn(
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                item { Text("Workflows", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = 8.dp)) }
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
                                clientId = clientId,
                                serverUrl = "http://$ip:$port",
                                wsManager = wsManager,
                                serverIp = ip,
                                serverPort = port
                            )
                        }
                    }
                    Screen.Library -> {
                        LibraryScreen(
                            results = libraryResults, 
                            serverUrl = "http://$ip:$port", 
                            onDelete = { ids -> 
                                scope.launch {
                                    dao.deleteByIds(ids)
                                }
                            },
                            onImageClick = { result -> 
                                fullScreenIndex = libraryResults.indexOf(result)
                            }
                        )
                    }
                }
            }
        }
        if (fullScreenIndex != null) {
            FullScreenImageViewer(
                results = libraryResults,
                initialIndex = fullScreenIndex!!,
                serverUrl = "http://$ip:$port", 
                onDismiss = { fullScreenIndex = null }
            )
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
    serverUrl: String,
    wsManager: WebSocketManager,
    serverIp: String,
    serverPort: String,
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
                    is NumberInput -> {
                        if (input.id == "seed") {
                            // 初始打开时就给一个随机种子
                            put(input.id, (0..Long.MAX_VALUE).random())
                        } else {
                            put(input.id, input.defaultValue)
                        }
                    }
                    is ImageArrayInput -> put(input.id, emptyList<String>())
                    else -> {} 
                }
            }
        }
    }
    
    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                    
                    var rotation = 0f
                    context.contentResolver.openInputStream(uri)?.use {
                        val exif = ExifInterface(it)
                        val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                        rotation = when (orientation) {
                            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                            else -> 0f
                        }
                    }
                    
                    var w = options.outWidth
                    var h = options.outHeight
                    if (rotation == 90f || rotation == 270f) {
                        val temp = w; w = h; h = temp
                    }

                    var finalBitmap: android.graphics.Bitmap? = null
                    val maxDim = 1280
                    
                    if (w > 0 && h > 0) {
                        val originalBitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                        if (originalBitmap != null) {
                            val matrix = Matrix()
                            matrix.postRotate(rotation)
                            
                            var targetW = w
                            var targetH = h
                            if (w > maxDim || h > maxDim) {
                                val ratio = min(maxDim.toFloat() / w, maxDim.toFloat() / h)
                                targetW = (w * ratio).toInt()
                                targetH = (h * ratio).toInt()
                            }
                            
                            targetW = (targetW / 16) * 16
                            targetH = (targetH / 16) * 16
                            
                            val rotatedBitmap = android.graphics.Bitmap.createBitmap(originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true)
                            finalBitmap = android.graphics.Bitmap.createScaledBitmap(rotatedBitmap, targetW, targetH, true)
                            
                            if (rotatedBitmap != originalBitmap) originalBitmap.recycle()
                            if (finalBitmap != rotatedBitmap) rotatedBitmap.recycle()
                            
                            inputStates["width"] = targetW
                            inputStates["height"] = targetH
                        }
                    }

                    val tempFile = File(context.cacheDir, "upload_${System.currentTimeMillis()}.png")
                    if (finalBitmap != null) {
                        tempFile.outputStream().use { out -> finalBitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out) }
                        finalBitmap.recycle()
                    } else {
                        context.contentResolver.openInputStream(uri)?.use { input -> tempFile.outputStream().use { output -> input.copyTo(output) } }
                    }

                    val requestFile = tempFile.asRequestBody("image/png".toMediaTypeOrNull())
                    val body = MultipartBody.Part.createFormData("image", tempFile.name, requestFile)
                    val response = NetworkClient.getApiService().uploadImage(body)
                    val currentList = (inputStates["ref_images"] as? List<*>)?.filterIsInstance<String>()?.toMutableList() ?: mutableListOf()
                    currentList.add(response.name)
                    inputStates["ref_images"] = currentList
                    Toast.makeText(context, "Uploaded: ${response.name}", Toast.LENGTH_SHORT).show()
                    
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(context, "Upload Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    var isGenerating by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val basicInputs = workflow.inputs.filter { it.id == "prompt" || it is ImageArrayInput }
    val advancedInputs = workflow.inputs.filter { it.id != "prompt" && it !is ImageArrayInput }
    var showAdvanced by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize().padding(16.dp).verticalScroll(scrollState)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onBack) { Text("Back") }
            Spacer(modifier = Modifier.width(16.dp))
            Text(workflow.name, style = MaterialTheme.typography.titleLarge)
        }
        Spacer(modifier = Modifier.height(16.dp))
        basicInputs.forEach { input ->
            if (input is ImageArrayInput) {
                val images = (inputStates[input.id] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(input.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
                        items(images) { filename ->
                            Box(modifier = Modifier.size(100.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceVariant)) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data("$serverUrl/view?filename=$filename&type=input")
                                        .crossfade(true).build(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                IconButton(
                                    onClick = {
                                        val newList = images.toMutableList()
                                        newList.remove(filename)
                                        inputStates[input.id] = newList
                                    },
                                    modifier = Modifier.align(Alignment.TopEnd).size(24.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                ) {
                                    Icon(Icons.Default.Close, "Remove", tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        if (images.size < input.maxCount) {
                            item {
                                Box(
                                    modifier = Modifier.size(100.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { imagePickerLauncher.launch("image/*") },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Add, "Add Image", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            } else {
                InputControl(input, inputStates, isGenerating)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        if (advancedInputs.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth().clickable { showAdvanced = !showAdvanced },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Advanced Settings", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Icon(if (showAdvanced) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null)
                }
            }
            AnimatedVisibility(visible = showAdvanced, enter = expandVertically(), exit = shrinkVertically()) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    AspectRatioSelector(onSelect = { w, h -> inputStates["width"] = w; inputStates["height"] = h })
                    Spacer(modifier = Modifier.height(8.dp))
                    advancedInputs.forEach { InputControl(it, inputStates, isGenerating) }
                }
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
                                        // 每次发起任务都重新建立 WebSocket 连接，确保其可用性
                                        // 甚至可以新建一个 clientId
                                        val currentClientId = UUID.randomUUID().toString()
                                        wsManager.connect(serverIp, serverPort, currentClientId)
                                        
                                        // 等待连接成功
                                        val connected = wsManager.waitForConnection(5000)
                                        if (!connected) {
                                            throw Exception("Failed to establish WebSocket connection")
                                        }

                                        val promptText = inputStates["prompt"] as? String ?: ""
                                        val workflowJson = engine.buildWorkflow(workflow.id, inputStates)
                                        val finalJson = "{\"client_id\": \"$currentClientId\", \"prompt\": $workflowJson}"
                
                        val mediaType = "application/json".toMediaTypeOrNull()
                        val body = RequestBody.create(mediaType, finalJson)
                        val response = NetworkClient.getApiService().queuePromptRaw(body)
                        dao.insert(GenerationResult(promptId = response.prompt_id, workflowName = workflow.name,
                            promptText = promptText, timestamp = System.currentTimeMillis(), outputType = "IMAGE", outputFiles = "", status = "PENDING"))
                        onSuccess()
                    } catch (e: Exception) {
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
            TextField(value = inputStates[input.id] as? String ?: "", onValueChange = { inputStates[input.id] = it },
                label = { Text(input.label) }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                minLines = if (input.multiline) 3 else 1, enabled = !isGenerating)
        }
        is NumberInput -> {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                TextField(value = inputStates[input.id]?.toString() ?: "", 
                    onValueChange = { val filtered = it.filter { char -> char.isDigit() || char == '.' }
                        inputStates[input.id] = if (input.isInteger) filtered.toLongOrNull() ?: 0L else filtered.toFloatOrNull() ?: 0f },
                    label = { Text(input.label) }, modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), enabled = !isGenerating)
                if (input.id == "seed") {
                    IconButton(onClick = { inputStates[input.id] = (0..Long.MAX_VALUE).random() }) { Icon(Icons.Default.Refresh, null) }
                }
            }
        }
        is ImageArrayInput -> {} 
        is ImageInput -> { Text("Single Image Input (Not implemented)") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AspectRatioSelector(onSelect: (Int, Int) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        AssistChip(onClick = { onSelect(1024, 1024) }, label = { Text("1:1") })
        AssistChip(onClick = { onSelect(832, 1216) }, label = { Text("Port.") })
        AssistChip(onClick = { onSelect(1216, 832) }, label = { Text("Land.") })
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun FullScreenImageViewer(results: List<GenerationResult>, initialIndex: Int, serverUrl: String, onDismiss: () -> Unit) {
    // 拦截返回键
    BackHandler(onBack = onDismiss)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var showSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(initialPage = initialIndex) {
        results.size
    }

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
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.pager.HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondBoundsPageCount = 1,
            userScrollEnabled = true 
        ) { page ->
            val result = results[page]
            val firstFile = result.outputFiles.split(",").firstOrNull()?.trim()
            val imageUrl = if (!firstFile.isNullOrEmpty()) "$serverUrl/view?filename=$firstFile&type=output" else null

            if (imageUrl != null) {
                val zoomableState = rememberZoomableImageState()
                ZoomableAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    state = zoomableState,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    onClick = { onDismiss() },
                    onLongClick = { showSheet = true }
                )
            }
        }

        if (showSheet) {
            val currentResult = results[pagerState.currentPage]
            val currentFirstFile = currentResult.outputFiles.split(",").firstOrNull()?.trim()
            val currentImageUrl = if (!currentFirstFile.isNullOrEmpty()) "$serverUrl/view?filename=$currentFirstFile&type=output" else null

            ModalBottomSheet(
                onDismissRequest = { showSheet = false },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp)
                ) {
                    ListItem(
                        headlineContent = { Text("Save to Gallery") },
                        leadingContent = { Icon(Icons.Default.Add, contentDescription = null) },
                        modifier = Modifier.clickable {
                            showSheet = false
                            if (currentImageUrl != null) {
                                scope.launch {
                                    saveToGallery(context, currentImageUrl, currentFirstFile ?: "image.png")
                                }
                            }
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Cancel") },
                        leadingContent = { Icon(Icons.Default.Close, contentDescription = null) },
                        modifier = Modifier.clickable { showSheet = false }
                    )
                }
            }
        }
    }
}

suspend fun saveToGallery(context: Context, imageUrl: String, filename: String) {
    try {
        val request = ImageRequest.Builder(context).data(imageUrl).build()
        val bitmap = (context.imageLoader.execute(request).drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
        
        if (bitmap != null) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "ComfyDroid_$filename")
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ComfyDroid")
            }
            
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                }
                Toast.makeText(context, "Saved to Gallery", Toast.LENGTH_SHORT).show()
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Save Failed: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
