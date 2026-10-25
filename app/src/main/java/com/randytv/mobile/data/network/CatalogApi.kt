package com.randytv.mobile.data.network

import com.randytv.mobile.data.model.*

interface CatalogApi {
    suspend fun getLiveCategories(): List<Category>
    suspend fun getVodCategories(): List<Category>
    suspend fun getSeriesCategories(): List<Category>
    suspend fun getLiveStreams(): List<LiveStream>
    suspend fun getVodStreams(): List<VodStream>
    suspend fun getSeriesList(): List<SeriesItem>
    suspend fun getSeriesInfo(seriesId: String): SeriesInfoResponse?
}
