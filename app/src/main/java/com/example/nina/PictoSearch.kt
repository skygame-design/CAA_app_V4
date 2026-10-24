package com.example.nina

import android.util.Log
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

/** Un pictogramme proposé par une banque d'images, avec sa source et sa licence (à créditer). */
data class PictoResult(val imageUrl: String, val source: String, val licence: String)

// --- Global Symbols (Mulberry, Sclera, Blissymbolics… avec libellés français) ---

data class GlobalSymbolsLabel(val text: String?, val picto: GlobalSymbolsPicto?)
data class GlobalSymbolsPicto(
    @SerializedName("symbolset_id") val symbolsetId: Int?,
    @SerializedName("image_url") val imageUrl: String?
)

interface GlobalSymbolsService {
    @GET("labels/search")
    suspend fun search(
        @Query("query") query: String,
        @Query("language") language: String = "fra",
        @Query("language_iso_format") isoFormat: String = "639-3",
        @Query("limit") limit: Int = 40
    ): List<GlobalSymbolsLabel>
}

// --- OpenSymbols (Tawasol, Twemoji, Noun Project, icônes…) ---

data class OpenSymbolsResult(
    val name: String?,
    @SerializedName("image_url") val imageUrl: String?,
    @SerializedName("repo_key") val repoKey: String?,
    val license: String?
)

interface OpenSymbolsSearchService {
    @GET("symbols/search")
    suspend fun search(@Query("q") query: String, @Query("locale") locale: String = "fr"): List<OpenSymbolsResult>
}

/**
 * Recherche de pictogrammes dans plusieurs banques à la fois : ARASAAC, Global Symbols et OpenSymbols.
 * Chaque banque est interrogée en parallèle ; si l'une ne répond pas, les autres restent affichées.
 */
object PictoSearch {
    const val ARASAAC = "ARASAAC"

    private val client = okhttp3.OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val globalSymbols: GlobalSymbolsService by lazy {
        Retrofit.Builder().baseUrl("https://globalsymbols.com/api/v1/").client(client)
            .addConverterFactory(GsonConverterFactory.create()).build().create(GlobalSymbolsService::class.java)
    }
    private val openSymbols: OpenSymbolsSearchService by lazy {
        Retrofit.Builder().baseUrl("https://www.opensymbols.org/api/v1/").client(client)
            .addConverterFactory(GsonConverterFactory.create()).build().create(OpenSymbolsSearchService::class.java)
    }

    /** Collections Global Symbols : nom affiché et licence. ARASAAC (17) est exclu : il est déjà interrogé directement. */
    private val GLOBAL_SYMBOLSETS = mapOf(
        13 to ("Mulberry" to "CC BY-SA 4.0"),
        86 to ("Mulberry" to "CC BY-SA 4.0"),
        205 to ("Mulberry" to "CC BY-SA 4.0"),
        81 to ("Sclera" to "CC BY-NC 4.0"),
        16 to ("Blissymbolics" to "CC BY-SA 4.0"),
    )
    private const val GLOBAL_ARASAAC_SET = 17

    /** Banques d'OpenSymbols déjà couvertes ailleurs (ARASAAC direct, Mulberry et Sclera via Global Symbols). */
    private val OPEN_SYMBOLS_DUPLICATES = setOf("arasaac", "mulberry", "sclera")
    private val OPEN_SYMBOLS_NAMES = mapOf(
        "tawasol" to "Tawasol", "twemoji" to "Twemoji", "noun-project" to "Noun Project",
        "icomoon" to "IcoMoon", "icon_archive" to "Icon Archive", "openmoji" to "OpenMoji"
    )

    /** Ordre d'affichage des sources (les autres suivent). */
    val SOURCE_ORDER = listOf(ARASAAC, "Mulberry", "Sclera", "Tawasol", "Twemoji", "OpenMoji", "Blissymbolics", "Noun Project")

    fun arasaacUrl(id: Int) = "https://static.arasaac.org/pictograms/$id/${id}_300.png"

    fun fromArasaac(results: List<ArasaacSearchResponse>): List<PictoResult> =
        results.map { PictoResult(arasaacUrl(it._id), ARASAAC, "CC BY-NC-SA 4.0") }

    fun fromGlobalSymbols(labels: List<GlobalSymbolsLabel>): List<PictoResult> =
        labels.mapNotNull { label ->
            val picto = label.picto ?: return@mapNotNull null
            val url = picto.imageUrl ?: return@mapNotNull null
            if (picto.symbolsetId == GLOBAL_ARASAAC_SET) return@mapNotNull null
            val (name, licence) = GLOBAL_SYMBOLSETS[picto.symbolsetId] ?: ("Global Symbols" to "voir globalsymbols.com")
            PictoResult(url, name, licence)
        }

    fun fromOpenSymbols(results: List<OpenSymbolsResult>): List<PictoResult> =
        results.mapNotNull { r ->
            val url = r.imageUrl ?: return@mapNotNull null
            val repo = r.repoKey ?: return@mapNotNull null
            if (repo in OPEN_SYMBOLS_DUPLICATES) return@mapNotNull null
            PictoResult(url, OPEN_SYMBOLS_NAMES[repo] ?: repo.replaceFirstChar { it.uppercase() }, r.license ?: "voir opensymbols.org")
        }

    /** Fusionne les résultats : sans doublon d'image, triés par source (ARASAAC d'abord). */
    fun merge(vararg lists: List<PictoResult>): List<PictoResult> =
        lists.toList().flatten()
            .distinctBy { it.imageUrl }
            .sortedBy { SOURCE_ORDER.indexOf(it.source).let { i -> if (i < 0) SOURCE_ORDER.size else i } }

    suspend fun search(query: String): List<PictoResult> = coroutineScope {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return@coroutineScope emptyList()
        val arasaac = async { safe("ARASAAC") { fromArasaac(RetrofitInstance.arasaacApi.searchPictograms(q)) } }
        val global = async { safe("Global Symbols") { fromGlobalSymbols(globalSymbols.search(q)) } }
        val open = async { safe("OpenSymbols") { fromOpenSymbols(openSymbols.search(q)) } }
        merge(arasaac.await(), global.await(), open.await())
    }

    private suspend fun safe(name: String, block: suspend () -> List<PictoResult>): List<PictoResult> =
        try { block() } catch (e: Exception) { Log.w("Nina-Picto", "$name indisponible : ${e.message}"); emptyList() }
}
