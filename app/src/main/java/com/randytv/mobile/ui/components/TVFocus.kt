package com.randytv.mobile.ui.components

import android.view.SoundEffectConstants
import androidx.compose.foundation.focusable
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

val FocusGreen = Color(0xFF00E676)
val FocusGlow = Color(0xFF00FF88)

@Composable
fun Modifier.tvFocusable(
    onFocused: ((Boolean) -> Unit)? = null
): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val view = LocalView.current

    this
        .onFocusChanged { state ->
            if (state.isFocused && !focused) {
                view.playSoundEffect(SoundEffectConstants.NAVIGATION_DOWN)
            }
            focused = state.isFocused
            onFocused?.invoke(focused)
        }
        .focusable()
        .zIndex(if (focused) 10f else 0f)
        .scale(if (focused) 1.2f else 1f)
        .drawWithContent {
            drawContent()
            if (focused) {
                // Capa brillante sobre la imagen
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.2f),
                            Color.Transparent,
                            Color.Transparent,
                            FocusGlow.copy(alpha = 0.15f)
                        )
                    ),
                    cornerRadius = CornerRadius(8.dp.toPx())
                )
            }
        }
        .drawBehind {
            if (focused) {
                // Glow exterior
                drawRoundRect(
                    color = FocusGlow.copy(alpha = 0.6f),
                    topLeft = Offset(-6.dp.toPx(), -6.dp.toPx()),
                    size = Size(size.width + 12.dp.toPx(), size.height + 12.dp.toPx()),
                    cornerRadius = CornerRadius(12.dp.toPx()),
                    style = Stroke(width = 4.dp.toPx())
                )
                // Borde interior
                drawRoundRect(
                    color = FocusGlow,
                    cornerRadius = CornerRadius(8.dp.toPx()),
                    style = Stroke(width = 2.5.dp.toPx())
                )
            } else {
                drawRoundRect(
                    color = Color(0x22FFFFFF),
                    cornerRadius = CornerRadius(8.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx())
                )
            }
        }
}
