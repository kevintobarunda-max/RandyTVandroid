package com.randytv.mobile.data.catalog

import com.randytv.mobile.data.model.LiveStream
import java.util.Locale

object ChannelGroups {
    fun isLowBitrate(stream: LiveStream): Boolean {
        val name = stream.name.orEmpty().lowercase(Locale.ROOT)
        return listOf("2mb", "| 2", "|2", "2 mb", "[2]", "(2)", "sd|", "|sd", " sd ").any { name.contains(it) }
    }
    fun is24Hour(stream: LiveStream): Boolean = stream.name.orEmpty().contains("24", true)
    fun isSimpsons(stream: LiveStream): Boolean = stream.name.orEmpty().contains("simpson", true)
}
