package com.realityvault.tv

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/*
 * Intro al abrir la app ("muro que se apaga"):
 *  1. la pantalla se llena con los pósters reales del catálogo;
 *  2. aparece la estrella del logo en su lugar y los pósters se dan vuelta y desaparecen
 *     en onda, desde la estrella hacia afuera;
 *  3. la estrella se achica y desde ella se abre el logo en círculo; abajo aparece la frase.
 * Todo corre con un solo reloj leído en la etapa de dibujo (graphicsLayer / drawWithContent),
 * así no recompone nada cuadro a cuadro y va fluido en la tele.
 */

private const val COLS = 9
private const val END_MS = 3700L
private val easeOut = CubicBezierEasing(.2f, .7f, .2f, 1f)
private val easeIn = CubicBezierEasing(.5f, 0f, .75f, 0f)
private val easeInOut = CubicBezierEasing(.6f, 0f, .2f, 1f)
private val LOGO_W = 420.dp
private val LOGO_H = LOGO_W * (137f / 900f)
private val LogoYellow = Color(0xFFE7F409)

private fun clamp01(v: Float) = v.coerceIn(0f, 1f)

@Composable
fun SplashScreen(nav: Nav) {
    val ctx = LocalContext.current
    var posters by remember { mutableStateOf<List<String>>(emptyList()) }
    var start by remember { mutableLongStateOf(0L) }       // 0 = todavía no arrancó
    val clock = remember { mutableLongStateOf(0L) }          // ms desde que arrancó

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val W = maxWidth; val H = maxHeight
        val gap = W * 0.012f
        val pw = (W - gap * (COLS + 1)) / COLS
        val ph = pw * 1.5f
        val rows = ((H / (ph + gap)).toInt()) + 2
        val count = COLS * rows
        // centro de la estrella dentro del logo (el logo va centrado en la pantalla)
        val sx = W / 2 + LOGO_W * (0.658f - 0.5f)
        val sy = H / 2 + LOGO_H * (0.438f - 0.5f)

        LaunchedEffect(Unit) {
            // primera vez sin catálogo guardado: espera un poquito a que llegue
            val t0 = System.currentTimeMillis()
            while (Store.published().none { isHttp(it.poster) } && System.currentTimeMillis() - t0 < 1500) delay(50)
            val list = Store.published().map { it.poster }.filter { isHttp(it) }.distinct().shuffled()
            posters = list
            // precarga los pósters (máximo ~0,9 s) para que el muro aparezca ya lleno
            if (list.isNotEmpty()) withTimeoutOrNull(900) {
                coroutineScope {
                    (0 until minOf(count, list.size)).map { i ->
                        async { runCatching { ctx.imageLoader.execute(ImageRequest.Builder(ctx).data(list[i]).build()) } }
                    }.awaitAll()
                }
            }
            start = withFrameMillis { it }
            while (clock.longValue < END_MS) withFrameMillis { clock.longValue = it - start }
            nav.section(if (Store.session != null) Screen.Home else Screen.Login(required = true))
        }

        // --- muro de pósters ---
        if (posters.isNotEmpty()) {
            for (i in 0 until count) {
                val r = i / COLS; val c = i % COLS
                val x = gap + (pw + gap) * c
                val y = gap + (ph + gap) * r - ph * 0.35f
                val d = hypot((x + pw / 2 - sx).value, (y + ph / 2 - sy).value) / W.value
                val inDelay = ((i * 7919) % 300).toFloat()
                val outAt = 900f + d * 1100f
                Box(
                    Modifier.offset(x, y).size(pw, ph)
                        .graphicsLayer {
                            val e = clock.longValue.toFloat()
                            val a = easeOut.transform(clamp01((e - inDelay) / 500f))
                            val f = easeIn.transform(clamp01((e - outAt) / 520f))
                            alpha = a * (1f - f)
                            rotationY = 90f * f
                            val s = (0.9f + 0.1f * a) * (1f - 0.15f * f)
                            scaleX = s; scaleY = s
                            cameraDistance = 14f * density
                        }
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color(0xFF15131A))
                        .drawWithContent {
                            drawContent()
                            val e = clock.longValue.toFloat()
                            val f = clamp01((e - outAt) / 520f)
                            if (f > 0f) drawRect(Color.Black.copy(alpha = 0.7f * f))
                        }
                ) { Img(posters[i % posters.size], Modifier.fillMaxSize(), crossfade = false) }
            }
        }

        // --- logo que se abre en círculo desde la estrella ---
        Box(
            Modifier.offset(W / 2 - LOGO_W / 2, H / 2 - LOGO_H / 2).size(LOGO_W, LOGO_H)
                .drawWithContent {
                    val e = clock.longValue.toFloat()
                    val p = easeInOut.transform(clamp01((e - 1550f) / 1100f))
                    if (p <= 0f) return@drawWithContent
                    val cx = size.width * 0.658f; val cy = size.height * 0.438f
                    val maxR = hypot(max(cx, size.width - cx), max(cy, size.height - cy)) * 1.05f
                    val path = Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(cx, cy), maxR * p)) }
                    clipPath(path) { this@drawWithContent.drawContent() }
                }
        ) { LocalLogo(Modifier.fillMaxSize()) }

        // --- estrella ---
        val starSize = LOGO_W * 0.145f
        Canvas(
            Modifier.offset(sx - starSize, sy - starSize).size(starSize * 2)
                .graphicsLayer {
                    val e = clock.longValue.toFloat()
                    val e1 = e - 600f
                    val p = easeOut.transform(clamp01(e1 / 900f))
                    val q = clamp01((e - 1650f) / 500f)
                    alpha = clamp01(e1 / 225f) * (1f - q)
                    val s = (0.1f + 0.9f * p) * (1f - 0.52f * q)
                    scaleX = s; scaleY = s
                    rotationZ = -120f * (1f - p)
                }
        ) {
            val ctr = Offset(size.width / 2, size.height / 2)
            drawCircle(Brush.radialGradient(listOf(LogoYellow.copy(alpha = .45f), Color.Transparent), ctr, size.minDimension / 2), size.minDimension / 2, ctr)
            drawPath(starPath(ctr, size.minDimension / 4f), LogoYellow)
        }

        // --- frase ---
        Text(
            "TU UNIVERSO DE SERIES Y PELÍCULAS",
            style = ts(15, FontWeight.Light, C.muted).copy(textAlign = TextAlign.Center, letterSpacing = androidx.compose.ui.unit.TextUnit(4f, androidx.compose.ui.unit.TextUnitType.Sp)),
            modifier = Modifier.offset(0.dp, H / 2 + LOGO_H / 2 + 26.dp).size(W, 30.dp)
                .graphicsLayer { alpha = clamp01((clock.longValue - 2300f) / 700f) }
        )
    }
}

/** Estrella de 5 puntas centrada en [c] con radio exterior [r]. */
private fun starPath(c: Offset, r: Float): Path = Path().apply {
    val inner = r * 0.45f
    for (k in 0 until 10) {
        val rad = if (k % 2 == 0) r else inner
        val ang = Math.toRadians((-90 + k * 36).toDouble())
        val px = c.x + (rad * cos(ang)).toFloat(); val py = c.y + (rad * sin(ang)).toFloat()
        if (k == 0) moveTo(px, py) else lineTo(px, py)
    }
    close()
}

