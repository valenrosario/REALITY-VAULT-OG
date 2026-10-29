package com.realityvault.tv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest

/* ---------------- colores y letras (los mismos del sitio) ---------------- */

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
    val pause = ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .addPath(pathData = addPathNodes("M6.5 4.5h4v15h-4zM13.5 4.5h4v15h-4z"), fill = SolidColor(Color.White)).build()
    val info = icon("M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0", "M12 11v6", "M12 7.6v0.2", width = 2f)
    val chevron = icon("M9 5l7 7l-7 7", width = 2.6f)
    val back = icon("M15 5l-7 7l7 7", width = 2.6f)
    val heart = icon("M12 20s-7.5 -4.6 -7.5 -10.2A4.3 4.3 0 0 1 12 7a4.3 4.3 0 0 1 7.5 2.8C19.5 15.4 12 20 12 20z")
}

/* ---------------- elemento enfocable con el control ---------------- */

/**
 * Todo lo que se elige con el control pasa por acá: al enfocarse se agranda un poco
 * y lleva un borde blanco (como Disney+). Va acelerado por la GPU para que sea fluido.
 */
@Composable
fun Focusable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(10.dp),
    scale: Float = 1.08f,
    ring: Boolean = true,
    lift: Boolean = false,
    clipContent: Boolean = true,
    requester: FocusRequester? = null,
    onFocus: (() -> Unit)? = null,
    // Las tarjetas de las filas (pósters, episodios...) viven dentro de una lista que se
    // desplaza sola para mantener la fila enfocada en su lugar (ver PivotSpec en Apple.kt).
    // Si la tarjeta crece desde su centro al enfocarse, su borde de arriba sube un poco
    // durante toda la animación de agrandado, y eso hace que esa lista se reacomode sola
    // cuadro a cuadro — se siente como un tembleque en toda la pantalla cada vez que te
    // movés para el costado. Agrandándola desde arriba en vez del centro, el borde de
    // arriba no se mueve, así que no hay nada que corregir.
    pivotTop: Boolean = false,
    content: @Composable BoxScope.(focused: Boolean) -> Unit
) {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    val s by animateFloatAsState(if (focused) scale else 1f, tween(160), label = "s")
    Box(
        modifier
            .graphicsLayer {
                scaleX = s; scaleY = s
                if (pivotTop) transformOrigin = TransformOrigin(0.5f, 0f)
            }
            .drawWithContent {
                drawContent()
                if (focused && ring) {
                    // el contorno sigue SIEMPRE la forma real del botón (círculo, píldora o esquina
                    // redondeada), calculado desde la misma shape que recorta el contenido —
                    // nunca un radio adivinado que pueda quedar cuadrado sobre un botón redondo.
                    val g = 3.5.dp.toPx(); val w = 2.dp.toPx()
                    inset(-g, -g) {
                        drawOutline(shape.createOutline(size, layoutDirection, this), Color.White, style = Stroke(w))
                    }
                }
            }
            .then(if (lift && focused) Modifier.shadow(14.dp, shape) else Modifier)
            .then(if (clipContent) Modifier.clip(shape) else Modifier)
            .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
            .onFocusChanged { if (it.isFocused) onFocus?.invoke() }
            .clickable(interactionSource = src, indication = null, onClick = onClick)
    ) { content(focused) }
}

/** Pide el foco apenas aparece (sin romperse si todavía no está en pantalla). */
@Composable
fun AutoFocus(requester: FocusRequester, key: Any? = Unit) {
    LaunchedEffect(key) {
        repeat(6) {
            if (runCatching { requester.requestFocus() }.isSuccess) return@LaunchedEffect
            kotlinx.coroutines.delay(60)
        }
    }
}

/* ---------------- botones (opción 1: píldora blanca + círculos con borde fino) ---------------- */

@Composable
fun PillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = Ic.play, requester: FocusRequester? = null, onFocus: (() -> Unit)? = null) {
    Focusable(onClick, modifier.height(38.dp), shape = RoundedCornerShape(99.dp), scale = 1.06f, requester = requester, onFocus = onFocus) {
        Row(
            Modifier.background(Color.White).padding(horizontal = 20.dp).height(38.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) { Icon(icon, null, tint = Color.Black, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(8.dp)) }
            Text(text, style = ts(14, FontWeight.Bold, Color.Black), maxLines = 1)
        }
    }
}

@Composable
fun RoundButton(icon: ImageVector, desc: String, onClick: () -> Unit, modifier: Modifier = Modifier, onFocus: (() -> Unit)? = null) {
    Focusable(onClick, modifier.size(38.dp), shape = CircleShape, scale = 1.1f, ring = false, onFocus = onFocus) { f ->
        Box(
            Modifier.fillMaxSize()
                .background(if (f) Color.White else Color.White.copy(alpha = .12f), CircleShape)
                .border(1.5.dp, if (f) Color.White else Color.White.copy(alpha = .45f), CircleShape),
            contentAlignment = Alignment.Center
        ) { Icon(icon, desc, tint = if (f) Color.Black else Color.White, modifier = Modifier.size(17.dp)) }
    }
}

@Composable
fun TextButtonTv(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, requester: FocusRequester? = null) {
    Focusable(onClick, modifier.height(38.dp), shape = RoundedCornerShape(99.dp), scale = 1.06f, ring = false, requester = requester) { f ->
        Box(
            Modifier.background(if (f) Color.White else Color.White.copy(alpha = .12f)).padding(horizontal = 18.dp).height(38.dp),
            contentAlignment = Alignment.Center
        ) { Text(text, style = ts(14, FontWeight.Bold, if (f) Color.Black else C.fg), maxLines = 1) }
    }
}

/* ---------------- teclado en pantalla (nada de teclado de sistema / formulario de escritorio) ---------------- */

private val KB_ROWS = listOf("qwertyuiop", "asdfghjklñ", "zxcvbnm@.-")

@Composable
fun TvKeyboard(
    onChar: (String) -> Unit,
    onBackspace: () -> Unit,
    onDone: () -> Unit,
    doneLabel: String,
    modifier: Modifier = Modifier,
    firstReq: FocusRequester? = null
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        KB_ROWS.forEachIndexed { ri, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEachIndexed { ci, ch ->
                    KeyTv(ch.toString(), Modifier.width(42.dp).height(42.dp), requester = if (ri == 0 && ci == 0) firstReq else null) { onChar(ch.toString()) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KeyTv("espacio", Modifier.weight(2.4f).height(42.dp)) { onChar(" ") }
            KeyTv("⌫ borrar", Modifier.weight(2.4f).height(42.dp)) { onBackspace() }
            KeyTv(doneLabel, Modifier.weight(2.4f).height(42.dp), accent = true) { onDone() }
        }
    }
}

@Composable
private fun KeyTv(label: String, modifier: Modifier, accent: Boolean = false, requester: FocusRequester? = null, onClick: () -> Unit) {
    Focusable(onClick, modifier, shape = RoundedCornerShape(10.dp), scale = 1.1f, ring = false, requester = requester) { f ->
        Box(
            Modifier.fillMaxSize()
                .background(if (f) Color.White else if (accent) C.accent.copy(alpha = .2f) else Color.White.copy(alpha = .08f), RoundedCornerShape(10.dp))
                .then(if (accent && !f) Modifier.border(1.dp, C.accent.copy(alpha = .55f), RoundedCornerShape(10.dp)) else Modifier),
            contentAlignment = Alignment.Center
        ) { Text(label, style = ts(14, FontWeight.Bold, if (f) Color.Black else if (accent) C.accent else C.fg)) }
    }
}

/** Campo que se elige con el control (no abre teclado del sistema); el que está activo recibe lo que se escribe en TvKeyboard. */
@Composable
fun TvFieldChip(label: String, value: String, active: Boolean, masked: Boolean = false, onClick: () -> Unit) {
    Focusable(onClick, Modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(12.dp), scale = 1f, ring = false) {
        Column(
            Modifier.fillMaxSize()
                .background(if (active) C.accent.copy(alpha = .1f) else Color.White.copy(alpha = .05f), RoundedCornerShape(12.dp))
                .border(1.5.dp, if (active) C.accent.copy(alpha = .75f) else Color.White.copy(alpha = .18f), RoundedCornerShape(12.dp))
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(label, style = ts(10, FontWeight.Bold, C.muted))
            val shown = if (masked) "•".repeat(value.length) else value
            Text(shown.ifBlank { " " }, style = ts(16, color = C.fg), maxLines = 1)
        }
    }
}

/** Igual que arriba pero de solo lectura (para un único campo, como buscar). */
@Composable
fun TvFieldDisplay(label: String, value: String, placeholder: String) {
    Column(
        Modifier.fillMaxWidth().height(58.dp)
            .background(C.accent.copy(alpha = .1f), RoundedCornerShape(12.dp))
            .border(1.5.dp, C.accent.copy(alpha = .6f), RoundedCornerShape(12.dp))
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(label, style = ts(10, FontWeight.Bold, C.muted))
        Text(value.ifBlank { placeholder }, style = ts(16, color = if (value.isBlank()) C.faint else C.fg), maxLines = 1)
    }
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
            modifier = Modifier.padding(12.dp), maxLines = 3, overflow = TextOverflow.Ellipsis
        )
    }
}

/** Logo de la serie, o el nombre si no tiene logo. */
@Composable
fun TitleLogo(t: Title, maxW: Dp, maxH: Dp, textSize: Int = 40) {
    if (t.logo.isNotBlank()) {
        Img(t.logo, Modifier.width(maxW).height(maxH), scale = ContentScale.Fit, alignment = Alignment.BottomStart)
    } else {
        Text(t.title, style = ts(textSize, FontWeight.Bold, Color.White), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(maxW))
    }
}

@Composable
fun MetaLine(t: Title) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (t.rating.isNotBlank()) {
            Box(Modifier.border(1.dp, C.fg2, RoundedCornerShape(3.dp)).padding(horizontal = 5.dp, vertical = 1.dp)) {
                Text(t.rating, style = ts(10, FontWeight.Bold, C.fg2))
            }
            Spacer(Modifier.width(10.dp))
        }
        Text(t.meta(), style = ts(12, color = C.fg2), maxLines = 1)
    }
}

/** Degradados para que el texto se lea sobre la imagen. */
val leftScrim = Brush.horizontalGradient(0f to Color.Black, .32f to Color.Black.copy(alpha = .88f), .6f to Color.Black.copy(alpha = .35f), 1f to Color.Transparent)
val bottomScrim = Brush.verticalGradient(0f to Color.Transparent, .55f to Color.Black.copy(alpha = .35f), 1f to Color.Black)
