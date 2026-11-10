package com.realityvault.tv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.delay

/* Archivo compartido con la app del celular (se copia tal cual, ver copyShared en app-mobile). */

/* ---------------- desenfoque tipo Apple TV ---------------- */

/*
 * La portada de la ficha se arma UNA sola vez como imagen final, en segundo plano, y queda en caché:
 * en pantalla es una foto común (cero capas, cero desenfoque en vivo). Y se arma con las operaciones
 * nativas de dibujo de Android (no pixel por pixel), así tarda muy poco y casi no usa memoria.
 *
 * El desenfoque es progresivo DE VERDAD: seis niveles donde cada uno duplica al anterior, y a cada altura
 * solo se mezclan dos niveles vecinos. Por eso arranca imperceptible y se va abriendo de a poco hacia
 * abajo, sin el "doble contorno" que aparece cuando se funde una foto nítida con una muy borrosa.
 */
internal object Blur {
    private val smooth = floatArrayOf(0f, .104f, .5f, .896f, 1f)   // curva suave (smoothstep) en 5 puntos

    /** Degradé vertical de transparencia entre a y b (fracciones de la altura), con entrada y salida suaves. */
    fun ramp(h: Int, a: Float, b: Float, from: Int, to: Int): android.graphics.Shader {
        val pos = FloatArray(5) { (a + (b - a) * it / 4f).coerceIn(0f, 1f) }
        val col = IntArray(5) { android.graphics.Color.argb((from + (to - from) * smooth[it]).toInt(), 0, 0, 0) }
        return android.graphics.LinearGradient(0f, 0f, 0f, h.toFloat(), col, pos, android.graphics.Shader.TileMode.CLAMP)
    }

    /** Recorta como ContentScale.Crop alineado arriba al centro, a w×h exactos. */
    fun cover(src: android.graphics.Bitmap, w: Int, h: Int): android.graphics.Bitmap {
        val out = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        val s = maxOf(w / src.width.toFloat(), h / src.height.toFloat())
        val m = android.graphics.Matrix().apply { setScale(s, s); postTranslate((w - src.width * s) / 2f, 0f) }
        android.graphics.Canvas(out).drawBitmap(src, m, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
        return out
    }

    /** La misma imagen achicada a la mitad una y otra vez (cada paso es, de por sí, un desenfoque). */
    fun pyramid(img: android.graphics.Bitmap, steps: Int): List<android.graphics.Bitmap> {
        val out = ArrayList<android.graphics.Bitmap>(); var cur = img
        repeat(steps) {
            cur = android.graphics.Bitmap.createScaledBitmap(cur, maxOf(2, cur.width / 2), maxOf(2, cur.height / 2), true)
            out += cur
        }
        return out
    }

    /** Nivel k vuelto a agrandar de a un paso por vez: queda parejo, sin cuadraditos ni rombos. */
    fun up(pyr: List<android.graphics.Bitmap>, k: Int, w: Int, h: Int): android.graphics.Bitmap {
        var cur = soften(pyr[k])
        for (i in k - 1 downTo 0) cur = android.graphics.Bitmap.createScaledBitmap(cur, pyr[i].width, pyr[i].height, true)
        return android.graphics.Bitmap.createScaledBitmap(cur, w, h, true)
    }

    /** Un suavizado chiquito sobre la copia chica, para borrar el escalonado de achicar a la mitad. */
    private fun soften(b: android.graphics.Bitmap): android.graphics.Bitmap {
        val w = b.width; val h = b.height
        if (w > 400) return b
        val a = IntArray(w * h); b.getPixels(a, 0, w, 0, 0, w, h); val t = IntArray(w * h)
        fun pass(inp: IntArray, out: IntArray, horiz: Boolean) {
            val n = if (horiz) w else h; val lines = if (horiz) h else w
            for (l in 0 until lines) {
                val base = if (horiz) l * w else l; val step = if (horiz) 1 else w
                for (i in 0 until n) {
                    val p0 = inp[base + maxOf(0, i - 1) * step]; val p1 = inp[base + i * step]; val p2 = inp[base + minOf(n - 1, i + 1) * step]
                    val r = (((p0 shr 16) and 255) + 2 * ((p1 shr 16) and 255) + ((p2 shr 16) and 255) + 2) shr 2
                    val g = (((p0 shr 8) and 255) + 2 * ((p1 shr 8) and 255) + ((p2 shr 8) and 255) + 2) shr 2
                    val bl = ((p0 and 255) + 2 * (p1 and 255) + (p2 and 255) + 2) shr 2
                    out[base + i * step] = (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
                }
            }
        }
        pass(a, t, true); pass(t, a, false)
        return android.graphics.Bitmap.createBitmap(a, w, h, android.graphics.Bitmap.Config.ARGB_8888)
    }

    /** Sombra suave a la izquierda (donde va el texto en la tele). */
    fun leftShade(w: Int): android.graphics.Shader = android.graphics.LinearGradient(0f, 0f, w * .58f, 0f,
        intArrayOf(android.graphics.Color.argb(70, 0, 0, 0), android.graphics.Color.argb(34, 0, 0, 0), 0), floatArrayOf(0f, .5f, 1f), android.graphics.Shader.TileMode.CLAMP)

    /** Grano finísimo: evita las "bandas" que aparecen en los degradés oscuros y le da textura, como en Apple TV. */
    val grain: android.graphics.Shader by lazy {
        val n = 96; val rnd = java.util.Random(7); val px = IntArray(n * n) {
            val a = rnd.nextInt(7); if (rnd.nextBoolean()) android.graphics.Color.argb(a, 255, 255, 255) else android.graphics.Color.argb(a, 0, 0, 0)
        }
        android.graphics.BitmapShader(android.graphics.Bitmap.createBitmap(px, n, n, android.graphics.Bitmap.Config.ARGB_8888), android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT)
    }
}

private const val BLUR_LEVELS = 7

/**
 * Portada final: nítida arriba, desenfoque progresivo hacia abajo desde [from] (fracción de la altura),
 * sombra leve y borde de abajo desvanecido. [left]: sombra a la izquierda (tele, texto a la izquierda).
 * [top]: sombra arriba (celular, para el botón de volver).
 */
internal class HeroBlurT(private val w: Int, private val h: Int, private val from: Float, private val left: Boolean, private val top: Boolean, private val dark: Int) : coil.transform.Transformation {
    override val cacheKey = "heroblur-$w-$h-$from-$left-$top-$dark-v5"
    override suspend fun transform(input: android.graphics.Bitmap, size: coil.size.Size): android.graphics.Bitmap {
        val src = if (input.config == android.graphics.Bitmap.Config.HARDWARE) input.copy(android.graphics.Bitmap.Config.ARGB_8888, false) else input
        val out = Blur.cover(src, w, h)                    // arranca siendo la foto nítida
        val c = android.graphics.Canvas(out)
        val pyr = Blur.pyramid(out, BLUR_LEVELS)
        val full = android.graphics.RectF(0f, 0f, w.toFloat(), h.toFloat())
        val p = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
        // un toque más de color en lo desenfocado, como hace Apple
        p.colorFilter = android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix().apply { setSaturation(1.16f) })
        val band = (1f - from) / BLUR_LEVELS
        for (k in 0 until BLUR_LEVELS) {
            val lvl = Blur.up(pyr, k, w, h)
            val a = from + band * k
            p.shader = android.graphics.ComposeShader(
                android.graphics.BitmapShader(lvl, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP),
                Blur.ramp(h, a - band * .25f, a + band * 1.15f, 0, 255), android.graphics.PorterDuff.Mode.DST_IN
            )
            c.drawRect(full, p)
            lvl.recycle()
        }
        pyr.forEach { it.recycle() }
        p.colorFilter = null
        // sombra leve para que el texto se lea: abajo (hasta igualar el fondo de la página)...
        p.shader = android.graphics.LinearGradient(0f, 0f, 0f, h.toFloat(),
            intArrayOf(0, 0, android.graphics.Color.argb(dark * 43 / 100, 0, 0, 0), android.graphics.Color.argb(dark, 0, 0, 0)),
            floatArrayOf(0f, (from - .03f).coerceAtLeast(.01f), from + (1f - from) * .47f, 1f), android.graphics.Shader.TileMode.CLAMP)
        c.drawRect(full, p)
        // ...a la izquierda (tele) y/o arriba (celular)
        if (left) { p.shader = Blur.leftShade(w); c.drawRect(full, p) }
        if (top) {
            p.shader = android.graphics.LinearGradient(0f, 0f, 0f, h * .22f, android.graphics.Color.argb(97, 0, 0, 0), 0, android.graphics.Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w.toFloat(), h * .22f, p)
        }
        p.shader = Blur.grain; c.drawRect(0f, h * (from - .07f).coerceAtLeast(0f), w.toFloat(), h.toFloat(), p)
        // el último tramo se desvanece: debajo queda el fondo de la página, que es la misma imagen desenfocada
        p.shader = Blur.ramp(h, .82f, 1f, 255, 0)
        p.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN)
        c.drawRect(full, p)
        return out
    }
}

/** Fondo de toda la página: la imagen muy desenfocada, oscurecida igual que el pie de la portada. */
internal class PageBlurT(private val w: Int, private val h: Int, private val left: Boolean, private val dark: Int) : coil.transform.Transformation {
    override val cacheKey = "pageblur-$w-$h-$left-$dark-v5"
    override suspend fun transform(input: android.graphics.Bitmap, size: coil.size.Size): android.graphics.Bitmap {
        val src = if (input.config == android.graphics.Bitmap.Config.HARDWARE) input.copy(android.graphics.Bitmap.Config.ARGB_8888, false) else input
        val img = Blur.cover(src, w, h)
        val pyr = Blur.pyramid(img, 5)
        val out = Blur.up(pyr, 4, w, h).let { if (it.isMutable) it else it.copy(android.graphics.Bitmap.Config.ARGB_8888, true) }
        val c = android.graphics.Canvas(out); val p = android.graphics.Paint()
        val full = android.graphics.RectF(0f, 0f, w.toFloat(), h.toFloat())
        p.color = android.graphics.Color.argb(dark, 0, 0, 0); c.drawRect(full, p)
        if (left) { p.shader = Blur.leftShade(w); c.drawRect(full, p) }
        p.shader = Blur.grain; c.drawRect(full, p)
        return out
    }
}

fun heroBlurReq(ctx: android.content.Context, url: String, w: Int, h: Int, from: Float, left: Boolean, top: Boolean, dark: Int = 107) =
    ImageRequest.Builder(ctx).data(url).size(w, h).scale(coil.size.Scale.FILL).transformations(HeroBlurT(w, h, from, left, top, dark)).crossfade(320).build()

fun pageBlurReq(ctx: android.content.Context, url: String, w: Int, h: Int, left: Boolean, dark: Int = 107) =
    ImageRequest.Builder(ctx).data(url).size(w, h).scale(coil.size.Scale.FILL).transformations(PageBlurT(w, h, left, dark)).crossfade(240).build()

/** Fondo fijo de toda la ficha (w×h chicos: es una imagen muy desenfocada que se estira a la pantalla). */
/* dark: cuánto se oscurece el fondo (0-255); tiene que ser el mismo en la portada y en la página para que empalmen. */
@Composable
fun BlurPage(url: String, w: Int, h: Int, left: Boolean, modifier: Modifier = Modifier, dark: Int = 107) {
    val ctx = LocalContext.current
    AsyncImage(model = remember(url, w, h, dark) { pageBlurReq(ctx, url, w, h, left, dark) }, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = modifier.fillMaxSize())
}

/**
 * Portada de la ficha con el desenfoque de Apple TV (nítida arriba, desenfocada hacia abajo, sin capas
 * oscuras) que se desvanece sobre el fondo de la página: nunca se ve dónde termina la imagen.
 * w×h: tamaño en pixeles de la imagen final (la proporción tiene que ser la del lugar donde se dibuja).
 */
@Composable
fun BlurHero(url: String, w: Int, h: Int, from: Float, left: Boolean, top: Boolean, dark: Int = 107) {
    val ctx = LocalContext.current
    Box(Modifier.fillMaxSize()) {
        var ready by remember(url) { mutableStateOf(false) }
        var swapped by remember(url) { mutableStateOf(false) }
        LaunchedEffect(ready) { if (ready) { delay(380); swapped = true } }
        // si la versión final todavía no está (pasa solo la primera vez, un instante), se ve la foto común
        // con el borde de abajo desvanecido, y la final aparece fundiéndose encima
        if (!swapped) AsyncImage(
            model = remember(url) { ImageRequest.Builder(ctx).data(url).build() }, contentDescription = null,
            contentScale = ContentScale.Crop, alignment = Alignment.TopCenter,
            modifier = Modifier.fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent { drawContent(); drawRect(Brush.verticalGradient(.70f to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn) }
        )
        AsyncImage(
            model = remember(url, w, h, dark) { heroBlurReq(ctx, url, w, h, from, left, top, dark) },
            contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize(),
            onSuccess = { ready = true }
        )
    }
}

/** Arma de antemano (en segundo plano) las imágenes de una ficha, para que al abrirla aparezcan al instante. */
fun prewarmBlur(ctx: android.content.Context, url: String, w: Int, h: Int, from: Float, left: Boolean, top: Boolean, pw: Int, ph: Int) {
    ctx.imageLoader.enqueue(pageBlurReq(ctx, url, pw, ph, left)); ctx.imageLoader.enqueue(heroBlurReq(ctx, url, w, h, from, left, top))
}
