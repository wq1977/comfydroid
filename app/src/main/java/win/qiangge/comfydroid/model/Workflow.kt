package win.qiangge.comfydroid.model

/**
 * 代表一个预定义的超级工作流
 */
data class SuperWorkflow(
    val id: String,
    val name: String,
    val description: String,
    val inputs: List<WorkflowInput>
)

/**
 * 工作流输入的基类
 */
sealed class WorkflowInput {
    abstract val id: String
    abstract val label: String
    abstract val isRequired: Boolean
}

data class TextInput(
    override val id: String,
    override val label: String,
    val defaultValue: String = "",
    val multiline: Boolean = false,
    override val isRequired: Boolean = true
) : WorkflowInput()

data class NumberInput(
    override val id: String,
    override val label: String,
    val defaultValue: Float,
    val min: Float,
    val max: Float,
    val isInteger: Boolean = true,
    override val isRequired: Boolean = true
) : WorkflowInput()

data class ImageInput(
    override val id: String,
    override val label: String,
    val hasMask: Boolean = false,
    override val isRequired: Boolean = true
) : WorkflowInput()

data class ImageArrayInput(
    override val id: String,
    override val label: String,
    val minCount: Int = 0,
    val maxCount: Int = 5,
    override val isRequired: Boolean = false
) : WorkflowInput()
