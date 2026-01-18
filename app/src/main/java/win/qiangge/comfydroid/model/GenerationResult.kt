package win.qiangge.comfydroid.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "generation_results")
data class GenerationResult(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val promptId: String,
    val workflowName: String,
    val promptText: String,
    val timestamp: Long,
    val outputType: String,
    val outputFiles: String,
    val status: String = "PENDING", // PENDING, COMPLETED, FAILED
    
    // 新增字段
    val progress: Int = 0,       // 总进度 0-100 (估算) 或当前节点进度
    val maxSteps: Int = 0,       // 当前节点最大步数
    val currentStep: Int = 0,    // 当前节点当前步数
    val nodeStatus: String = "Waiting" // e.g. "KSampler", "VAE Decode"
)