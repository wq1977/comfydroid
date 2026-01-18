package win.qiangge.comfydroid.network

import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Body
import retrofit2.http.Path
import retrofit2.http.Headers
import retrofit2.http.Multipart
import retrofit2.http.Part
import okhttp3.MultipartBody
import okhttp3.RequestBody

data class PromptRequest(
    val prompt: Map<String, Any>,
    val client_id: String? = null
)

data class PromptResponse(
    val prompt_id: String,
    val number: Int,
    val node_errors: Map<String, Any>?
)

data class ImageUploadResponse(
    val name: String,
    val subfolder: String = "",
    val type: String = "input"
)

interface ComfyApiService {
    @GET("system_stats")
    @Headers("Accept: application/json")
    suspend fun getSystemStats(): Map<String, Any>

    @POST("prompt")
    suspend fun queuePrompt(@Body request: PromptRequest): PromptResponse

    @POST("prompt")
    @Headers("Content-Type: application/json")
    suspend fun queuePromptRaw(@Body request: RequestBody): PromptResponse
    
    @GET("history/{prompt_id}")
    suspend fun getHistory(@Path("prompt_id") promptId: String): Map<String, Any>

    @Multipart
    @POST("upload/image")
    suspend fun uploadImage(@Part image: MultipartBody.Part): ImageUploadResponse
}
