package win.qiangge.comfydroid

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import win.qiangge.comfydroid.model.GenerationResult

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    results: List<GenerationResult>,
    serverUrl: String,
    onDelete: (List<Int>) -> Unit,
    onImageClick: (GenerationResult) -> Unit
) {
    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<Int>() }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(isSelectionMode) {
        if (!isSelectionMode) selectedIds.clear()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (results.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No generations yet. Go create something!", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            Column {
                if (isSelectionMode) {
                    TopAppBar(
                        title = { Text("${selectedIds.size} Selected") },
                        navigationIcon = {
                            IconButton(onClick = { isSelectionMode = false }) {
                                Icon(Icons.Default.Close, "Close")
                            }
                        },
                        actions = {
                            TextButton(onClick = {
                                if (selectedIds.size == results.size) {
                                    selectedIds.clear()
                                } else {
                                    selectedIds.clear()
                                    selectedIds.addAll(results.map { it.id })
                                }
                            }) {
                                Text(if (selectedIds.size == results.size) "Deselect All" else "Select All")
                            }
                        },
                        colors = TopAppBarDefaults.smallTopAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    )
                }

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    contentPadding = PaddingValues(
                        start = 16.dp, 
                        end = 16.dp, 
                        top = 16.dp, 
                        bottom = if (isSelectionMode) 88.dp else 16.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(results) { result ->
                        val selected = selectedIds.contains(result.id)
                        ResultCard(
                            result = result, 
                            serverUrl = serverUrl,
                            isSelectionMode = isSelectionMode,
                            isSelected = selected,
                            onClick = {
                                if (isSelectionMode) {
                                    if (selected) selectedIds.remove(result.id) else selectedIds.add(result.id)
                                } else {
                                    onImageClick(result)
                                }
                            },
                            onLongClick = {
                                if (!isSelectionMode) {
                                    isSelectionMode = true
                                    selectedIds.add(result.id)
                                }
                            }
                        )
                    }
                }
            }
        }

        if (isSelectionMode && selectedIds.isNotEmpty()) {
            FloatingActionButton(
                onClick = { showDeleteConfirm = true },
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp)
            ) {
                Icon(Icons.Default.Delete, "Delete")
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete ${selectedIds.size} items?") },
            text = { Text("This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(selectedIds.toList())
                        isSelectionMode = false
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ResultCard(
    result: GenerationResult, 
    serverUrl: String,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val firstFile = result.outputFiles.split(",").firstOrNull()?.trim()
    val imageUrl = if (!firstFile.isNullOrEmpty()) "$serverUrl/view?filename=$firstFile&type=output" else null

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.8f)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .then(if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CardDefaults.shape) else Modifier),
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
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (result.status == "PENDING") {
                        if (result.progress > 0) {
                            LinearProgressIndicator(progress = result.progress / 100f, modifier = Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("${result.progress}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        } else {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(result.nodeStatus, style = MaterialTheme.typography.bodySmall, maxLines = 2, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text("Failed", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            
            // 在右上角显示流程名称
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                color = Color.Black.copy(alpha = 0.5f),
                shape = MaterialTheme.shapes.extraSmall
            ) {
                Text(
                    text = result.workflowName,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
            }
            
            if (isSelectionMode) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent)
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(24.dp)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.4f), 
                                CircleShape
                            )
                            .border(1.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            } else {
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(text = result.promptText, maxLines = 2, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
