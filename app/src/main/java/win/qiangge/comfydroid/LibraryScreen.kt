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
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment

@Composable
fun LibraryScreen(
    results: List<GenerationResult>,
    serverUrl: String,
    onDelete: (GenerationResult) -> Unit
) {
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
    val firstFile = result.outputFiles.split(",").firstOrNull()?.trim()
    val imageUrl = if (!firstFile.isNullOrEmpty()) "$serverUrl/view?filename=$firstFile&type=output" else null

    Card(
        modifier = Modifier.fillMaxWidth().aspectRatio(0.8f), // Slightly taller card to fit status
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
