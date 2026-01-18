package win.qiangge.comfydroid.network

import android.content.Context
import org.json.JSONObject
import org.json.JSONArray

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
        // 检查是否有参考图
        val imagePaths = (inputs["ref_images"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        val templateName = if (imagePaths.isNotEmpty()) "templates/flux_reference_template.json" else "templates/flux_base.json"
        
        val jsonString = context.assets.open(templateName).bufferedReader().use { it.readText() }
        val workflow = JSONObject(jsonString)

        // 2. 修改基础参数 (使用 JSONObject 直接修改，保留类型)
        
        // 修改 75:74 (CLIPTextEncode) 的 text
        // 注意：reference 模板中 prompt 节点 ID 可能不同。
        // turbo-text.json: 75:74 (Positive)
        // turbo-edit.json: 92:74 (Positive)
        // 为了通用，我们需要根据模板查找 ID，或者我们约定好 ID。
        // 这里为了简单，我们假设 ID 映射关系：
        // Base -> Ref
        // 75:74 -> 92:74 (Prompt)
        // 75:73 -> 92:73 (Seed)
        // 75:62 -> 92:62 (Scheduler)
        // 75:63 -> 92:63 (Guider)
        // 75:66 -> 92:66 (EmptyLatent)
        
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
            // 如果是 Ref 模式，宽高通常由图片决定，但我们这里强制覆盖
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
            // 断开可能存在的 GetImageSize 连接
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
        // 在 turbo-edit.json 中：
        // 原始链：Prompt(92:74) -> RefLatent(92:79:77) -> RefLatent(92:84:77) -> Guider(92:63)
        // 这是一个比较复杂的双图结构。
        // 为了支持动态 N 张图，我们需要重建这个链条。
        
        // 1. 找到 Guider 的 positive 输入，看看它连着谁。
        // 目标：构建 Prompt -> Ref1 -> Ref2 -> ... -> RefN -> Guider
        
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
            // 注意：为了效果更好，通常需要先 Scale 图片。这里简化，直接 VAEEncode。
            // 或者我们可以复用模板里的 ImageScaleToTotalPixels 逻辑，但这太复杂了。
            // 我们直接用 LoadImage -> VAEEncode -> ReferenceLatent
            
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