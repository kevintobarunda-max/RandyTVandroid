package com.randytv.mobile.data.network

import com.randytv.mobile.data.model.*

data class CatalogCompletion<T>(val items: List<T>, val failedCategories: Int = 0, val checked: Boolean = false)

interface CatalogApi {
    suspend fun getLiveCategories(): List<Category>
    suspend fun getVodCategories(): List<Category>
    suspend fun getSeriesCategories(): List<Category>
    suspend fun getLiveStreams(): List<LiveStream>
    suspend fun getVodStreams(): List<VodStream>
    suspend fun getSeriesList(): List<SeriesItem>
    suspend fun completeVod(seed: List<VodStream>, categories: List<Category>): CatalogCompletion<VodStream> = CatalogCompletion(seed)
    suspend fun completeSeries(seed: List<SeriesItem>, categories: List<Category>): CatalogCompletion<SeriesItem> = CatalogCompletion(seed)
    suspend fun getSeriesInfo(seriesId: String): SeriesInfoResponse?
}
