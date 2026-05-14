package com.example.nina

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path

data class ArasaacSearchResponse(val _id: Int)

data class OpenSymbolResponse(
    val name: String? = null,
    val imageUrl: String? = null
)

interface ArasaacService {
    @GET("fr/search/{label}")
    suspend fun searchPictograms(@Path("label") label: String): List<ArasaacSearchResponse>
}

object RetrofitInstance {
    private val client = okhttp3.OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    val arasaacApi: ArasaacService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.arasaac.org/api/pictograms/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ArasaacService::class.java)
    }
}
