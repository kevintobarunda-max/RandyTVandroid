package com.randytv.mobile.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.randytv.mobile.ui.components.FocusGreen
import com.randytv.mobile.ui.components.tvFocusable

@Composable
fun HomeScreen(
    onNavigateLive: () -> Unit,
    onNavigateVod: () -> Unit,
    onNavigateSeries: () -> Unit,
    onNavigateSimpsons: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Fondo de imagen
        val context = LocalContext.current
        val bgResId = context.resources.getIdentifier("home_bg", "drawable", context.packageName)
        if (bgResId != 0) {
            Image(
                painter = painterResource(id = bgResId),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0A)))
        }
        // Overlay oscuro
        Box(modifier = Modifier.fillMaxSize().background(Color(0xCC000000)))

        // Contenido
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Logo izquierda
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Text("RANDY TV", fontSize = 48.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, color = FocusGreen)
                Text("STREAMING", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0x99FFFFFF))
            }
            Spacer(modifier = Modifier.width(48.dp))
            // Botones derecha
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                TVBtn(Icons.Default.Tv, "TV en Vivo", onNavigateLive)
                TVBtn(Icons.Default.Movie, "Peliculas", onNavigateVod)
                TVBtn(Icons.Default.PlayCircle, "Series", onNavigateSeries)
                TVBtn(Icons.Default.AutoAwesome, "Simpsons 24h", onNavigateSimpsons)
            }
        }
    }
}

@Composable
private fun TVBtn(icon: ImageVector, label: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .tvFocusable { focused = it }
            .clickable(onClick = onClick)
            .background(
                if (focused) Color(0x44FFFFFF) else Color(0x1AFFFFFF),
                RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (focused) FocusGreen else Color(0x99FFFFFF), modifier = Modifier.size(26.dp))
            Spacer(modifier = Modifier.width(14.dp))
            Text(label, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = if (focused) Color.White else Color(0xCCFFFFFF))
        }
    }
}
