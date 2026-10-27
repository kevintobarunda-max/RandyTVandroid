package com.randytv.mobile.player

/** SDR image presets. Digital adjustments, not LED power. */
enum class PictureMode(
    val label: String, val description: String,
    val gain: Float, val lift: Float, val saturation: Float,
    val maximumWindowBrightness: Boolean = false
) {
    ORIGINAL("Original / rápido", "Sin filtros; menor trabajo gráfico.", 1f, 0f, 1f),
    TV("TV", "Imagen equilibrada con sombras más visibles.", 1.06f, 0.035f, 1.05f),
    MOVIE("Películas", "Color suave y aclarado moderado.", 1.03f, 0.025f, 0.97f),
    SPORTS("Deportes", "Imagen luminosa y colores más intensos.", 1.12f, 0.05f, 1.16f),
    VIVID("Vívido", "Mayor intensidad de color y luminosidad.", 1.16f, 0.065f, 1.25f),
    MAXIMUM("Máximo brillo", "Máximo aclarado digital; puede perder detalle en blancos y negros.", 1.30f, 0.18f, 1f, true);

    companion object {
        fun fromSaved(value: String?): PictureMode =
            values().firstOrNull { it.name == value } ?: MAXIMUM
    }
}
