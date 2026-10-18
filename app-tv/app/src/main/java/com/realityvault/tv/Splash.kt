package com.realityvault.tv

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Pantalla al abrir la app: el logo entra con una animación suave
 * y después pasa sola a Inicio (si ya hay sesión) o a la puerta de activación/registro.
 */
@Composable
fun SplashScreen(nav: Nav) {
    // progreso de entrada: 0 -> 1 en ~900ms
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = System.currentTimeMillis()
        while (t < 1f) {
            t = ((System.currentTimeMillis() - start) / 900f).coerceIn(0f, 1f)
            delay(16)
        }
    }
    val inFrac = FastOutSlowInEasing.transform(t)

    val infinite = rememberInfiniteTransition(label = "splash")
    val glow by infinite.animateFloat(
        initialValue = 0.7f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "glow"
    )
    val ring1 by infinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "ring1"
    )
    val ring2 by infinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, delayMillis = 900, easing = LinearEasing)), label = "ring2"
    )
    val bar by infinite.animateFloat(
        initialValue = -40f, targetValue = 150f,
        animationSpec = infiniteRepeatable(tween(1500, delayMillis = 1000, easing = FastOutSlowInEasing)), label = "bar"
    )

    LaunchedEffect(Unit) {
        delay(2100)
        nav.section(if (Store.session != null) Screen.Home else Screen.Login(required = true))
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Box(
            Modifier.fillMaxSize()
                .background(Brush.radialGradient(listOf(Color(0xFF2A1520), Color.Black), radius = 1500f))
                .alpha(glow)
        )

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Ring(ring1)
            Ring(ring2)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .graphicsLayer {
                            val s = 0.72f + 0.28f * inFrac
                            scaleX = s; scaleY = s
                            translationY = (1f - inFrac) * 18f
                        }
                        .alpha(inFrac)
                ) {
                    if (Store.siteLogo.isNotBlank()) Img(Store.siteLogo, Modifier.width(420.dp).height(66.dp), scale = ContentScale.Fit)
                    else LocalLogo(Modifier.width(420.dp).height(66.dp))
                }
                Box(Modifier.height(20.dp))
                Text(
                    "TU UNIVERSO DE SERIES Y PELÍCULAS",
                    style = ts(15, FontWeight.Light, C.muted),
                    modifier = Modifier.alpha(((t - .45f) / .5f).coerceIn(0f, 1f))
                )
            }
        }

        Box(Modifier.fillMaxSize().padding(bottom = 90.dp), contentAlignment = Alignment.BottomCenter) {
            Box(Modifier.width(150.dp).height(3.dp).background(Color.White.copy(alpha = .14f), RoundedCornerShape(99.dp))) {
                Box(
                    Modifier.offset(x = bar.dp).width(60.dp).height(3.dp)
                        .background(Brush.horizontalGradient(listOf(C.accent, Color.White)), RoundedCornerShape(99.dp))
                )
            }
        }
    }
}

@Composable
private fun Ring(progress: Float) {
    val a = (1f - progress) * .5f
    val scale = 0.5f + progress * 0.95f
    Canvas(
        Modifier.width(420.dp).height(420.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = a }
    ) {
        drawCircle(C.accent, radius = size.minDimension / 2, style = Stroke(1.5.dp.toPx()))
    }
}
