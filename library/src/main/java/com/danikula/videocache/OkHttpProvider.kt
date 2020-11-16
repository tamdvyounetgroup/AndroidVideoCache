package com.danikula.videocache

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object OkHttpProvider {
    val okHttp : OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS).build()

    val builder: OkHttpClient.Builder
        get() {
            return okHttp.newBuilder()
        }

}