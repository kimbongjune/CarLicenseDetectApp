package com.example.carlicense.network

import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

// API 인터페이스 정의
interface ApiService {
    @Multipart
    @POST("/api/upload")
    fun uploadImage(
        @Part file: MultipartBody.Part
    ): Call<ApiResponse>
}

// Retrofit 클라이언트 설정
object RetrofitClient {
    private const val BASE_URL = "http://192.168.0.46:5500" // IP 주소와 포트 번호

    private val CONNECTION_TIMEOUT = 10L
    private val READ_TIMEOUT = 10L
    private val WRITE_TIMEOUT = 10L

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        setLevel(HttpLoggingInterceptor.Level.BODY)
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(CONNECTION_TIMEOUT, java.util.concurrent.TimeUnit.SECONDS) // 연결 타임아웃 설정
        .readTimeout(READ_TIMEOUT, java.util.concurrent.TimeUnit.SECONDS) // 읽기 타임아웃 설정
        .writeTimeout(WRITE_TIMEOUT, java.util.concurrent.TimeUnit.SECONDS) // 쓰기 타임아웃 설정
        .build()


    val instance: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}

// 서버에서 받을 응답 데이터 모델
data class ApiResponse(
    val prediction: String?,
    val texts: List<String>?,
    val license_plate_image: String?
)
