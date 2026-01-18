package win.qiangge.comfydroid.network

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.*
import win.qiangge.comfydroid.model.GenerationDao
import java.util.concurrent.TimeUnit

class WebSocketManager(private val dao: GenerationDao) {
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO)

    fun connect(ip: String, port: String, clientId: String) {
        val url = "ws://$ip:$port/ws?clientId=$clientId"
        Log.d("ComfyWS", "Attempting connection to: $url")
        
        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("ComfyWS", "CONNECTED SUCCESSFULLY")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                // 打印每一条收到的消息
                Log.d("ComfyWS", "MSG RECEIVED: $text")
                handleMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("ComfyWS", "Closing ($code): $reason")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("ComfyWS", "CONNECTION FAILURE", t)
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
                    val promptId = data["prompt_id"] as? String
                    Log.d("ComfyWS", "Processing started: $promptId")
                    updateStatus(promptId, "Executing...", 1)
                }
                "executing" -> {
                    val promptId = data["prompt_id"] as? String
                    val node = data["node"] as? String
                    Log.d("ComfyWS", "Node executing: $node for $promptId")
                    if (node != null) {
                        updateStatus(promptId, "Node $node", -1) // -1 表示维持当前进度
                    }
                }
                "progress" -> {
                    val promptId = data["prompt_id"] as? String
                    val value = (data["value"] as? Number)?.toInt() ?: 0
                    val max = (data["max"] as? Number)?.toInt() ?: 1
                    val progress = (value.toFloat() / max.toFloat() * 100).toInt()
                    Log.d("ComfyWS", "Progress: $progress% ($value/$max) for $promptId")
                    updateStatus(promptId, "Sampling...", progress, value, max)
                }
                "execution_success" -> {
                    val promptId = data["prompt_id"] as? String
                    Log.d("ComfyWS", "Success: $promptId")
                    updateStatus(promptId, "Done", 100)
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
            if (task != null) {
                // 如果是进度更新，保留 1-99 之间的值
                val finalProgress = if (progress == -1) task.progress else progress
                val updated = task.copy(
                    nodeStatus = statusMsg,
                    progress = finalProgress,
                    currentStep = if (currentStep > 0) currentStep else task.currentStep,
                    maxSteps = if (maxSteps > 0) maxSteps else task.maxSteps
                )
                dao.update(updated)
                Log.d("ComfyWS", "DB UPDATED for $promptId: $statusMsg $finalProgress%")
            } else {
                Log.w("ComfyWS", "Task not found in DB for promptId: $promptId")
            }
        }
    }
}