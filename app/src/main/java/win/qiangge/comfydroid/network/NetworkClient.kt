package win.qiangge.comfydroid.network

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object NetworkClient {
    private var retrofit: Retrofit? = null
    private var apiService: ComfyApiService? = null
    private var baseUrl: String = ""

    fun initialize(ip: String, port: String) {
        val newUrl = "http://$ip:$port/"
        if (baseUrl != newUrl) {
            baseUrl = newUrl
            val client = OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()

            retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            apiService = retrofit?.create(ComfyApiService::class.java)
        }
    }

    fun getApiService(): ComfyApiService {
        return apiService ?: throw IllegalStateException("NetworkClient not initialized. Call initialize() first.")
    }
}
