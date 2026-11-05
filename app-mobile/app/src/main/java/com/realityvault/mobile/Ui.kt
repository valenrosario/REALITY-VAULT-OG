package com.realityvault.mobile

import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.composed
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.realityvault.tv.Formats
import com.realityvault.tv.R
import com.realityvault.tv.Title

/* ---------------- colores y letras (los mismos del sitio y de la app de TV) ---------------- */

object C {
    val bg = Color(0xFF000000)
    val surface = Color(0xFF1A1518)
    val fg = Color(0xFFFDEEF4)
    val fg2 = Color(0xFFECD3DE)
    val muted = Color(0xFFC6A3B3)
    val faint = Color(0xFF8C6B7A)
    val accent = Color(0xFFF5A3C4)
}

val Grotesk = FontFamily(
    Font(R.font.gg_bold, FontWeight.Bold),
    Font(R.font.gg_book, FontWeight.Normal),
    Font(R.font.gg_light, FontWeight.Light)
)

fun ts(size: Number, weight: FontWeight = FontWeight.Normal, color: Color = C.fg) =
    TextStyle(fontFamily = Grotesk, fontWeight = weight, fontSize = size.toFloat().sp, color = color)

/* ---------------- íconos (trazos iguales a los del sitio) ---------------- */

private fun icon(vararg paths: String, width: Float = 2.2f): ImageVector {
    val b = ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
    paths.forEach {
        b.addPath(
            pathData = addPathNodes(it), stroke = SolidColor(Color.White), strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        )
    }
    return b.build()
}

private fun filled(path: String): ImageVector =
    ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .addPath(pathData = addPathNodes(path), fill = SolidColor(Color.White)).build()

object Ic {
    val search = icon("M4 11a7 7 0 1 0 14 0a7 7 0 1 0 -14 0", "M20 20l-3.5 -3.5")
    val home = icon("M3.5 10.5L12 4l8.5 6.5V20h-5.5v-5.5h-6V20H3.5z")
    val tv = icon("M3 8a2 2 0 0 1 2 -2h14a2 2 0 0 1 2 2v8.5a2 2 0 0 1 -2 2H5a2 2 0 0 1 -2 -2z", "M8.5 2.5L12 6l3.5 -3.5")
    val film = icon("M5 4h14a2 2 0 0 1 2 2v12a2 2 0 0 1 -2 2H5a2 2 0 0 1 -2 -2V6a2 2 0 0 1 2 -2z", "M7.5 4v16M16.5 4v16M3 9h4.5M3 15h4.5M16.5 9H21M16.5 15H21")
    val plus = icon("M12 5v14M5 12h14", width = 2.4f)
    val check = icon("M5 12.5l4.5 4.5L19 7.5", width = 2.4f)
    val user = icon("M8 8a4 4 0 1 0 8 0a4 4 0 1 0 -8 0", "M4 20.5c1.5 -4 4.5 -6 8 -6s6.5 2 8 6")
    val clapper = icon("M3.5 10h17v9.5a1.5 1.5 0 0 1 -1.5 1.5H5a1.5 1.5 0 0 1 -1.5 -1.5z", "M3.2 9.9L2.6 7.3a1.5 1.5 0 0 1 1.1 -1.8l13.6 -3.2a1.5 1.5 0 0 1 1.8 1.1l0.6 2.6z", "M7.6 4.9l2.6 4.1M12.6 3.7l2.6 4.1", width = 1.9f)
    val play = filled("M7 4.5v15l12.5 -7.5z")
    val pause = filled("M6.5 4.5h4v15h-4zM13.5 4.5h4v15h-4z")
    val info = icon("M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0", "M12 11v6", "M12 7.6v0.2", width = 2f)
    val chevron = icon("M9 5l7 7l-7 7", width = 2.6f)
    val back = icon("M15 5l-7 7l7 7", width = 2.6f)
    val heart = icon("M12 20s-7.5 -4.6 -7.5 -10.2A4.3 4.3 0 0 1 12 7a4.3 4.3 0 0 1 7.5 2.8C19.5 15.4 12 20 12 20z")
    val close = icon("M6 6l12 12M18 6L6 18", width = 2.4f)
    val lock = icon("M6 11h12v9H6z", "M8.5 11V8a3.5 3.5 0 0 1 7 0v3")
    val unlock = icon("M6 11h12v9H6z", "M8.5 11V8a3.5 3.5 0 0 1 6.8 -1.2")
    val cc = icon("M3 6a2 2 0 0 1 2 -2h14a2 2 0 0 1 2 2v12a2 2 0 0 1 -2 2H5a2 2 0 0 1 -2 -2z", "M10.4 10.2a2.2 2.2 0 1 0 0 3.6", "M17.4 10.2a2.2 2.2 0 1 0 0 3.6", width = 1.9f)
    val volume = icon("M4 9v6h3.5L12 19V5L7.5 9z", "M15.5 9a4.5 4.5 0 0 1 0 6", "M18 6.5a8 8 0 0 1 0 11", width = 2f)
    val volumeOff = icon("M4 9v6h3.5L12 19V5L7.5 9z", "M16 9.5l5 5M21 9.5l-5 5", width = 2f)
    val sun = icon("M8 12a4 4 0 1 0 8 0a4 4 0 1 0 -8 0", "M12 3v2M12 19v2M3 12h2M19 12h2M5.6 5.6l1.4 1.4M17 17l1.4 1.4M5.6 18.4L7 17M17 7l1.4 -1.4", width = 2f)
    val settings = icon("M4 7h10M18 7h2M4 17h2M10 17h10", "M14 7a2 2 0 1 0 4 0a2 2 0 1 0 -4 0", "M6 17a2 2 0 1 0 4 0a2 2 0 1 0 -4 0", width = 2f)
    val fullscreen = icon("M4 9V4h5M20 9V4h-5M4 15v5h5M20 15v5h-5", width = 2.2f)
    val fullscreenExit = icon("M9 4v5H4M15 4v5h5M9 20v-5H4M15 20v-5h5", width = 2.2f)
    val replay = icon("M4 12a8 8 0 1 0 2.5 -5.8", "M4 4v4h4", width = 2f)
    val forward = icon("M20 12a8 8 0 1 1 -2.5 -5.8", "M20 4v4h-4", width = 2f)
    val next = icon("M6 5l9 7l-9 7z", "M18 5v14", width = 2f)
    val cast = icon("M3 7V6a2 2 0 0 1 2 -2h14a2 2 0 0 1 2 2v12a2 2 0 0 1 -2 2h-6", "M3 11a9 9 0 0 1 9 9", "M3 15a5 5 0 0 1 5 5", "M3 19.5h.5", width = 2f)
    val bell = icon("M6 16V11a6 6 0 0 1 12 0v5l1.5 2h-15z", "M10 20.5a2 2 0 0 0 4 0", width = 2f)
    val audio = icon("M9 18V6l10 -2v12", "M3 18a3 3 0 1 0 6 0a3 3 0 1 0 -6 0", "M13 16a3 3 0 1 0 6 0a3 3 0 1 0 -6 0", width = 2f)
}

/* ---------------- toques con respuesta (se achica un poco al apretar) ---------------- */

fun Modifier.tap(onClick: () -> Unit, scale: Float = 0.96f): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) scale else 1f, tween(110), label = "tap")
    this.graphicsLayer { scaleX = s; scaleY = s }.clickable(interactionSource = src, indication = null, onClick = onClick)
}

/* ---------------- botones ---------------- */

@Composable
fun PillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = Ic.play, dark: Boolean = false) {
    Row(
        modifier.height(46.dp).tap(onClick).clip(RoundedCornerShape(99.dp))
            .background(if (dark) Color.White.copy(alpha = .14f) else Color.White).padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
    ) {
        val ink = if (dark) Color.White else Color.Black
        if (icon != null) { Icon(icon, null, tint = ink, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, style = ts(15, FontWeight.Bold, ink), maxLines = 1)
    }
}

@Composable
fun GlassButton(icon: ImageVector, desc: String, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    Box(
        modifier.size(size).tap(onClick, .9f).clip(CircleShape).background(Color(0x9C3C3C42)),
        contentAlignment = Alignment.Center
    ) { Icon(icon, desc, tint = Color.White, modifier = Modifier.size(size * .43f)) }
}

@Composable
fun Chip(text: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.height(32.dp).tap(onClick).clip(RoundedCornerShape(99.dp))
            .background(if (on) C.accent else Color.White.copy(alpha = .1f)).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, style = ts(12.5f, FontWeight.Bold, if (on) Color.Black else C.fg), maxLines = 1) }
}

/* ---------------- imágenes ---------------- */

@Composable
fun Img(url: String, modifier: Modifier = Modifier, scale: ContentScale = ContentScale.Crop, alignment: Alignment = Alignment.Center, crossfade: Boolean = true) {
    val ctx = LocalContext.current
    AsyncImage(
        model = remember(url) { ImageRequest.Builder(ctx).data(url).crossfade(if (crossfade) 220 else 0).build() },
        contentDescription = null, contentScale = scale, alignment = alignment, modifier = modifier
    )
}

/** Imagen de relleno cuando un título no tiene foto (pastel, como en el sitio). */
@Composable
fun Placeholder(t: Title?, modifier: Modifier = Modifier, big: Boolean = false) {
    val h = ((t?.title ?: "x").fold(7) { a, ch -> (a * 31 + ch.code) and 0xffff } % 360).toFloat()
    val c1 = Color.hsl(h, .62f, .86f); val c2 = Color.hsl((h + 24) % 360, .55f, .78f)
    Box(modifier.background(Brush.linearGradient(listOf(c1, c2))), contentAlignment = Alignment.BottomStart) {
        Text(
            t?.title ?: "", style = ts(if (big) 22 else 12, FontWeight.Bold, Color(0xFF4A2233)),
            modifier = Modifier.padding(10.dp), maxLines = 3, overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun LocalLogo(modifier: Modifier) {
    Image(painterResource(R.drawable.site_logo), null, modifier, contentScale = ContentScale.Fit)
}

/** Logo de la serie, o el nombre si no tiene logo. */
@Composable
fun TitleLogo(t: Title, maxW: Dp, maxH: Dp, textSize: Int = 30, align: Alignment = Alignment.BottomCenter) {
    if (t.logo.isNotBlank()) Img(t.logo, Modifier.width(maxW).height(maxH), scale = ContentScale.Fit, alignment = align)
    else Text(t.title, style = ts(textSize, FontWeight.Bold, Color.White), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(maxW))
}

@Composable
fun PosterCard(t: Title, modifier: Modifier = Modifier) {
    Box(modifier.background(C.surface)) {
        if (t.poster.isNotBlank()) Img(t.poster, Modifier.fillMaxSize()) else Placeholder(t, Modifier.fillMaxSize())
        if (t.isSoon) Text(
            "Próximamente", style = ts(8, FontWeight.Bold, Color.Black),
            modifier = Modifier.align(Alignment.TopStart).padding(6.dp).background(C.accent, RoundedCornerShape(99.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun RatingBox(r: String) {
    Box(Modifier.border(1.dp, Color.White.copy(alpha = .75f), RoundedCornerShape(3.dp)).padding(horizontal = 4.dp)) {
        Text(r, style = ts(10, FontWeight.Bold, Color.White))
    }
}

@Composable
fun KindLine(t: Title, center: Boolean = false) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (center) Arrangement.Center else Arrangement.Start,
        modifier = if (center) Modifier.fillMaxWidth() else Modifier
    ) {
        Text((listOf(t.kind()) + t.genres.take(2)).joinToString(" · "), style = ts(13, color = Color.White.copy(alpha = .88f)), maxLines = 1)
        if (t.rating.isNotBlank()) { Spacer(Modifier.width(9.dp)); RatingBox(t.rating) }
        if (t.formats.any) { Spacer(Modifier.width(9.dp)); FmtBadges(t.formats) }
    }
}

/* ---------------- color de la portada (resplandor de la ficha) ---------------- */

private val tintCache = HashMap<String, Color>()

@Composable
fun rememberTint(url: String): Color {
    val ctx = LocalContext.current
    var c by remember(url) { mutableStateOf(tintCache[url] ?: Color(0xFF1C1A24)) }
    LaunchedEffect(url) {
        if (url.isBlank() || tintCache.containsKey(url)) return@LaunchedEffect
        val r = runCatching { ctx.imageLoader.execute(ImageRequest.Builder(ctx).data(url).size(48).allowHardware(false).build()) }.getOrNull()
        val bmp = (r?.drawable as? BitmapDrawable)?.bitmap ?: return@LaunchedEffect
        val w = bmp.width; val h = bmp.height
        val px = IntArray(w * h); bmp.getPixels(px, 0, w, 0, 0, w, h)
        val hist = FloatArray(36); val hsv = FloatArray(3)
        px.forEach { p -> android.graphics.Color.colorToHSV(p, hsv); if (hsv[2] > .2f) hist[(hsv[0] / 10).toInt().coerceIn(0, 35)] += hsv[1] * hsv[1] }
        val best = hist.indices.maxByOrNull { hist[it] } ?: 0
        val sat = if (hist[best] < 0.5f) .12f else .55f
        val col = Color.hsl(best * 10f + 5f, sat, .30f)
        tintCache[url] = col; c = col
    }
    return c
}

fun Color.darken(f: Float) = Color(red * (1 - f), green * (1 - f), blue * (1 - f), alpha)
fun Color.lighten(f: Float) = Color(red + (1 - red) * f, green + (1 - green) * f, blue + (1 - blue) * f, alpha)

/** Foto de perfil elegida en la web ("a1" / "a2") → imagen incluida en la app. */
fun avatarRes(key: String?): Int? = when (key) { "a1" -> R.drawable.avatar_a1; "a2" -> R.drawable.avatar_a2; else -> null }

/* ---------------- insignias de formato (4K / HDR / Dolby Atmos / 5.1) ---------------- */

private val FmtInk = Color(0xFFEBEBF5)

private val FourKLogo: ImageVector by lazy {
    ImageVector.Builder(name = "4K", defaultWidth = 26.dp, defaultHeight = 15.dp, viewportWidth = 26f, viewportHeight = 15f)
        .addPath(
            pathData = addPathNodes("M2.737 0h20.528A2.73 2.73 0 0 1 26 2.727v9.545A2.73 2.73 0 0 1 23.265 15H2.737A2.73 2.73 0 0 1 0 12.272V2.727A2.73 2.73 0 0 1 2.737 0m7.078 11.6H11.5v-1.5h1.1V8.681h-1.1v-5.53H9a56 56 0 0 0-3.39 5.442V10.1h4.205zM7.162 8.686A40 40 0 0 1 9.815 4.4h.035v4.333H7.162zm8.273 2.914V9.02l.791-.949 2.507 3.529h2.114L17.5 6.9l3.13-3.749h-1.968l-3.174 3.854h-.053V3.151h-1.769V11.6z"),
            fill = SolidColor(FmtInk), pathFillType = PathFillType.EvenOdd
        ).build()
}

private val DolbyAtmosLogo: ImageVector by lazy {
    ImageVector.Builder(name = "DolbyAtmos", defaultWidth = 52.dp, defaultHeight = 22.dp, viewportWidth = 52f, viewportHeight = 22f)
        .addPath(
            pathData = addPathNodes("m12.114 14.87 3.014 7.085h-1.423l-.653-1.62H10.02l-.632 1.62H7.993l3.044-7.084zm8.386 0v1.101h-2.144v5.984H17.11v-5.984h-2.144v-1.1zm3.5 0 1.818 5.034h.03l1.798-5.033h1.956v7.084h-1.235v-5.693h-.02l-2.124 5.693h-.87l-2.094-5.693h-.02v5.693h-1.186v-7.084zm10.652 0q.78 0 1.438.248.658.247 1.138.705t.748 1.11q.267.651.268 1.46 0 .792-.268 1.444a3.3 3.3 0 0 1-.748 1.123q-.48.471-1.138.733a3.9 3.9 0 0 1-1.438.262q-.772 0-1.425-.262a3.35 3.35 0 0 1-1.128-.733 3.4 3.4 0 0 1-.743-1.123 3.8 3.8 0 0 1-.268-1.443q0-.81.268-1.462a3.2 3.2 0 0 1 .743-1.11 3.3 3.3 0 0 1 1.128-.704 4 4 0 0 1 1.425-.247zm7.359.012q.506 0 1.026.18t.905.541l-.769.854a1.44 1.44 0 0 0-.53-.412 1.57 1.57 0 0 0-1.101-.114 1.3 1.3 0 0 0-.375.161.9.9 0 0 0-.277.285.8.8 0 0 0-.107.422.76.76 0 0 0 .093.39.9.9 0 0 0 .258.275q.165.113.398.2.235.084.516.17.319.105.66.237.343.133.63.352.285.218.468.55t.183.826q0 .541-.197.944a1.94 1.94 0 0 1-.525.67 2.3 2.3 0 0 1-.769.399 3.2 3.2 0 0 1-.928.132 3.4 3.4 0 0 1-1.265-.242q-.619-.241-1.013-.717l.844-.797a1.7 1.7 0 0 0 .651.541q.408.2.802.2.206 0 .422-.053.215-.051.389-.175a1 1 0 0 0 .28-.309.9.9 0 0 0 .109-.46.76.76 0 0 0-.122-.442 1.1 1.1 0 0 0-.328-.303q-.206-.129-.488-.223l-.59-.2q-.3-.094-.6-.228-.3-.132-.54-.35a1.7 1.7 0 0 1-.388-.537q-.15-.318-.15-.793 0-.513.21-.883.212-.369.554-.612.342-.242.778-.36.435-.12.886-.12zm-7.35 1.14a2.08 2.08 0 0 0-1.592.687 2.3 2.3 0 0 0-.447.751 2.7 2.7 0 0 0-.159.934q0 .522.159.957.158.435.452.756.294.32.696.5c.402.18.566.178.891.178q.489 0 .896-.179a2.1 2.1 0 0 0 .7-.499q.294-.321.453-.756.158-.435.158-.957a2.7 2.7 0 0 0-.158-.934 2.3 2.3 0 0 0-.448-.75 2.1 2.1 0 0 0-.696-.505 2.2 2.2 0 0 0-.905-.183m-23.12.35-1.097 2.882h2.174zM46.325 3.15l1.993 4.537 1.993-4.537h1.671l-3.962 8.966a1.973 1.973 0 0 1-2.612 1.017l-.53-.24h-.003l.19-.432.427-.972.22.1a.813.813 0 0 0 1.077-.42l.004-.01.613-1.402.02-.044.056-.125-2.828-6.438zM36.392.002v10.23h-1.53V.002zm2.818 0v3.784a3.45 3.45 0 0 1 2-.638c1.928 0 3.496 1.588 3.496 3.54s-1.568 3.542-3.496 3.542a3.45 3.45 0 0 1-2-.639v.64h-1.532V.002zm-8.834 3.146c1.928 0 3.496 1.588 3.496 3.54s-1.568 3.541-3.496 3.541c-1.929 0-3.497-1.588-3.497-3.54s1.568-3.541 3.497-3.541M1.489 0c2.78 0 5.045 2.295 5.045 5.11s-2.266 5.11-5.045 5.11H0V0zm12.867 0v10.22h-1.488c-2.78 0-5.046-2.295-5.046-5.11S10.088 0 12.868 0zm6.7 0c2.783 0 5.047 2.292 5.047 5.11s-2.264 5.11-5.046 5.11h-3.645V0zm9.32 4.54c-1.157 0-2.109.95-2.109 2.135 0 1.172.939 2.135 2.109 2.135 1.157 0 2.108-.95 2.108-2.135 0-1.171-.951-2.135-2.108-2.135m10.834 0c-.927 0-1.72.61-2 1.462a2.15 2.15 0 0 0 0 1.346 2.11 2.11 0 0 0 2 1.463c1.157 0 2.108-.95 2.108-2.135 0-1.172-.951-2.135-2.108-2.135zM21.057 1.55h-2.115v7.12h2.115c1.937 0 3.516-1.599 3.516-3.56s-1.58-3.56-3.516-3.56"),
            fill = SolidColor(FmtInk)
        ).build()
}

private val DolbyVisionLogo: ImageVector by lazy {
    ImageVector.Builder(name = "DolbyVision", defaultWidth = 52.dp, defaultHeight = 22.dp, viewportWidth = 52f, viewportHeight = 22f)
        .addPath(
            pathData = addPathNodes("m9.72 14.87 1.927 5.454h.04l1.946-5.453h1.344l-2.806 7.084h-1.077l-2.767-7.084zm7.839 0v7.085h-1.245v-7.084zm9.369 0v7.085h-1.245v-7.084zm5.227 0q.78 0 1.438.248.658.247 1.138.705t.748 1.11.268 1.46q0 .792-.268 1.444a3.3 3.3 0 0 1-.748 1.123q-.48.471-1.138.733a3.9 3.9 0 0 1-1.438.262q-.773 0-1.425-.262a3.35 3.35 0 0 1-1.128-.733 3.4 3.4 0 0 1-.743-1.123 3.8 3.8 0 0 1-.268-1.443q0-.81.268-1.462.267-.651.743-1.109.474-.458 1.128-.705.652-.247 1.425-.247zm6.985 0 3.25 5.304h.02v-5.303h1.245v7.084h-1.58l-3.32-5.473h-.02v5.473h-1.246v-7.084zm-17.28.002q.506 0 1.026.18.416.145.746.405l.159.137-.769.854a1.44 1.44 0 0 0-.53-.413 1.57 1.57 0 0 0-1.1-.114 1.3 1.3 0 0 0-.376.162.9.9 0 0 0-.276.284.8.8 0 0 0-.108.423.76.76 0 0 0 .351.664q.164.114.399.2.234.085.515.17.32.105.661.238.343.133.628.351c.285.218.347.33.469.55q.183.333.183.826 0 .541-.197.945a1.94 1.94 0 0 1-.525.67 2.2 2.2 0 0 1-.769.398 3.2 3.2 0 0 1-.928.133 3.4 3.4 0 0 1-1.265-.242 2.5 2.5 0 0 1-.875-.565l-.138-.152.844-.797q.243.342.652.54.407.2.801.2.206 0 .422-.052a1.1 1.1 0 0 0 .389-.176 1 1 0 0 0 .281-.308.9.9 0 0 0 .108-.46.76.76 0 0 0-.122-.442 1.1 1.1 0 0 0-.328-.304q-.207-.128-.488-.223l-.59-.2q-.3-.093-.6-.227-.3-.133-.54-.351a1.7 1.7 0 0 1-.388-.537q-.15-.318-.15-.792 0-.513.21-.883.212-.37.554-.612.342-.243.778-.361.435-.12.886-.119m10.304 1.15q-.498 0-.9.183-.402.184-.692.504a2.3 2.3 0 0 0-.447.751 2.7 2.7 0 0 0-.159.934q0 .523.159.957.158.435.452.756.294.32.696.5c.402.18.566.178.891.178q.489 0 .895-.179c.406-.179.505-.285.701-.499q.294-.321.453-.756.158-.435.158-.957a2.7 2.7 0 0 0-.158-.934 2.3 2.3 0 0 0-.448-.75 2.1 2.1 0 0 0-.697-.505 2.2 2.2 0 0 0-.904-.183M46.325 3.15l1.993 4.536 1.993-4.536h1.671l-3.962 8.966a1.975 1.975 0 0 1-2.47 1.075l-.142-.058-.53-.24h-.003l.19-.431.428-.973.219.1a.81.81 0 0 0 1.025-.32l.052-.1.004-.01.613-1.402.02-.044.057-.125-2.829-6.438zM39.21.002v3.784a3.45 3.45 0 0 1 2-.638c1.928 0 3.496 1.588 3.496 3.541s-1.568 3.54-3.496 3.54a3.45 3.45 0 0 1-2-.638v.64h-1.532V.002zm-2.818 0v10.23h-1.53V.002zm-6.016 3.146c1.928 0 3.496 1.588 3.496 3.54 0 1.953-1.568 3.541-3.496 3.541-1.929 0-3.497-1.588-3.497-3.54 0-1.953 1.568-3.541 3.497-3.541M21.057 0c2.782 0 5.046 2.292 5.046 5.11s-2.264 5.11-5.046 5.11h-3.645V0zm-6.7 0v10.22h-1.49c-2.78 0-5.045-2.295-5.045-5.11 0-2.743 2.151-4.992 4.833-5.105L12.868 0zM1.488 0c2.78 0 5.046 2.295 5.046 5.11 0 2.743-2.152 4.991-4.833 5.105l-.213.004h-1.49V0zm39.72 4.541c-.926 0-1.719.61-1.999 1.461a2.15 2.15 0 0 0 0 1.346 2.11 2.11 0 0 0 2 1.463c1.157 0 2.108-.95 2.108-2.135 0-1.172-.951-2.135-2.108-2.135zm-10.833 0c-1.157 0-2.109.95-2.109 2.134 0 1.172.939 2.135 2.109 2.135 1.157 0 2.108-.95 2.108-2.135 0-1.171-.951-2.135-2.108-2.135zm-9.32-2.992h-2.114V8.67h2.115c1.937 0 3.516-1.599 3.516-3.56s-1.58-3.56-3.516-3.56z"),
            fill = SolidColor(FmtInk)
        ).build()
}

@Composable
private fun FmtLogo(icon: ImageVector, heightDp: Dp, vwVh: Float, desc: String) {
    Image(icon, desc, modifier = Modifier.height(heightDp).width(heightDp * vwVh))
}

@Composable
fun FmtBadges(f: Formats) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (f.hd) Box(Modifier.border(1.dp, Color.White.copy(alpha = .75f), RoundedCornerShape(3.dp)).padding(horizontal = 4.dp)) {
            Text("HD", style = ts(10, FontWeight.Bold, Color.White))
        }
        if (f.uhd4k) FmtLogo(FourKLogo, 15.dp, 26f / 15f, "4K Ultra HD")
        if (f.hdr) FmtLogo(DolbyVisionLogo, 13.dp, 52f / 22f, "Dolby Vision")
        if (f.atmos) FmtLogo(DolbyAtmosLogo, 13.dp, 52f / 22f, "Dolby Atmos")
        if (f.surround51) Box(Modifier.border(1.dp, Color.White.copy(alpha = .75f), RoundedCornerShape(3.dp)).padding(horizontal = 4.dp)) {
            Text("5.1", style = ts(10, FontWeight.Bold, Color.White))
        }
    }
}

