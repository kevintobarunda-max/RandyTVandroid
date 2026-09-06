package com.randytv.mobile.data.network

import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

const val TMDB_API_KEY = "eb55a71c3a8f3526e1a448ba8b77bc30"

data class TmdbSearchResponse(
    @SerializedName("results") val results: List<TmdbMovie>? = null
)

data class TmdbMovie(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("title") val title: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("overview") val overview: String? = null,
    @SerializedName("vote_average") val voteAverage: Double? = null,
    @SerializedName("vote_count") val voteCount: Int? = null,
    @SerializedName("release_date") val releaseDate: String? = null,
    @SerializedName("first_air_date") val firstAirDate: String? = null
)

data class TmdbDetail(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("title") val title: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("overview") val overview: String? = null,
    @SerializedName("vote_average") val voteAverage: Double? = null,
    @SerializedName("vote_count") val voteCount: Int? = null,
    @SerializedName("release_date") val releaseDate: String? = null,
    @SerializedName("first_air_date") val firstAirDate: String? = null,
    @SerializedName("runtime") val runtime: Int? = null,
    @SerializedName("genres") val genres: List<TmdbGenre>? = null,
    @SerializedName("tagline") val tagline: String? = null
)

data class TmdbGenre(@SerializedName("name") val name: String = "")

data class TmdbCredits(
    @SerializedName("cast") val cast: List<TmdbCast>? = null,
    @SerializedName("crew") val crew: List<TmdbCrew>? = null
)

data class TmdbCast(@SerializedName("name") val name: String = "")
data class TmdbCrew(@SerializedName("name") val name: String = "", @SerializedName("job") val job: String = "")

data class OmdbResult(
    val title: String? = null,
    val year: String? = null,
    val runtime: String? = null,
    val genre: String? = null,
    val director: String? = null,
    val actors: String? = null,
    val plot: String? = null,
    val awards: String? = null,
    val imdbRating: String? = null,
    val imdbVotes: String? = null,
    val response: String? = null
)

class OmdbService {
    private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build()
    private val gson: Gson = GsonBuilder().setLenient().create()
    private val base = "https://api.themoviedb.org/3"

    private fun cleanTitle(title: String): String {
        return title
            .replace(Regex("\\s*\\(.*?\\)"), "")
            .replace(Regex("\\s*\\[.*?]"), "")
            .replace(Regex("\\s*\\|.*"), "")
            .replace(Regex("\\s*-\\s*(HD|SD|4K|720|1080|Latino|Castellano|Dual|VOSE|Sub|Esp|FHD|UHD).*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+(HD|SD|4K|720p|1080p|2MB|3MB|Latino|Castellano|Dual|VOSE|FHD|UHD|ESP|ENG|SUB)$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+\\d{4}$"), "")
            .replace(Regex("^\\d+\\s*[-.]\\s*"), "")
            .trim()
    }

    private fun fetch(url: String): String? {
        return try {
            val req = Request.Builder().url(url).build()
            val resp = client.newCall(req).execute()
            resp.body?.string()
        } catch (e: Exception) { null }
    }

    suspend fun searchByTitle(title: String): OmdbResult? = withContext(Dispatchers.IO) {
        try {
            val clean = cleanTitle(title)
            Log.d("TMDB", "Buscando: $clean")

            // Buscar pelicula
            var json = fetch("$base/search/movie?api_key=$TMDB_API_KEY&language=es-ES&query=${clean.replace(" ", "+")}")
            var search = gson.fromJson(json ?: "{}", TmdbSearchResponse::class.java)
            var movieId = search?.results?.firstOrNull()?.id ?: 0
            var isSeries = false

            // Si no encuentra, buscar serie
            if (movieId == 0) {
                json = fetch("$base/search/tv?api_key=$TMDB_API_KEY&language=es-ES&query=${clean.replace(" ", "+")}")
                search = gson.fromJson(json ?: "{}", TmdbSearchResponse::class.java)
                movieId = search?.results?.firstOrNull()?.id ?: 0
                isSeries = true
            }

            if (movieId == 0) { Log.d("TMDB", "No encontrado: $clean"); return@withContext null }

            val type = if (isSeries) "tv" else "movie"
            Log.d("TMDB", "Encontrado ID: $movieId tipo: $type")

            // Detalle en espanol
            json = fetch("$base/$type/$movieId?api_key=$TMDB_API_KEY&language=es-ES")
            val detail = gson.fromJson(json ?: "{}", TmdbDetail::class.java)

            // Creditos
            json = fetch("$base/$type/$movieId/credits?api_key=$TMDB_API_KEY")
            val credits = gson.fromJson(json ?: "{}", TmdbCredits::class.java)

            val director = credits?.crew?.firstOrNull { it.job == "Director" }?.name
            val actors = credits?.cast?.take(8)?.joinToString(", ") { it.name }
            val genreStr = detail?.genres?.joinToString(", ") { it.name }
            val year = (detail?.releaseDate ?: detail?.firstAirDate)?.take(4)
            val runtimeStr = detail?.runtime?.let { "${it} min" }
            val votes = detail?.voteCount?.let { if (it >= 1000) "${it/1000}K" else "$it" }

            Log.d("TMDB", "Plot: ${detail?.overview?.take(50)}")

            OmdbResult(
                title = detail?.title ?: detail?.name,
                year = year,
                runtime = runtimeStr,
                genre = genreStr,
                director = director,
                actors = actors,
                plot = detail?.overview,
                awards = detail?.tagline,
                imdbRating = detail?.voteAverage?.let { String.format("%.1f", it) },
                imdbVotes = votes,
                response = "True"
            )
        } catch (e: Exception) { Log.e("TMDB", "Error: ${e.message}"); null }
    }
}
