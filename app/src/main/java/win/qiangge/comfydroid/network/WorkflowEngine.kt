package win.qiangge.comfydroid.network

import android.content.Context
import org.json.JSONObject
import org.json.JSONArray

/**
 * 核心引擎：使用原生 JSONObject 确保类型安全
 */
class WorkflowEngine(private val context: Context) {

    fun getRawTemplate(name: String = "flux_base.json"): String {
        return context.assets.open("templates/$name").bufferedReader().use { it.readText() }
    }

    fun buildWorkflow(workflowId: String, inputs: Map<String, Any>): String {
        return when (workflowId) {
            "flux2klein" -> buildFlux2Klein(inputs)
            "z_image_turbo" -> buildZImageTurbo(inputs)
            else -> "{}"
        }
    }

    private fun buildZImageTurbo(inputs: Map<String, Any>): String {
        val prompt = inputs["prompt"] as? String ?: ""
        val seedRaw = inputs["seed"]
        val seed = when(seedRaw) {
            is Long -> seedRaw
            is Float -> seedRaw.toLong()
            is Int -> seedRaw.toLong()
            else -> 0L
        }
        val finalSeed = if (seed <= 0) (0..Long.MAX_VALUE).random() else seed

        val width = (inputs["width"] as? Number)?.toInt() ?: 1280
        val height = (inputs["height"] as? Number)?.toInt() ?: 720
        val steps = (inputs["steps"] as? Number)?.toInt() ?: 9
        val cfg = (inputs["cfg"] as? Number)?.toDouble() ?: 1.0

        val jsonString = getRawTemplate("z_image_turbo.json")
        val workflow = JSONObject(jsonString)

        // 1. Prompt (Node 45)
        if (workflow.has("45")) {
            workflow.getJSONObject("45").getJSONObject("inputs").put("text", prompt)
        }

        // 2. KSampler (Node 44) - Steps, CFG, Seed
        if (workflow.has("44")) {
            val inputs = workflow.getJSONObject("44").getJSONObject("inputs")
            inputs.put("seed", finalSeed)
            inputs.put("steps", steps)
            inputs.put("cfg", cfg)
        }

        // 3. Empty Latent (Node 41) - Width, Height
        if (workflow.has("41")) {
            val inputs = workflow.getJSONObject("41").getJSONObject("inputs")
            inputs.put("width", width)
            inputs.put("height", height)
        }

        return workflow.toString()
    }

    private fun buildFlux2Klein(inputs: Map<String, Any>): String {
        val prompt = inputs["prompt"] as? String ?: ""
        val seedRaw = inputs["seed"]
        val seed = when(seedRaw) {
            is Long -> seedRaw
            is Float -> seedRaw.toLong()
            is Int -> seedRaw.toLong()
            else -> 0L
        }
        
        // 1. 加载基础模板
        // 检查是否有参考图
        val imagePaths = (inputs["ref_images"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        val templateName = if (imagePaths.isNotEmpty()) "flux_reference_template.json" else "flux_base.json"
        
        val jsonString = getRawTemplate(templateName)
        val workflow = JSONObject(jsonString)

        val idPrefix = if (imagePaths.isNotEmpty()) "92" else "75"
        
        // 修改 Prompt
        val promptNodeId = "$idPrefix:74"
        if (workflow.has(promptNodeId)) {
            val node = workflow.getJSONObject(promptNodeId)
            val nodeInputs = node.getJSONObject("inputs")
            nodeInputs.put("text", prompt)
        }
        
        // 修改 Seed
        val seedNodeId = "$idPrefix:73"
        if (workflow.has(seedNodeId)) {
            val node = workflow.getJSONObject(seedNodeId)
            val nodeInputs = node.getJSONObject("inputs")
            val finalSeed = if (seed <= 0) (0..Long.MAX_VALUE).random() else seed
            nodeInputs.put("noise_seed", finalSeed)
        }

        // 3. 处理参数内联
        val width = (inputs["width"] as? Number)?.toInt() ?: 1024
        val height = (inputs["height"] as? Number)?.toInt() ?: 1024
        val steps = (inputs["steps"] as? Number)?.toInt() ?: 4
        val cfg = (inputs["cfg"] as? Number)?.toDouble() ?: 1.0

        // Scheduler
        val schedulerId = "$idPrefix:62"
        if (workflow.has(schedulerId)) {
            val nodeInputs = workflow.getJSONObject(schedulerId).getJSONObject("inputs")
            nodeInputs.put("steps", steps)
            nodeInputs.put("width", width)
            nodeInputs.put("height", height)
        }

        // Guider
        val guiderId = "$idPrefix:63"
        if (workflow.has(guiderId)) {
            val nodeInputs = workflow.getJSONObject(guiderId).getJSONObject("inputs")
            nodeInputs.put("cfg", cfg)
        }

        // EmptyLatent
        val emptyLatentId = "$idPrefix:66"
        if (workflow.has(emptyLatentId)) {
            val nodeInputs = workflow.getJSONObject(emptyLatentId).getJSONObject("inputs")
            // 关键修复：无论是否有参考图，都强制使用 UI 指定的宽高
            nodeInputs.put("width", width)
            nodeInputs.put("height", height)
        }

        // 4. 处理参考图 (仅当 imagePaths 不为空)
        if (imagePaths.isNotEmpty()) {
            applyReferenceImages(workflow, imagePaths)
        }

        return workflow.toString()
    }

    private fun applyReferenceImages(workflow: JSONObject, imagePaths: List<String>) {
        // 起点：Prompt 节点
        var currentConditioningOutput = JSONArray().put("92:74").put(0) // ["92:74", 0]
        
        // 我们需要 VAELoader ID
        val vaeLoaderId = "92:72"

        imagePaths.forEachIndexed { index, filename ->
            val idSuffix = "_dyn_$index"
            
            // A. LoadImage
            val loadImageId = "LoadImage$idSuffix"
            val loadImageNode = JSONObject()
            loadImageNode.put("class_type", "LoadImage")
            val loadImageInputs = JSONObject()
            loadImageInputs.put("image", filename)
            loadImageNode.put("inputs", loadImageInputs)
            workflow.put(loadImageId, loadImageNode)
            
            // B. VAEEncode (需要 VAE)
            val vaeEncodeId = "VAEEncode$idSuffix"
            val vaeEncodeNode = JSONObject()
            vaeEncodeNode.put("class_type", "VAEEncode")
            val vaeEncodeInputs = JSONObject()
            vaeEncodeInputs.put("pixels", JSONArray().put(loadImageId).put(0))
            vaeEncodeInputs.put("vae", JSONArray().put(vaeLoaderId).put(0))
            vaeEncodeNode.put("inputs", vaeEncodeInputs)
            workflow.put(vaeEncodeId, vaeEncodeNode)
            
            // C. ReferenceLatent
            val refLatentId = "ReferenceLatent$idSuffix"
            val refLatentNode = JSONObject()
            refLatentNode.put("class_type", "ReferenceLatent")
            val refLatentInputs = JSONObject()
            refLatentInputs.put("latent", JSONArray().put(vaeEncodeId).put(0))
            refLatentInputs.put("conditioning", currentConditioningOutput) // 串联上一个
            refLatentNode.put("inputs", refLatentInputs)
            workflow.put(refLatentId, refLatentNode)
            
            // 更新指针
            currentConditioningOutput = JSONArray().put(refLatentId).put(0)
        }
        
        // 5. 最终将链条连入 Guider (92:63)
        val guiderInputs = workflow.getJSONObject("92:63").getJSONObject("inputs")
        guiderInputs.put("positive", currentConditioningOutput)
    }
}
