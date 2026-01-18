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
        inputs: Map<String, Any>
    ): String {
        val prompt = inputs["prompt"] as? String ?: ""
        val seedRaw = inputs["seed"]
        val seed = when(seedRaw) {
            is Long -> seedRaw
            is Float -> seedRaw.toLong()
            is Int -> seedRaw.toLong()
            else -> 0L
        }
        
        // 1. 加载基础模板
        val jsonString = getRawTemplate()
        val workflow = JSONObject(jsonString)

        // 2. 修改基础参数 (使用 JSONObject 直接修改，保留类型)
        
        // 修改 75:74 (CLIPTextEncode) 的 text
        if (workflow.has("75:74")) {
            val nodePositive = workflow.getJSONObject("75:74")
            val nodeInputs = nodePositive.getJSONObject("inputs")
            nodeInputs.put("text", prompt)
        }
        
        // 修改 75:73 (RandomNoise) 的 noise_seed
        if (workflow.has("75:73")) {
            val nodeNoise = workflow.getJSONObject("75:73")
            val nodeInputs = nodeNoise.getJSONObject("inputs")
            val finalSeed = if (seed <= 0) (0..Long.MAX_VALUE).random() else seed
            nodeInputs.put("noise_seed", finalSeed)
        }

        // 3. 处理参数内联 (从 inputs Map 读取)
        val width = (inputs["width"] as? Number)?.toInt() ?: 1024
        val height = (inputs["height"] as? Number)?.toInt() ?: 1024
        val steps = (inputs["steps"] as? Number)?.toInt() ?: 4
        val cfg = (inputs["cfg"] as? Number)?.toDouble() ?: 1.0

        // Flux2Scheduler (75:62): steps, width, height
        if (workflow.has("75:62")) {
            val nodeInputs = workflow.getJSONObject("75:62").getJSONObject("inputs")
            nodeInputs.put("steps", steps)
            nodeInputs.put("width", width)
            nodeInputs.put("height", height)
        }

        // CFGGuider (75:63): cfg
        if (workflow.has("75:63")) {
            val nodeInputs = workflow.getJSONObject("75:63").getJSONObject("inputs")
            nodeInputs.put("cfg", cfg)
        }

        // EmptyFlux2LatentImage (75:66): width, height
        if (workflow.has("75:66")) {
            val nodeInputs = workflow.getJSONObject("75:66").getJSONObject("inputs")
            nodeInputs.put("width", width)
            nodeInputs.put("height", height)
        }

        // 4. TODO: 处理参考图 (我们先验证文本生成的动态参数)

        return workflow.toString()
    }

}