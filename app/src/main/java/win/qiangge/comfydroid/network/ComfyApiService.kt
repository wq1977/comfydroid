package win.qiangge.comfydroid.network

import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Body
import retrofit2.http.Path
import retrofit2.http.Headers
import retrofit2.http.Multipart
import retrofit2.http.Part
import okhttp3.MultipartBody

data class PromptRequest(
// ... (keep existing)

data class PromptResponse(
// ... (keep existing)

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
    
    @GET("history/{prompt_id}")
    suspend fun getHistory(@Path("prompt_id") promptId: String): Map<String, Any>

    @Multipart
    @POST("upload/image")
    suspend fun uploadImage(@Part image: MultipartBody.Part): ImageUploadResponse
}


