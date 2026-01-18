package win.qiangge.comfydroid.model

object WorkflowRegistry {
    val workflows = listOf(
        SuperWorkflow(
            id = "flux2klein",
            name = "Flux 2 Klein",
            description = "支持 0-N 张参考图的智能生成流程。如果只提供文本，就是普通文生图；如果提供图片，自动启用参考生成。",
            inputs = listOf(
                // --- 基础区域 ---
                TextInput(
                    id = "prompt",
                    label = "正向提示词 (Prompt)",
                    multiline = true,
                    defaultValue = "A beautiful landscape"
                ),
                
                ImageArrayInput(
                    id = "ref_images",
                    label = "参考图片 (Reference Images)",
                    maxCount = 5,
                    isRequired = false
                ),

                // --- 高级区域 (UI 将根据 ID 或顺序折叠这些) ---
                NumberInput(
                    id = "width",
                    label = "宽度 (Width)",
                    defaultValue = 1024f,
                    min = 64f,
                    max = 4096f,
                    isInteger = true
                ),
                NumberInput(
                    id = "height",
                    label = "高度 (Height)",
                    defaultValue = 1024f,
                    min = 64f,
                    max = 4096f,
                    isInteger = true
                ),
                NumberInput(
                    id = "seed",
                    label = "随机种子 (Seed)",
                    defaultValue = 0f, // 0 表示随机
                    min = 0f,
                    max = Long.MAX_VALUE.toFloat(),
                    isInteger = true
                ),
                NumberInput(
                    id = "steps",
                    label = "步数 (Steps)",
                    defaultValue = 4f,
                    min = 1f,
                    max = 50f,
                    isInteger = true
                ),
                NumberInput(
                    id = "cfg",
                    label = "提示词引导 (CFG)",
                    defaultValue = 1f, // Flux 通常用 1.0
                    min = 0f,
                    max = 20f,
                    isInteger = false
                )
            )
        )
    )
}