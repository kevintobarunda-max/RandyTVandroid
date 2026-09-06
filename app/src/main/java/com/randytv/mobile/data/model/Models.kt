package com.randytv.mobile.data.model

import com.google.gson.annotations.SerializedName

data class Category(
    @SerializedName("category_id") val categoryId: String = "",
    @SerializedName("category_name") val categoryName: String = "",
    @SerializedName("parent_id") val parentId: Int = 0
)

data class LiveStream(
    @SerializedName("num") val num: Int = 0,
    @SerializedName("name") val name: String = "",
    @SerializedName("stream_type") val streamType: String = "",
    @SerializedName("stream_id") val streamId: Int = 0,
    @SerializedName("stream_icon") val streamIcon: String = "",
    @SerializedName("epg_channel_id") val epgChannelId: String? = null,
    @SerializedName("added") val added: String? = null,
    @SerializedName("category_id") val categoryId: String = "",
    @SerializedName("custom_sid") val customSid: String? = null,
    @SerializedName("tv_archive") val tvArchive: Int = 0,
    @SerializedName("direct_source") val directSource: String? = null,
    @SerializedName("tv_archive_duration") val tvArchiveDuration: Int = 0
)

data class VodStream(
    @SerializedName("num") val num: Int = 0,
    @SerializedName("name") val name: String = "",
    @SerializedName("stream_type") val streamType: String = "",
    @SerializedName("stream_id") val streamId: Int = 0,
    @SerializedName("stream_icon") val streamIcon: String = "",
    @SerializedName("rating") val rating: String? = null,
    @SerializedName("rating_5based") val rating5Based: String? = null,
    @SerializedName("added") val added: String? = null,
    @SerializedName("category_id") val categoryId: String = "",
    @SerializedName("container_extension") val containerExtension: String = "mp4",
    @SerializedName("custom_sid") val customSid: String? = null,
    @SerializedName("direct_source") val directSource: String? = null,
    @SerializedName("plot") val plot: String? = null,
    @SerializedName("cast") val cast: String? = null,
    @SerializedName("director") val director: String? = null,
    @SerializedName("genre") val genre: String? = null,
    @SerializedName("release_date") val releaseDate: String? = null,
    @SerializedName("cover") val cover: String? = null
) {
    fun getRating(): Double? {
        rating?.toDoubleOrNull()?.let { return it }
        rating5Based?.toDoubleOrNull()?.let { return it * 2 }
        return null
    }
    fun getImageUrl(): String = streamIcon.ifEmpty { cover ?: "" }
}

data class SeriesItem(
    @SerializedName("num") val num: Int = 0,
    @SerializedName("name") val name: String = "",
    @SerializedName("series_id") val seriesId: Int = 0,
    @SerializedName("cover") val cover: String = "",
    @SerializedName("plot") val plot: String? = null,
    @SerializedName("cast") val cast: String? = null,
    @SerializedName("director") val director: String? = null,
    @SerializedName("genre") val genre: String? = null,
    @SerializedName("release_date") val releaseDate: String? = null,
    @SerializedName("rating") val rating: String? = null,
    @SerializedName("rating_5based") val rating5Based: String? = null,
    @SerializedName("category_id") val categoryId: String = "",
    @SerializedName("stream_icon") val streamIcon: String? = null
) {
    fun getRating(): Double? {
        rating?.toDoubleOrNull()?.let { return it }
        rating5Based?.toDoubleOrNull()?.let { return it * 2 }
        return null
    }
    fun getImageUrl(): String = cover.ifEmpty { streamIcon ?: "" }
}

data class SeriesInfoResponse(
    @SerializedName("seasons") val seasons: List<Season>? = null,
    @SerializedName("episodes") val episodes: Map<String, List<Episode>>? = null,
    @SerializedName("info") val info: SeriesInfo? = null
)

data class Season(
    @SerializedName("season_number") val seasonNumber: Int = 0,
    @SerializedName("name") val name: String = ""
)

data class Episode(
    @SerializedName("id") val id: String = "",
    @SerializedName("episode_num") val episodeNum: Int = 0,
    @SerializedName("title") val title: String = "",
    @SerializedName("container_extension") val containerExtension: String = "mp4",
    @SerializedName("season") val season: Int = 0,
    @SerializedName("info") val info: EpisodeInfo? = null
)

data class EpisodeInfo(
    @SerializedName("plot") val plot: String? = null,
    @SerializedName("duration") val duration: String? = null,
    @SerializedName("movie_image") val movieImage: String? = null
)

data class SeriesInfo(
    @SerializedName("name") val name: String = "",
    @SerializedName("cover") val cover: String = "",
    @SerializedName("plot") val plot: String? = null,
    @SerializedName("cast") val cast: String? = null,
    @SerializedName("genre") val genre: String? = null,
    @SerializedName("rating") val rating: String? = null
)
