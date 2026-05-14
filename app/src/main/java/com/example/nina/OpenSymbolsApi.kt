package com.example.nina

import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

// Modèle de données pour un symbole
data class OpenSymbol(
    val name: String,
    @SerializedName("image_url") val imageUrl: String
)

// Interface Retrofit
interface OpenSymbolsService {
    @GET("symbols/search")
    suspend fun searchSymbols(@Query("q") query: String): List<OpenSymbol>
}

// Client réseau (Singleton)
object OpenSymbolsNetwork {
    private val retrofit = Retrofit.Builder()
        .baseUrl("https://www.opensymbols.org/api/v1/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val api: OpenSymbolsService = retrofit.create(OpenSymbolsService::class.java)
}
