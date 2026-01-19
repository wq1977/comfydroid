package win.qiangge.comfydroid.model

object WorkflowRegistry {
    val workflows = listOf(
        SuperWorkflow(
            id = "flux2klein",
            name = "Flux 2 Klein",
            description = "支持 0-N 张参考图的智能生成流程。如果只提供文本，就是普通文生图；如果提供图片，自动启用参考生成。",
            inputs = listOf(
                TextInput("prompt", "正向提示词 (Prompt)", multiline = true, defaultValue = "A beautiful landscape"),
                TextInput("negative_prompt", "负面提示词 (Negative)", multiline = true, defaultValue = "low quality, blurry, distorted, ugly, bad anatomy, deformed, watermark, text, out of frame, lowres, error, extra fingers, cropped, worst quality, jpeg artifacts, signature, artist name, b&w, monochrome, desaturated"),
                ImageArrayInput("ref_images", "参考图片 (Reference Images)", maxCount = 5, isRequired = false),
                NumberInput("width", "宽度 (Width)", 1024f, 64f, 4096f, true),
                NumberInput("height", "高度 (Height)", 1024f, 64f, 4096f, true),
                NumberInput("seed", "随机种子 (Seed)", 0f, 0f, Long.MAX_VALUE.toFloat(), true),
                NumberInput("steps", "步数 (Steps)", 4f, 1f, 50f, true),
                NumberInput("cfg", "提示词引导 (CFG)", 1f, 0f, 20f, false)
            )
        ),
        SuperWorkflow(
            id = "z_image_turbo",
            name = "Z-Image Turbo",
            description = "极速文生图 (无参考图支持)。适合快速出图。",
            inputs = listOf(
                TextInput("prompt", "正向提示词 (Prompt)", multiline = true, defaultValue = "A cyberpunk city"),
                NumberInput("width", "宽度 (Width)", 1280f, 64f, 4096f, true),
                NumberInput("height", "高度 (Height)", 720f, 64f, 4096f, true),
                NumberInput("seed", "随机种子 (Seed)", 0f, 0f, Long.MAX_VALUE.toFloat(), true),
                NumberInput("steps", "步数 (Steps)", 9f, 1f, 50f, true),
                NumberInput("cfg", "提示词引导 (CFG)", 1f, 0f, 20f, false)
            )
        )
    )
}