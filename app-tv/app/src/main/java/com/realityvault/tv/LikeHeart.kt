package com.realityvault.tv

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// Compartido con la app del celular (app-mobile lo copia al compilar).
// Corazón de "Mi lista": transición "Like button" de transitions.dev (misma que en la web).
// Rojo #f40051, el relleno tarda 150 ms, pop 1 → .82 → 1 con rebote y 8 partículas que salen y se desvanecen.
// Al sacarlo de la lista se vacía sin partículas.
private val LikeRed = Color(0xFFF40051)
private const val HEART = "M12 20.5s-7.5-4.6-9.2-9.4C1.6 7.7 3.9 4.5 7.3 4.5c2 0 3.6 1.1 4.7 2.7 1.1-1.6 2.7-2.7 4.7-2.7 3.4 0 5.7 3.2 4.5 6.6-1.7 4.8-9.2 9.4-9.2 9.4z"

@Composable
fun LikeHeart(liked: Boolean, size: Dp, modifier: Modifier = Modifier, idle: Color = Color.White) {
    val path = remember { PathParser().parsePathString(HEART).toPath() }
    val fill by animateFloatAsState(if (liked) 1f else 0f, tween(150), label = "likeFill")
    val pop = remember { Animatable(1f) }
    val burst = remember { Animatable(1f) }
    var seed by remember { mutableIntStateOf(0) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(liked) {
        if (first) { first = false; return@LaunchedEffect }   // al abrir la pantalla no anima, solo al tocar
        if (liked) {
            seed++
            coroutineScope {
                launch { pop.snapTo(1f); pop.animateTo(.82f, tween(105)); pop.animateTo(1f, spring(dampingRatio = .38f, stiffness = Spring.StiffnessMedium)) }
                launch { burst.snapTo(0f); burst.animateTo(1f, tween(600, easing = LinearOutSlowInEasing)) }
            }
        }
    }
    Canvas(modifier.size(size).graphicsLayer { scaleX = pop.value; scaleY = pop.value }) {
        val k = this.size.width / 24f
        val color = lerp(idle, LikeRed, fill)
        withTransform({ scale(k, k, Offset.Zero) }) {
            if (fill > 0f) drawPath(path, LikeRed.copy(alpha = fill), style = Fill)
            drawPath(path, color, style = Stroke(width = 2.3f))
        }
        val p = burst.value
        if (p in 0.001f..0.999f) {
            val rnd = Random(seed)
            val pos = if (p < .2f) .25f * (p / .2f) else .25f + .75f * ((p - .2f) / .8f)
            val alpha = if (p < .2f) p / .2f else 1f - (p - .2f) / .8f
            val grow = if (p < .2f) .4f + .6f * (p / .2f) else 1f - (1f - .6f) * ((p - .2f) / .8f)
            for (i in 0 until 8) {
                val ang = i / 8f * 6.2832f + (rnd.nextFloat() - .5f) * .6f
                val dist = this.size.width * (.75f + rnd.nextFloat() * .6f)
                val r = 1.25.dp.toPx() * (.8f + rnd.nextFloat() * .9f) * (this.size.width / 20.dp.toPx())
                drawCircle(LikeRed.copy(alpha = alpha.coerceIn(0f, 1f)), r * grow, Offset(this.size.width / 2 + cos(ang) * dist * pos, this.size.height / 2 + sin(ang) * dist * pos))
            }
        }
    }
}
