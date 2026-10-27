package com.randytv.mobile.player

import java.util.Locale

object MediaContainer {
    fun extension(value: String?): String = value.orEmpty().trim().removePrefix(".")
        .lowercase(Locale.ROOT).ifBlank { "mp4" }
}
