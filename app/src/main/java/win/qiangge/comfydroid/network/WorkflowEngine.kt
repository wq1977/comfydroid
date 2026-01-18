package win.qiangge.comfydroid.network

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 核心引擎：负责将用户输入转化为 ComfyUI API JSON
 */
class WorkflowEngine(private val context: Context) {
    private val gson = Gson()

    fun buildFluxWorkflow(
        prompt: String,
        seed: Long,
        imagePaths: List<String> = emptyList()
    ): String {
        // 1. 加载基础模板 (turbo-text)
        val jsonString = context.assets.open("templates/flux_base.json").bufferedReader().use { it.readText() }
        val workflow: MutableMap<String, MutableMap<String, Any>> = gson.fromJson(
            jsonString, 
            object : TypeToken<MutableMap<String, MutableMap<String, Any>>>() {}.type
        )

        // 2. 修改基础参数
        // 对应 turbo-text.json 中的 76 号节点 (Prompt)
        (workflow["76"]?.get("inputs") as? MutableMap<String, Any>)?.put("value", prompt)
        
        // 对应 75:73 号节点 (RandomNoise)
        val finalSeed = if (seed <= 0) (0..Long.MAX_VALUE).random() else seed
        (workflow["75:73"]?.get("inputs") as? MutableMap<String, Any>)?.put("noise_seed", finalSeed)

        // 3. 处理参考图 (如有)
        if (imagePaths.isNotEmpty()) {
            applyReferenceImages(workflow, imagePaths)
        }

        return gson.toJson(workflow)
    }

    private fun applyReferenceImages(
        workflow: MutableMap<String, MutableMap<String, Any>>, 
        imagePaths: List<String>
    ) {
        // 这部分逻辑较为复杂，需要动态修改 75:63 (CFGGuider) 的 positive 输入
        // 原本 75:63 的 positive 连接的是 ["75:74", 0] (Prompt)
        
        var lastConditioningNodeId = "75:74"
        
        imagePaths.forEachIndexed { index, path ->
            val nodeIdPrefix = "dynamic_ref_$index"
            val loadImageId = "${nodeIdPrefix}_load"
            val vaeEncodeId = "${nodeIdPrefix}_vae"
            val refLatentId = "${nodeIdPrefix}_ref"

            // A. 添加 LoadImage
            workflow[loadImageId] = mutableMapOf(
                "class_type" to "LoadImage",
                "inputs" to mutableMapOf("image" to path)
            )

            // B. 添加 VAEEncode
            workflow[vaeEncodeId] = mutableMapOf(
                "class_type" to "VAEEncode",
                "inputs" to mutableMapOf(
                    "pixels" to listOf(loadImageId, 0),
                    "vae" to listOf("75:72", 0)
                )
            )

            // C. 添加 ReferenceLatent
            workflow[refLatentId] = mutableMapOf(
                "class_type" to "ReferenceLatent",
                "inputs" to mutableMapOf(
                    "conditioning" to listOf(lastConditioningNodeId, 0),
                    "latent" to listOf(vaeEncodeId, 0)
                )
            )

            lastConditioningNodeId = refLatentId
        }

        // D. 最终将链条末端连回 CFGGuider (75:63)
        (workflow["75:63"]?.get("inputs") as? MutableMap<String, Any>)?.put("positive", listOf(lastConditioningNodeId, 0))
    }
}
