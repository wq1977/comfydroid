package win.qiangge.comfydroid.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "generation_results")
data class GenerationResult(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val promptId: String,       // ComfyUI 返回的任务ID
    val workflowName: String,   // 使用了哪个超级流程
    val promptText: String,     // 主要提示词
    val timestamp: Long,
    val outputType: String,     // IMAGE, VIDEO, AUDIO
    val outputFiles: String,    // JSON 列表存储文件路径 (e.g. ["filename1.png", "filename2.png"])
    val status: String = "PENDING" // PENDING, COMPLETED, FAILED
)
