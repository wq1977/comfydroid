package win.qiangge.comfydroid

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import win.qiangge.comfydroid.model.GenerationResult
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun LibraryScreen(
    results: List<GenerationResult>,
    serverUrl: String, // 用于拼接图片完整 URL
    onDelete: (GenerationResult) -> Unit
) {
    if (results.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Text("No generations yet. Go create something!", style = MaterialTheme.typography.bodyLarge)
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(results) { result ->
                ResultCard(result, serverUrl, onDelete)
            }
        }
    }
}

@Composable
fun ResultCard(
    result: GenerationResult, 
    serverUrl: String,
    onDelete: (GenerationResult) -> Unit
) {
    // 假设 outputFiles 是简单的逗号分隔文件名 (真实场景需要 JSON 解析)
    val firstFile = result.outputFiles.split(",").firstOrNull()?.trim()
    val imageUrl = if (!firstFile.isNullOrEmpty()) "$serverUrl/view?filename=$firstFile" else null

    Card(
        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = result.promptText,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            
            // 简单的底部遮罩显示 Prompt 摘要
            Surface(
                modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter).fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
            ) {
                Text(
                    text = result.promptText,
                    maxLines = 1,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(4.dp)
                )
            }
        }
    }
}
