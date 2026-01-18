package win.qiangge.comfydroid.model

object WorkflowRegistry {
    val workflows = listOf(
        SuperWorkflow(
            id = "flux_ref",
            name = "Flux 参考图生成 (超级版)",
            description = "支持 0-N 张参考图的智能生成流程。如果只提供文本，就是普通文生图；如果提供图片，自动启用参考生成。",
            inputs = listOf(
                TextInput(
                    id = "prompt",
                    label = "正向提示词 (Prompt)",
                    multiline = true,
                    defaultValue = "A beautiful landscape"
                ),
                NumberInput(
                    id = "seed",
                    label = "随机种子 (Seed)",
                    defaultValue = 0f, // 0 means random
                    min = 0f,
                    max = Long.MAX_VALUE.toFloat(),
                    isInteger = true
                ),
                // 暂时只用基础类型验证 UI，后续实现 ImageArrayInput
                TextInput(
                    id = "debug_note",
                    label = "调试备注",
                    isRequired = false
                )
            )
        )
    )
}
