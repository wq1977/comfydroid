package win.qiangge.comfydroid.network

import android.content.Context
import org.json.JSONObject

/**
 * 核心引擎：使用原生 JSONObject 确保类型安全
 */
class WorkflowEngine(private val context: Context) {

    fun getRawTemplate(): String {
        return context.assets.open("templates/flux_base.json").bufferedReader().use { it.readText() }
    }

    fun buildFluxWorkflow(
        prompt: String,
        seed: Long,
        imagePaths: List<String> = emptyList()
    ): String {
        // 1. 加载基础模板
        val jsonString = getRawTemplate()
        val workflow = JSONObject(jsonString)

        // 2. 修改基础参数 (使用 JSONObject 直接修改，保留类型)
        
        // 修改 75:74 (CLIPTextEncode) 的 text
        if (workflow.has("75:74")) {
            val nodePositive = workflow.getJSONObject("75:74")
            val inputs = nodePositive.getJSONObject("inputs")
            inputs.put("text", prompt)
        }
        
        // 修改 75:73 (RandomNoise) 的 noise_seed
        if (workflow.has("75:73")) {
            val nodeNoise = workflow.getJSONObject("75:73")
            val inputs = nodeNoise.getJSONObject("inputs")
            val finalSeed = if (seed <= 0) (0..Long.MAX_VALUE).random() else seed
            inputs.put("noise_seed", finalSeed)
        }

        // 3. 处理参数内联 (断开 PrimitiveInt 连接，直接填入值)
        
        // Flux2Scheduler (75:62)
        if (workflow.has("75:62")) {
            val inputs = workflow.getJSONObject("75:62").getJSONObject("inputs")
            inputs.put("steps", 4)
            inputs.put("width", 1024)
            inputs.put("height", 1024)
        }

        // CFGGuider (75:63)
        if (workflow.has("75:63")) {
            val inputs = workflow.getJSONObject("75:63").getJSONObject("inputs")
            inputs.put("cfg", 5.0)
        }

        // EmptyFlux2LatentImage (75:66)
        if (workflow.has("75:66")) {
            val inputs = workflow.getJSONObject("75:66").getJSONObject("inputs")
            inputs.put("width", 1024)
            inputs.put("height", 1024)
        }

        // 4. TODO: 处理参考图 (我们先验证文本生成的动态参数)

        return workflow.toString()
    }
}