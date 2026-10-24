package com.randytv.mobile.player

import android.opengl.Matrix
import androidx.media3.effect.RgbMatrix

/**
 * Small SDR lift using a GPU color matrix, never CPU frame copies.
 * HDR keeps its original matrix: this is not HDR-to-SDR tone mapping.
 */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class ProjectorBrightness(amount: Float) : RgbMatrix {
    private val identity = FloatArray(16).also { Matrix.setIdentityM(it, 0) }
    private val lifted = identity.copyOf().also {
        val lift = amount.coerceIn(0f, 0.12f)
        Matrix.translateM(it, 0, lift, lift, lift)
    }

    override fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray =
        if (useHdr) identity else lifted

    override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean = false
}
