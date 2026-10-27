package com.randytv.mobile.player

/** Include dialogue/centre and surround channels, with headroom to avoid clipping. */
object StereoMixing {
    fun coefficients(channels: Int): FloatArray {
        require(channels in 1..8)
        if (channels == 1) return floatArrayOf(1f, 1f)
        val matrix = FloatArray(channels * 2)
        matrix[0] = 1f
        matrix[3] = 1f
        for (channel in 2 until channels) {
            val gain = if (channel == 3 && channels >= 6) .25f else .7071f
            matrix[channel * 2] = gain
            matrix[channel * 2 + 1] = gain
        }
        for (output in 0..1) {
            val total = (0 until channels).sumOf { matrix[it * 2 + output].toDouble() }.toFloat()
            for (input in 0 until channels) matrix[input * 2 + output] /= total.coerceAtLeast(1f)
        }
        return matrix
    }
}
