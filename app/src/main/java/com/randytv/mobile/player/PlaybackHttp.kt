package com.randytv.mobile.player

import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Shared HTTP connections across channel switches, without parallel channel downloads. */
object PlaybackHttp {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(4, 2, TimeUnit.MINUTES))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
}
