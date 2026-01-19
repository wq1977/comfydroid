package win.qiangge.comfydroid.network

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import okhttp3.*
import win.qiangge.comfydroid.model.GenerationDao
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap

enum class ConnectionStatus {
    DISCONNECTED, CONNECTING, CONNECTED, ERROR
}

class WebSocketManager(private val dao: GenerationDao) {
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO)
    
    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus

    // 记录每个任务已执行的节点数
    private val taskNodeCounts = ConcurrentHashMap<String, Int>()

    fun connect(ip: String, port: String, clientId: String) {
        // 如果已经是连接状态，且参数相同，可以考虑跳过，但为了稳定性，用户要求每次发起都要确保连接
        // 我们先关闭之前的
        if (_connectionStatus.value == ConnectionStatus.CONNECTED) {
            webSocket?.close(1000, "Reconnecting")
        }
        
        _connectionStatus.value = ConnectionStatus.CONNECTING
        
        val url = "ws://$ip:$port/ws?clientId=$clientId"
        Log.d("ComfyWS", "Attempting connection to: $url")
        
        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("ComfyWS", "CONNECTED SUCCESSFULLY")
                _connectionStatus.value = ConnectionStatus.CONNECTED
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                // 强制打印所有收到的消息，用于排查进度不显示的问题
                Log.e("ComfyWS_DEBUG", "RAW MSG: $text")
                handleMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("ComfyWS", "Closing ($code): $reason")
                _connectionStatus.value = ConnectionStatus.DISCONNECTED
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("ComfyWS", "Closed: $reason")
                _connectionStatus.value = ConnectionStatus.DISCONNECTED
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("ComfyWS", "CONNECTION FAILURE", t)
                _connectionStatus.value = ConnectionStatus.ERROR
            }
        })
    }

    suspend fun waitForConnection(timeoutMs: Long = 5000): Boolean {
        return try {
            withTimeout(timeoutMs) {
                _connectionStatus.first { it == ConnectionStatus.CONNECTED }
                true
            }
        } catch (e: Exception) {
            Log.e("ComfyWS", "Timeout waiting for connection")
            false
        }
    }

    private fun handleMessage(text: String) {
        try {
            val mapType = object : TypeToken<Map<String, Any>>() {}.type
            val msg: Map<String, Any> = gson.fromJson(text, mapType)
            val type = msg["type"] as? String ?: return
            val data = msg["data"] as? Map<String, Any> ?: return

            when (type) {
                "execution_start" -> {
                    val promptId = data["prompt_id"] as? String
                    if (promptId != null) {
                        taskNodeCounts[promptId] = 0
                        updateStatus(promptId, "Started", 0)
                    }
                }
                "executing" -> {
                    val promptId = data["prompt_id"] as? String
                    val node = data["node"] as? String // Node ID
                    if (promptId != null) {
                        val count = taskNodeCounts.getOrDefault(promptId, 0) + 1
                        taskNodeCounts[promptId] = count
                        
                        // 这是一个新的节点开始
                        val status = if (node != null) "Node $node (#$count)" else "Processing (#$count)"
                        // 传入 -1 表示维持之前的进度百分比，不重置为 0
                        updateStatus(promptId, status, -1)
                    }
                }
                "progress" -> {
                    val promptId = data["prompt_id"] as? String
                    val value = (data["value"] as? Number)?.toInt() ?: 0
                    val max = (data["max"] as? Number)?.toInt() ?: 1
                    
                    // 计算节点内百分比
                    val progress = (value.toFloat() / max.toFloat() * 100).toInt()
                    
                    if (promptId != null) {
                        val count = taskNodeCounts.getOrDefault(promptId, 0)
                        val status = "Sampling ($value/$max) - Node #$count"
                        updateStatus(promptId, status, progress, value, max)
                    }
                }
                "execution_success" -> {
                    val promptId = data["prompt_id"] as? String
                    taskNodeCounts.remove(promptId)
                    updateStatus(promptId, "Finalizing...", 100)
                }
            }
        } catch (e: Exception) {
            Log.e("ComfyWS", "Error parsing message", e)
        }
    }

    private fun updateStatus(promptId: String?, statusMsg: String, progress: Int, currentStep: Int = 0, maxSteps: Int = 0) {
        if (promptId == null) return
        scope.launch {
            val task = dao.getByPromptId(promptId)
            if (task != null && task.status == "PENDING") {
                val updated = task.copy(
                    nodeStatus = statusMsg,
                    progress = if (progress == -1) task.progress else progress,
                    currentStep = if (currentStep > 0) currentStep else task.currentStep,
                    maxSteps = if (maxSteps > 0) maxSteps else task.maxSteps
                )
                dao.update(updated)
            }
        }
    }
    
    fun disconnect() {
        webSocket?.close(1000, "App closed")
    }
}
