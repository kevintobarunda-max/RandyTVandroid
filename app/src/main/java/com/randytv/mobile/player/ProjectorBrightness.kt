package com.randytv.mobile.player

import androidx.media3.effect.RgbMatrix

/** One GPU matrix combines gain, shadow lift and saturation; no CPU frame copies. */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class ProjectorBrightness(mode: PictureMode) : RgbMatrix {
    private val identity = FloatArray(16).also { for (i in 0..3) it[i * 5] = 1f }
    @Volatile private var adjusted = matrixFor(mode)

    fun update(mode: PictureMode) { adjusted = matrixFor(mode) }

    private fun matrixFor(mode: PictureMode) = identity.copyOf().also { matrix ->
        val weights = floatArrayOf(0.2126f, 0.7152f, 0.0722f)
        // OpenGL column-major RGB matrix; neutral grays stay neutral.
        for (row in 0..2) {
            for (column in 0..2) {
                matrix[column * 4 + row] = mode.gain * (
                    (1f - mode.saturation) * weights[column] +
                        (if (row == column) mode.saturation else 0f)
                    )
            }
            matrix[12 + row] = mode.lift
        }
    }

    // HDR needs tone mapping, not an SDR matrix.
    override fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray =
        if (useHdr) identity else adjusted

    override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean = false
}
