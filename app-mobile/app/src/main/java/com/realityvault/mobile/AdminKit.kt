package com.realityvault.mobile

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min

/* ---------------- piezas del panel de control ---------------- */

/** Documento en edición: el JSON original + un contador para redibujar al cambiar algo. */
class Doc(val o: JSONObject) {
    var v by mutableIntStateOf(0)
    var dirty by mutableStateOf(false)
    fun touch() { v++; dirty = true }
    fun str(k: String, src: JSONObject = o): String { v; return src.optString(k).let { if (it == "null") "" else it } }
    fun bool(k: String, src: JSONObject = o): Boolean { v; return src.optBoolean(k) }
    fun set(k: String, value: Any?, src: JSONObject = o) { src.put(k, value); touch() }
}

/** Aviso breve abajo de todo ("Guardado", "No se pudo…"). */
object AdminToast {
    var msg by mutableStateOf<String?>(null)
    var bad by mutableStateOf(false)
    var tick by mutableIntStateOf(0)
    fun show(m: String, error: Boolean = false) { msg = m; bad = error; tick++ }
}

@Composable
fun AdminToastHost(modifier: Modifier = Modifier) {
    val t = AdminToast.tick
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(t) { if (t > 0) { visible = true; delay(2600); visible = false } }
    AnimatedVisibility(visible, modifier, enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut() + slideOutVertically { it / 2 }) {
        Row(
            Modifier.navigationBarsPadding().padding(16.dp).clip(RoundedCornerShape(14.dp))
                .background(if (AdminToast.bad) Color(0xFF4A1620) else Color(0xFF232026)).border(1.dp, Color.White.copy(alpha = .12f), RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(if (AdminToast.bad) Ic.close else Ic.check, null, tint = if (AdminToast.bad) Color(0xFFFF8A8A) else C.accent, modifier = Modifier.size(18.dp))
            Text(AdminToast.msg ?: "", style = ts(14, FontWeight.Bold, Color.White), modifier = Modifier.padding(start = 10.dp))
        }
    }
}

/** Encabezado de cada pantalla del panel. */
@Composable
fun AdminTop(title: String, sub: String?, nav: Nav, onBack: (() -> Unit)? = null, actions: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 12.dp, end = 14.dp, top = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        GlassButton(Ic.back, "Volver", { onBack?.invoke() ?: nav.back() }, size = 40.dp)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(title, style = ts(21, FontWeight.Bold, Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!sub.isNullOrBlank()) Text(sub, style = ts(12, color = C.muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        actions()
    }
}

/** Tarjeta de sección con título. */
@Composable
fun ACard(title: String? = null, hint: String? = null, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = .055f)).padding(16.dp)) {
        if (title != null) Text(title, style = ts(15.5f, FontWeight.Bold, Color.White))
        if (hint != null) Text(hint, style = ts(12.5f, color = C.muted).copy(lineHeight = 17.sp), modifier = Modifier.padding(top = 3.dp))
        if (title != null || hint != null) Spacer(Modifier.height(12.dp))
        content()
    }
}

/** Campo de texto del panel (con etiqueta arriba). */
@Composable
fun AField(label: String, value: String, onChange: (String) -> Unit, placeholder: String = "", lines: Int = 1, number: Boolean = false, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Text(label.uppercase(), style = ts(10.5f, FontWeight.Bold, C.muted).copy(letterSpacing = .6.sp), modifier = Modifier.padding(start = 2.dp, bottom = 5.dp))
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = .35f)).border(1.dp, Color.White.copy(alpha = .14f), RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 12.dp)) {
            if (value.isEmpty() && placeholder.isNotEmpty()) Text(placeholder, style = ts(15, color = Color.White.copy(alpha = .3f)), maxLines = lines)
            BasicTextField(
                value, onChange, textStyle = ts(15, color = Color.White), singleLine = lines == 1, minLines = if (lines > 1) lines else 1,
                cursorBrush = androidx.compose.ui.graphics.SolidColor(C.accent),
                keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Number else KeyboardType.Text),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** Interruptor con texto, estilo iOS/One UI. */
@Composable
fun AToggle(label: String, hint: String? = null, on: Boolean, onChange: (Boolean) -> Unit) {
    val x by animateFloatAsState(if (on) 1f else 0f, tween(180), label = "sw")
    Row(Modifier.fillMaxWidth().tap({ onChange(!on) }, .99f).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 14.dp)) {
            Text(label, style = ts(15, FontWeight.Bold, Color.White))
            if (hint != null) Text(hint, style = ts(12, color = C.muted), modifier = Modifier.padding(top = 2.dp))
        }
        Box(Modifier.width(50.dp).height(30.dp).clip(RoundedCornerShape(99.dp)).background(if (on) C.accent else Color.White.copy(alpha = .18f)).padding(3.dp)) {
            Box(Modifier.padding(start = (20 * x).dp).size(24.dp).background(Color.White, CircleShape))
        }
    }
}

/** Opciones en píldoras (elige una). */
@Composable
fun ASegment(options: List<Pair<String, String>>, selected: String, onPick: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = .35f)).padding(4.dp)) {
        options.forEach { (k, label) ->
            val on = k == selected
            Box(Modifier.weight(1f).clip(RoundedCornerShape(9.dp)).background(if (on) Color.White else Color.Transparent).tap({ onPick(k) }, .97f).padding(vertical = 9.dp), contentAlignment = Alignment.Center) {
                Text(label, style = ts(13, FontWeight.Bold, if (on) Color.Black else Color.White.copy(alpha = .75f)), maxLines = 1)
            }
        }
    }
}

/** Botón chico redondeado. */
@Composable
fun AButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, danger: Boolean = false, primary: Boolean = false, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    Row(
        modifier.clip(RoundedCornerShape(99.dp)).background(when { primary -> Color.White; danger -> Color(0xFF5A1A26); else -> Color.White.copy(alpha = .1f) })
            .tap(onClick, .95f).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) { Icon(icon, null, tint = if (primary) Color.Black else if (danger) Color(0xFFFF9AA8) else Color.White, modifier = Modifier.size(15.dp)); Spacer(Modifier.width(7.dp)) }
        Text(text, style = ts(13.5f, FontWeight.Bold, if (primary) Color.Black else if (danger) Color(0xFFFF9AA8) else Color.White), maxLines = 1)
    }
}

/** Confirmación antes de borrar. */
@Composable
fun AConfirm(text: String, sub: String? = null, yes: String = "Eliminar", onYes: () -> Unit, onNo: () -> Unit) {
    BackHandler { onNo() }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .7f)).tap(onNo, 1f), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(28.dp).widthIn(max = 380.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xFF1C181B)).tap({}, 1f).padding(22.dp)) {
            Text(text, style = ts(18, FontWeight.Bold, Color.White))
            if (sub != null) Text(sub, style = ts(13.5f, color = C.fg2), modifier = Modifier.padding(top = 6.dp))
            Row(Modifier.padding(top = 20.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                AButton("Cancelar", onNo)
                AButton(yes, onYes, danger = true)
            }
        }
    }
}

/* ---------------- imágenes: elegir, recortar y subir ---------------- */

/**
 * Casilla de imagen: muestra la imagen en su proporción real. Al tocarla se elige una foto de la galería,
 * se recorta con los dedos en la proporción exacta que usa la app y se sube.
 */
@Composable
fun AImage(label: String, kind: String, url: String, hint: String? = null, ratio: Float = AdminData.ASPECT[kind] ?: (16f / 9f), contain: Boolean = false,
           modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var picked by remember { mutableStateOf<Bitmap?>(null) }
    var busy by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) scope.launch {
            try {
                val b = AdminData.decode(ctx, uri)
                if (AdminData.ASPECT[kind] == null) { busy = true; onChange(AdminData.upload(b, kind, null)); AdminToast.show("Imagen subida") } else picked = b
            } catch (e: Exception) { AdminToast.show(e.message ?: "No se pudo abrir la imagen", true) }
            busy = false
        }
    }
    Column(modifier.padding(bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label.uppercase(), style = ts(10.5f, FontWeight.Bold, C.muted).copy(letterSpacing = .6.sp), modifier = Modifier.weight(1f))
            if (url.isNotBlank()) Text("Quitar", style = ts(12, FontWeight.Bold, Color(0xFFFF9AA8)), modifier = Modifier.tap({ onChange("") }).padding(4.dp))
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier.fillMaxWidth().aspectRatio(ratio).clip(RoundedCornerShape(12.dp))
                .background(if (contain) Brush.linearGradient(listOf(Color(0xFF2B2530), Color(0xFF15111A))) else Brush.linearGradient(listOf(Color.Black.copy(alpha = .4f), Color.Black.copy(alpha = .4f))))
                .border(1.dp, Color.White.copy(alpha = .14f), RoundedCornerShape(12.dp))
                .tap({ if (!busy) launcher.launch("image/*") }, .98f),
            contentAlignment = Alignment.Center
        ) {
            if (url.isNotBlank()) Img(url, Modifier.fillMaxSize().padding(if (contain) 12.dp else 0.dp), scale = if (contain) ContentScale.Fit else ContentScale.Crop)
            else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Ic.plus, null, tint = Color.White.copy(alpha = .7f), modifier = Modifier.size(26.dp))
                Text("Elegir imagen", style = ts(12.5f, FontWeight.Bold, Color.White.copy(alpha = .7f)), modifier = Modifier.padding(top = 4.dp))
            }
            if (url.isNotBlank() && !busy) Text("Cambiar", style = ts(11.5f, FontWeight.Bold, Color.White), modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                .background(Color.Black.copy(alpha = .6f), RoundedCornerShape(99.dp)).padding(horizontal = 10.dp, vertical = 5.dp))
            if (busy) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .6f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = C.accent, strokeWidth = 2.5.dp, modifier = Modifier.size(28.dp))
                    Text("Subiendo…", style = ts(12, FontWeight.Bold, Color.White), modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
        if (hint != null) Text(hint, style = ts(11.5f, color = C.faint).copy(lineHeight = 15.sp), modifier = Modifier.padding(top = 5.dp))
    }
    val b = picked
    // el recorte se abre a pantalla completa, encima de todo
    if (b != null) androidx.compose.ui.window.Dialog(
        onDismissRequest = { picked = null },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        CropOverlay(b, ratio, label, { picked = null }) { rect ->
            picked = null; busy = true
            scope.launch {
                try { onChange(AdminData.upload(b, kind, rect)); AdminToast.show("Imagen subida") }
                catch (e: Exception) { AdminToast.show(e.message ?: "No se pudo subir", true) }
                busy = false
            }
        }
    }
}

/** Recorte a pantalla completa: se mueve y se agranda la foto con los dedos dentro del marco. */
@Composable
fun CropOverlay(bmp: Bitmap, ratio: Float, title: String, onCancel: () -> Unit, onDone: (android.graphics.Rect) -> Unit) {
    BackHandler { onCancel() }
    val img = remember(bmp) { bmp.asImageBitmap() }
    var zoom by remember { mutableFloatStateOf(1f) }
    var off by remember { mutableStateOf(Offset.Zero) }
    Box(Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) { }) {
        BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(top = 64.dp, bottom = 96.dp)) {
            val density = LocalDensity.current
            val W = with(density) { maxWidth.toPx() }; val H = with(density) { maxHeight.toPx() }
            // marco: lo más grande posible con la proporción pedida
            val fw = min(W * .92f, H * .92f * ratio); val fh = fw / ratio
            val fl = (W - fw) / 2; val ft = (H - fh) / 2
            // escala base: la foto cubre el marco
            val base = max(fw / bmp.width, fh / bmp.height)
            fun clamp(z: Float, o: Offset): Offset {
                val s = base * z; val iw = bmp.width * s; val ih = bmp.height * s
                val mx = max(0f, (iw - fw) / 2); val my = max(0f, (ih - fh) / 2)
                return Offset(o.x.coerceIn(-mx, mx), o.y.coerceIn(-my, my))
            }
            Box(Modifier.fillMaxSize().pointerInput(Unit) {
                detectTransformGestures { _, pan, z, _ -> val nz = (zoom * z).coerceIn(1f, 6f); zoom = nz; off = clamp(nz, off + pan) }
            }) {
                Canvas(Modifier.fillMaxSize()) {
                    val s = base * zoom
                    val iw = bmp.width * s; val ih = bmp.height * s
                    val cx = fl + fw / 2 + off.x; val cy = ft + fh / 2 + off.y
                    drawImage(img, dstOffset = androidx.compose.ui.unit.IntOffset((cx - iw / 2).toInt(), (cy - ih / 2).toInt()), dstSize = androidx.compose.ui.unit.IntSize(iw.toInt(), ih.toInt()))
                    val frame = Path().apply { addRect(androidx.compose.ui.geometry.Rect(fl, ft, fl + fw, ft + fh)) }
                    clipPath(frame, ClipOp.Difference) { drawRect(Color.Black.copy(alpha = .62f)) }
                    drawRect(Color.White, Offset(fl, ft), Size(fw, fh), style = Stroke(2.dp.toPx()))
                    // guías de tercios
                    for (i in 1..2) {
                        drawLine(Color.White.copy(alpha = .25f), Offset(fl + fw * i / 3, ft), Offset(fl + fw * i / 3, ft + fh))
                        drawLine(Color.White.copy(alpha = .25f), Offset(fl, ft + fh * i / 3), Offset(fl + fw, ft + fh * i / 3))
                    }
                }
            }
            val doneRect = {
                val s = base * zoom
                val iw = bmp.width * s; val ih = bmp.height * s
                val ix = fl + fw / 2 + off.x - iw / 2; val iy = ft + fh / 2 + off.y - ih / 2
                val l = ((fl - ix) / s).toInt().coerceIn(0, bmp.width - 1); val t = ((ft - iy) / s).toInt().coerceIn(0, bmp.height - 1)
                val r = ((fl + fw - ix) / s).toInt().coerceIn(l + 1, bmp.width); val b = ((ft + fh - iy) / s).toInt().coerceIn(t + 1, bmp.height)
                android.graphics.Rect(l, t, r, b)
            }
            Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 0.dp).offset96(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AButton("Cancelar", onCancel)
                AButton("Usar este recorte", { onDone(doneRect()) }, primary = true, icon = Ic.check)
            }
        }
        Column(Modifier.statusBarsPadding().padding(16.dp).align(Alignment.TopCenter), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Recortar · $title", style = ts(16, FontWeight.Bold, Color.White))
            Text("Mové y agrandá la foto con los dedos. Lo que queda dentro del marco es lo que se ve en la app.", style = ts(12, color = C.muted).copy(textAlign = TextAlign.Center), modifier = Modifier.padding(top = 3.dp))
        }
    }
}

private fun Modifier.offset96() = this.then(Modifier.graphicsLayer { translationY = 72.dp.toPx() })

/* ---------------- elegir un título ---------------- */

@Composable
fun TitlePickerSheet(onPick: (String) -> Unit, onClose: () -> Unit, exclude: Set<String> = emptySet()) {
    BackHandler { onClose() }
    var q by remember { mutableStateOf("") }
    val all = remember(AdminData.ver) { AdminData.titles }
    val norm = { s: String -> java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase() }
    val hits = all.filter { it.optString("id") !in exclude && (q.isBlank() || norm(it.optString("title")).contains(norm(q.trim()))) }.take(60)
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .7f)).tap(onClose, 1f), contentAlignment = Alignment.BottomCenter) {
        Column(Modifier.fillMaxWidth().heightIn(max = 620.dp).clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(Color(0xFF17131A)).tap({}, 1f).navigationBarsPadding().imePadding().padding(top = 10.dp)) {
            Box(Modifier.align(Alignment.CenterHorizontally).width(40.dp).height(4.dp).background(Color.White.copy(alpha = .25f), RoundedCornerShape(9.dp)))
            Text("Elegí un título", style = ts(18, FontWeight.Bold, Color.White), modifier = Modifier.padding(start = 18.dp, top = 14.dp, bottom = 8.dp))
            Box(Modifier.padding(horizontal = 16.dp)) { AField("Buscar", q, { q = it }, "Escribí el nombre…") }
            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                items(hits, key = { it.optString("id") }) { t ->
                    Row(Modifier.fillMaxWidth().tap({ onPick(t.optString("id")) }, .99f).padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(44.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(6.dp)).background(C.surface)) { if (t.optString("poster").isNotBlank()) Img(t.optString("poster"), Modifier.fillMaxSize()) }
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(t.optString("title").ifBlank { "Sin título" }, style = ts(15, FontWeight.Bold, Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(listOf(if (t.optString("type") == "serie") "Serie" else "Película", t.optString("year"), if (t.optString("status") == "borrador") "Borrador" else "").filter { it.isNotBlank() }.joinToString(" · "),
                                style = ts(12, color = C.muted))
                        }
                    }
                }
            }
        }
    }
}

/** Píldora de estado (Borrador, Destacado, Próximamente). */
@Composable
fun ABadge(text: String, color: Color) {
    Text(text, style = ts(10, FontWeight.Bold, Color.Black), modifier = Modifier.background(color, RoundedCornerShape(99.dp)).padding(horizontal = 7.dp, vertical = 2.dp))
}
