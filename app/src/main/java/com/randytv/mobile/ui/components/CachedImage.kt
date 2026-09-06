package com.randytv.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Scale
import coil.size.Size

@Composable
fun CachedImage(
    url: String,
    height: Dp,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    targetWidthPx: Int = 300,
    targetHeightPx: Int = 450
) {
    if (url.isBlank()) {
        Box(modifier = modifier.fillMaxWidth().height(height).background(Color(0xFF1A1A1A)))
        return
    }
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(url)
            .crossfade(150)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            // Decodifica solo el tamano real que se va a mostrar en pantalla,
            // en vez de un tamano fijo de 200x300 para todo (cuadrados y portadas)
            .size(Size(targetWidthPx, targetHeightPx))
            .scale(Scale.FILL)
            .precision(Precision.INEXACT)
            .allowHardware(true)
            .build(),
        contentDescription = null,
        contentScale = contentScale,
        modifier = modifier.fillMaxWidth().height(height)
    )
}
