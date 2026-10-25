package com.randytv.mobile.ui.components

import android.view.SoundEffectConstants
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp

val FocusGreen = Color(0xFF00E676)
val FocusGlow = Color(0xFF00FF88)

/** Clickable/Button supplies the focus target; decoration adds no duplicate target or layers. */
@Composable
fun Modifier.tvFocusable(onFocused: ((Boolean) -> Unit)? = null): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val view = LocalView.current
    this.onFocusChanged {
        if (focused != it.isFocused) {
            focused = it.isFocused
            if (focused) view.playSoundEffect(SoundEffectConstants.NAVIGATION_DOWN)
            onFocused?.invoke(focused)
        }
    }.border(if (focused) 2.dp else 1.dp, if (focused) FocusGlow else Color(0x22FFFFFF),
        RoundedCornerShape(8.dp))
}
