package win.qiangge.comfydroid

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import win.qiangge.comfydroid.model.GenerationResult

@Composable
fun LibraryScreen(
    results: List<GenerationResult>,
    serverUrl: String,
    onDelete: (GenerationResult) -> Unit
) {
    // 状态：当前选中的图片用于全屏预览
    var selectedResult by remember { mutableStateOf<GenerationResult?>(null) }

    if (results.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No generations yet. Go create something!", style = MaterialTheme.typography.bodyLarge)
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(results) { result ->
                ResultCard(
                    result = result, 
                    serverUrl = serverUrl, 
                    onDelete = onDelete,
                    onClick = { selectedResult = result } // 点击打开全屏
                )
            }
        }
    }

    // 全屏预览 Dialog
    if (selectedResult != null) {
        FullScreenImageDialog(
            result = selectedResult!!,
            serverUrl = serverUrl,
            onDismiss = { selectedResult = null }
        )
    }
}

@Composable
fun FullScreenImageDialog(
    result: GenerationResult,
    serverUrl: String,
    onDismiss: () -> Unit
) {
    val firstFile = result.outputFiles.split(",").firstOrNull()?.trim()
    val imageUrl = if (!firstFile.isNullOrEmpty()) "$serverUrl/view?filename=$firstFile&type=output" else null

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false, // 允许全屏宽度
            decorFitsSystemWindows = false   // 延伸到状态栏
        )
    ) {
        // 使用 Box 充满屏幕背景，点击背景关闭
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            if (imageUrl != null) {
                // 简单的缩放实现
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
                                scale = (scale * zoom).coerceIn(1f, 3f) // 限制缩放 1x - 3x
                                offset = if (scale == 1f) androidx.compose.ui.geometry.Offset.Zero else offset + pan
                            }
                        }
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        ),
                    contentScale = ContentScale.Fit // 智能适配：完整显示
                )
            } else {
                Text("Invalid Image", color = Color.White)
            }
        }
    }
}

@Composable
fun ResultCard(
    result: GenerationResult, 
    serverUrl: String,
    onDelete: (GenerationResult) -> Unit,
    onClick: () -> Unit = {}
) {
    val firstFile = result.outputFiles.split(",").firstOrNull()?.trim()
    val imageUrl = if (!firstFile.isNullOrEmpty()) "$serverUrl/view?filename=$firstFile&type=output" else null

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.8f)
            .clickable { if (result.status == "COMPLETED") onClick() }, // 只有完成后才可点击
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (result.status == "COMPLETED" && imageUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = result.promptText,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                // Pending / Processing State
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (result.status == "PENDING") {
                        if (result.progress > 0) {
                            LinearProgressIndicator(
                                progress = result.progress / 100f,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "${result.progress}%", 
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            result.nodeStatus, 
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text("Failed", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            
            // Text overlay at bottom
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        text = result.promptText,
                        maxLines = 2,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}