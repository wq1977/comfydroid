package win.qiangge.comfydroid.network

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.*
import okio.ByteString
import win.qiangge.comfydroid.model.GenerationDao
import java.util.concurrent.TimeUnit

class WebSocketManager(private val dao: GenerationDao) {
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // WS 需要长连接
        .pingInterval(30, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO)

    fun connect(ip: String, port: String, clientId: String) {
        val url = "ws://$ip:$port/ws?clientId=$clientId"
        Log.d("ComfyWS", "Connecting to $url")
        
        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("ComfyWS", "Connected")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("ComfyWS", "Closing: $reason")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("ComfyWS", "Failure", t)
            }
        })
    }

    private fun handleMessage(text: String) {
        try {
            val mapType = object : TypeToken<Map<String, Any>>() {}.type
            val msg: Map<String, Any> = gson.fromJson(text, mapType)
            val type = msg["type"] as? String ?: return
            val data = msg["data"] as? Map<String, Any> ?: return

            when (type) {
                "execution_start" -> {
                    // {"prompt_id": "..."}
                    val promptId = data["prompt_id"] as? String
                    updateStatus(promptId, "Started", 0)
                }
                "executing" -> {
                    // {"node": "10", "prompt_id": "..."}
                    val promptId = data["prompt_id"] as? String
                    val node = data["node"] as? String
                    // 这里我们不知道 Node ID 对应的名字，除非我们有 workflow 映射
                    // 暂时显示 "Processing Node $node"
                    if (node != null) {
                        updateStatus(promptId, "Node $node", 0)
                    }
                }
                "progress" -> {
                    // {"value": 1, "max": 20, "prompt_id": "..."}
                    val promptId = data["prompt_id"] as? String
                    val value = (data["value"] as? Number)?.toInt() ?: 0
                    val max = (data["max"] as? Number)?.toInt() ?: 1
                    
                    // 计算百分比
                    val progress = (value.toFloat() / max.toFloat() * 100).toInt()
                    updateStatus(promptId, "Sampling", progress, value, max)
                }
                "executed" -> {
                    // 节点完成
                }
                "execution_success" -> {
                    // 整个流程完成，PollingService 会处理结果抓取，这里可以更新状态为 Finalizing
                    val promptId = data["prompt_id"] as? String
                    updateStatus(promptId, "Finalizing", 100)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateStatus(promptId: String?, statusMsg: String, progress: Int, currentStep: Int = 0, maxSteps: Int = 0) {
        if (promptId == null) return
        scope.launch {
            val task = dao.getByPromptId(promptId)
            if (task != null && task.status == "PENDING") {
                val updated = task.copy(
                    nodeStatus = statusMsg,
                    progress = progress,
                    currentStep = currentStep,
                    maxSteps = maxSteps
                )
                dao.update(updated)
            }
        }
    }
    
    fun disconnect() {
        webSocket?.close(1000, "App closed")
    }
}
