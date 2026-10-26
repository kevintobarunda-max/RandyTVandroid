package com.randytv.mobile.player

import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.decoder.vp9.VpxLibrary
import androidx.media3.decoder.av1.Gav1Library

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
object VideoDecoders {
    init { VpxLibrary.setLibraries(C.CRYPTO_TYPE_UNSUPPORTED, "vpxV2JNI") }
    val vp9: Boolean = VpxLibrary.isAvailable()
    val av1: Boolean = Gav1Library.isAvailable()
    fun supports(mime: String?): Boolean = when (mime) {
        MimeTypes.VIDEO_VP9 -> vp9
        MimeTypes.VIDEO_AV1 -> av1
        else -> false
    }
}
