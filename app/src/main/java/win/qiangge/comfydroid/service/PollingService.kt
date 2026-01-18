package win.qiangge.comfydroid.service

import android.util.Log
import kotlinx.coroutines.*
import win.qiangge.comfydroid.model.GenerationDao
import win.qiangge.comfydroid.network.NetworkClient
import com.google.gson.Gson
import com.google.gson.internal.LinkedTreeMap

class PollingService(private val dao: GenerationDao) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isPolling = false

    fun startPolling() {
        if (isPolling) return
        isPolling = true
        scope.launch {
            while (isActive) {
                try {
                    val pendingTasks = dao.getPendingResults()
                    if (pendingTasks.isNotEmpty()) {
                        Log.d("PollingService", "Checking ${pendingTasks.size} pending tasks...")
                        
                        pendingTasks.forEach { task ->
                            try {
                                checkTaskStatus(task)
                            } catch (e: Exception) {
                                Log.e("PollingService", "Failed to check task ${task.promptId}", e)
                            }
                        }
                    }
                    delay(3000) // 每 3 秒轮询
                } catch (e: Exception) {
                    e.printStackTrace()
                    delay(5000)
                }
            }
        }
    }

    private suspend fun checkTaskStatus(task: win.qiangge.comfydroid.model.GenerationResult) {
        val api = NetworkClient.getApiService()
        // ComfyUI history API 返回 Map<String, Any>
        // Key 是 prompt_id
        val history = api.getHistory(task.promptId)
        
        if (history.containsKey(task.promptId)) {
            // 任务已完成
            val taskData = history[task.promptId] as? Map<String, Any>
            val outputs = taskData?.get("outputs") as? Map<String, Any>
            
            if (outputs != null) {
                // 寻找包含 images 的节点
                val imageFiles = mutableListOf<String>()
                
                outputs.values.forEach { nodeOutput ->
                    val outputMap = nodeOutput as? Map<String, Any>
                    val images = outputMap?.get("images") as? List<Map<String, Any>>
                    images?.forEach { img ->
                        val filename = img["filename"] as? String
                        if (filename != null) {
                            imageFiles.add(filename)
                        }
                    }
                }

                if (imageFiles.isNotEmpty()) {
                    // 更新数据库
                    val updated = task.copy(
                        status = "COMPLETED",
                        outputFiles = imageFiles.joinToString(",")
                    )
                    dao.update(updated)
                    Log.d("PollingService", "Task ${task.promptId} completed with ${imageFiles.size} images")
                }
            }
        }
    }
}