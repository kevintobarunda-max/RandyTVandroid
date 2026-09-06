package com.randytv.mobile.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.randytv.mobile.ui.components.FocusGreen
import com.randytv.mobile.ui.components.FocusGlow

@Composable
fun SplashScreen(progress: Double) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Fondo igual que Home
        val context = LocalContext.current
        val bgResId = context.resources.getIdentifier("home_bg", "drawable", context.packageName)
        if (bgResId != 0) {
            Image(
                painter = painterResource(id = bgResId),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        // Overlay oscuro
        Box(modifier = Modifier.fillMaxSize().background(Color(0xDD000000)))

        // Contenido centrado
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Nombre de la app
            Text(
                "RandyTV",
                fontSize = 52.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                color = FocusGreen
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "STREAMING",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0x99FFFFFF)
            )
            Spacer(Modifier.height(32.dp))

            // Porcentaje circular
            Box(
                modifier = Modifier.size(90.dp).background(Color(0x33000000), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = progress.toFloat().coerceIn(0f, 1f),
                    modifier = Modifier.size(80.dp),
                    color = FocusGlow,
                    trackColor = Color(0x22FFFFFF),
                    strokeWidth = 5.dp,
                    strokeCap = StrokeCap.Round
                )
                Text(
                    "${(progress * 100).toInt()}%",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Cargando contenido...",
                fontSize = 12.sp,
                color = Color(0x77FFFFFF)
            )
        }
    }
}
