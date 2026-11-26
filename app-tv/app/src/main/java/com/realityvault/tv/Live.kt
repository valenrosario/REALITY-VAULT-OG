package com.realityvault.tv

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C as M3C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player as M3Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clipToBounds
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/* ======================================================================
   TV en vivo: canales armados en el panel web (site/live).
   No hay servidor emitiendo: cada canal tiene una hora de arranque (epoch) y una lista con la
   duración de cada video, así que cualquier tele calcula qué va ahora y en qué segundo — todas
   ven lo mismo al mismo tiempo. El mismo cálculo está en el panel web (liveSchedule en app.js).
   ====================================================================== */

data class LiveAd(val id: String, val name: String, val url: String, val dur: Double)
data class LiveItem(
    val id: String, val titleId: String, val season: Int, val episode: Int,
    val label: String, val url: String, val subs: String, val dur: Double, val breaks: List<Double>
)
data class LiveChannel(
    val id: String, val name: String, val number: Int, val logo: String, val logoPos: String,
    val logoOpacity: Float, val logoSize: Int, val active: Boolean, val epoch: Long,
    val captions: Boolean, val adsPerBreak: Int, val items: List<LiveItem>,
    /** Imagen del canal en la guía (si está vacía, la del programa que se está emitiendo). */
    val guideImage: String = "",
    /** Estilo de subtítulos: cc608, cc608mixed, rollup, teletext, dvd, dvdyellow. */
    val ccStyle: String = "cc608",
    /** Tamaño de los subtítulos en la tele (1 = normal). */
    val ccScale: Float = 1f
)
data class LiveData(val ads: List<LiveAd>, val channels: List<LiveChannel>)

private fun JSONObject.str(k: String) = when (val v = opt(k)) { null, JSONObject.NULL -> ""; else -> v.toString().trim() }
private fun JSONObject.num(k: String, d: Double = 0.0) = str(k).toDoubleOrNull() ?: d

fun parseLive(d: JSONObject): LiveData {
    val aa = d.optJSONArray("ads") ?: JSONArray()
    val ads = (0 until aa.length()).mapNotNull { i ->
        val a = aa.optJSONObject(i) ?: return@mapNotNull null
        LiveAd(a.str("id"), a.str("name"), a.str("url"), a.num("dur"))
    }
    val ca = d.optJSONArray("channels") ?: JSONArray()
    val chans = (0 until ca.length()).mapNotNull { i ->
        val c = ca.optJSONObject(i) ?: return@mapNotNull null
        val ia = c.optJSONArray("items") ?: JSONArray()
        val items = (0 until ia.length()).mapNotNull { k ->
            val o = ia.optJSONObject(k) ?: return@mapNotNull null
            val ba = o.optJSONArray("breaks") ?: JSONArray()
            LiveItem(o.str("id"), o.str("titleId"), o.num("season").toInt(), o.num("episode").toInt(), o.str("label"),
                o.str("url"), o.str("subs"), o.num("dur"), (0 until ba.length()).map { ba.optDouble(it, 0.0) })
        }
        LiveChannel(c.str("id"), c.str("name"), c.num("number", (i + 1).toDouble()).toInt(), c.str("logo"),
            c.str("logoPos").ifBlank { "br" }, c.num("logoOpacity", .35).toFloat().coerceIn(.05f, 1f),
            c.num("logoSize", 56.0).toInt().coerceIn(20, 160), c.opt("active") != false,
            c.optLong("epoch", 0L), c.opt("captions") != false, c.num("adsPerBreak", 2.0).toInt().coerceIn(0, 8), items,
            c.str("guideImage"), c.str("ccStyle").ifBlank { "cc608" }, c.num("ccScaleTv", 1.0).toFloat().coerceIn(.6f, 1.8f))
    }
    return LiveData(ads, chans)
}

/** Un tramo de la emisión: parte de un programa (de `from` a `to` segundos del archivo) o una publicidad. */
class Seg(val ad: Boolean, val url: String, val subs: String, val from: Double, val to: Double, val itemIdx: Int, val last: Boolean) {
    val len get() = to - from
}
/** Un programa completo en la guía (con sus tandas), en segundos desde el arranque del ciclo. */
class Block(val item: LiveItem, val start: Double, val end: Double)
class Sched(val segs: List<Seg>, val starts: DoubleArray, val cycle: Double, val blocks: List<Block>)

/** Toma el enlace y los subtítulos actuales del catálogo (si se cambió el video en el panel, el canal lo usa). */
fun resolveItem(it: LiveItem): LiveItem {
    val t = Store.byId(it.titleId) ?: return it
    return if (it.season > 0) {
        val ep = t.seasons.firstOrNull { s -> s.number == it.season }?.episodes?.firstOrNull { e -> e.number == it.episode } ?: return it
        it.copy(url = if (isFileUrl(ep.url)) ep.url.trim() else it.url, subs = ep.subs.ifBlank { it.subs })
    } else it.copy(url = if (isFileUrl(t.url)) t.url.trim() else it.url, subs = t.subs.ifBlank { it.subs })
}

private val notFile = Regex("youtube|youtu\\.be|vimeo|drive\\.google|<iframe", RegexOption.IGNORE_CASE)
fun isFileUrl(u: String) = u.trim().let { x -> (x.startsWith("http://") || x.startsWith("https://")) && !notFile.containsMatchIn(x) }

fun buildSched(ch: LiveChannel, ads: List<LiveAd>): Sched {
    val okAds = ads.filter { it.dur > 1 && it.url.startsWith("http") }
    val per = minOf(ch.adsPerBreak, okAds.size)
    val segs = ArrayList<Seg>(); val blocks = ArrayList<Block>()
    var t = 0.0; var k = 0
    fun add(s: Seg) { segs += s; t += s.len }
    ch.items.forEachIndexed { i, raw ->
        val it = resolveItem(raw)
        if (it.dur < 5 || !it.url.startsWith("http")) return@forEachIndexed
        val start = t
        fun pod() {
            if (per <= 0) return
            repeat(per) { j -> val a = okAds[(k * per + j) % okAds.size]; add(Seg(true, a.url, "", 0.0, a.dur, i, true)) }
            k++
        }
        val cuts = it.breaks.filter { b -> b > 20 && b < it.dur - 20 }.distinct().sorted()
        var prev = 0.0
        for (c in cuts) { add(Seg(false, it.url, it.subs, prev, c, i, false)); pod(); prev = c }
        add(Seg(false, it.url, it.subs, prev, it.dur, i, true)); pod()   // tanda entre programas
        blocks += Block(it, start, t)
    }
    val starts = DoubleArray(segs.size); var acc = 0.0
    segs.forEachIndexed { i, s -> starts[i] = acc; acc += s.len }
    return Sched(segs, starts, acc, blocks)
}

/** Qué va ahora: (índice del tramo, segundos ya emitidos de ese tramo, segundo del ciclo). */
fun liveAt(sc: Sched, ch: LiveChannel, nowMs: Long = System.currentTimeMillis()): Triple<Int, Double, Double> {
    val c = sc.cycle
    val raw = (nowMs - ch.epoch) / 1000.0
    val t = ((raw % c) + c) % c
    var lo = 0; var hi = sc.starts.size - 1
    while (lo < hi) { val m = (lo + hi + 1) / 2; if (sc.starts[m] <= t) lo = m else hi = m - 1 }
    return Triple(lo, t - sc.starts[lo], t)
}

fun liveChannels(): List<LiveChannel> {
    val d = Store.live ?: return emptyList()
    return d.channels.filter { it.active && it.items.isNotEmpty() }.sortedBy { it.number }
}

fun itemTitle(it: LiveItem): String = Store.byId(it.titleId)?.title ?: it.label.ifBlank { "Programa" }
fun itemSub(it: LiveItem): String {
    if (it.season <= 0) return it.label.takeIf { l -> l.isNotBlank() && l != itemTitle(it) } ?: "Película"
    val ep = Store.byId(it.titleId)?.seasons?.firstOrNull { s -> s.number == it.season }?.episodes?.firstOrNull { e -> e.number == it.episode }
    return "T${it.season} · E${it.episode}" + (ep?.title?.takeIf { x -> x.isNotBlank() }?.let { x -> " · $x" } ?: "")
}
fun itemThumb(it: LiveItem): String {
    val t = Store.byId(it.titleId) ?: return ""
    val ep = if (it.season > 0) t.seasons.firstOrNull { s -> s.number == it.season }?.episodes?.firstOrNull { e -> e.number == it.episode } else null
    return ep?.thumb?.ifBlank { null } ?: t.wide
}
private fun hhmm(ms: Long): String { val c = Calendar.getInstance().apply { timeInMillis = ms }; return "%02d:%02d".format(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE)) }

/** Programas de un canal entre dos horas (en milisegundos reales), repitiendo el ciclo. */
fun blocksBetween(sc: Sched, ch: LiveChannel, fromMs: Long, toMs: Long): List<Triple<Block, Long, Long>> {
    if (sc.cycle <= 0 || sc.blocks.isEmpty()) return emptyList()
    val now = System.currentTimeMillis()
    val (_, _, t) = liveAt(sc, ch, now)
    val cycleMs = (sc.cycle * 1000).toLong()
    var base = now - (t * 1000).toLong()          // dónde empezó el ciclo actual
    while (base > fromMs) base -= cycleMs
    val out = ArrayList<Triple<Block, Long, Long>>()
    var guard = 0
    while (base < toMs && guard++ < 400) {
        for (b in sc.blocks) {
            val s = base + (b.start * 1000).toLong(); val e = base + (b.end * 1000).toLong()
            if (e > fromMs && s < toMs) out += Triple(b, s, e)
        }
        base += cycleMs
    }
    return out
}

/* ---------------- la guía (pestaña "En vivo" del menú de arriba) ---------------- */

private val Pink = Color(0xFFFF3D8B)
private const val GUIDE_HOURS = 8
private const val VIEW_MIN = 120          // minutos visibles a la vez

@Composable
fun LiveGuide(nav: Nav, firstReq: FocusRequester) {
    val chans = liveChannels()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(15_000); now = System.currentTimeMillis() } }
    // canales nuevos o cambios de programación: se consultan al abrir la guía y cada minuto mientras está abierta
    LaunchedEffect(Unit) { while (true) { Store.refreshLive(); delay(60_000) } }
    val bg = Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF2A1430), Color(0xFF141120), Color(0xFF0B0A12)),
        start = androidx.compose.ui.geometry.Offset(0f, 0f), end = androidx.compose.ui.geometry.Offset(1400f, 1600f)))
    if (chans.isEmpty()) {
        Box(bg, contentAlignment = Alignment.Center) {
            Text("Todavía no hay canales. Se crean desde el panel web, en la sección Canales en vivo.", style = ts(20, color = C.muted))
        }
        return
    }
    // la grilla arranca en la media hora actual y llega hasta 8 horas después; se corre sola siguiendo el foco
    val t0 = now - (now % (30 * 60_000L))
    val tEnd = t0 + GUIDE_HOURS * 3600_000L
    var note by remember { mutableStateOf("") }
    LaunchedEffect(note) { if (note.isNotBlank()) { delay(3200); note = "" } }
    val scrollMs = remember { androidx.compose.animation.core.Animatable(0f) }
    val scope = rememberCoroutineScope()
    val leftW = 150.dp; val thumbW = 176.dp; val gap = 8.dp
    Box(bg) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(top = 100.dp, start = 48.dp, end = 40.dp)) {
            val lineW = maxWidth - leftW - thumbW - gap * 2
            val perMin = lineW / VIEW_MIN.toFloat()
            fun xOf(ms: Long) = perMin * ((ms - t0) / 60_000f - scrollMs.value)
            /** Lleva la grilla para que el programa enfocado quede a la vista. */
            fun reveal(s: Long, e: Long) {
                val sMin = ((s - t0) / 60_000f).coerceAtLeast(0f); val eMin = (e - t0) / 60_000f
                var target = scrollMs.value
                if (eMin > target + VIEW_MIN) target = minOf(sMin - 10f, eMin - VIEW_MIN + 4f)
                if (sMin < target) target = sMin - 10f
                target = target.coerceIn(0f, (GUIDE_HOURS * 60f - VIEW_MIN))
                if (target != scrollMs.value) scope.launch { scrollMs.animateTo(target, tween(320)) }
            }
            Column {
                // cabecera: HOY, horarios y reloj
                Box(Modifier.fillMaxWidth().height(46.dp)) {
                    Text("HOY", style = ts(13, FontWeight.Bold, C.muted).copy(letterSpacing = 1.5.sp), modifier = Modifier.align(Alignment.CenterStart))
                    Box(Modifier.padding(start = leftW + thumbW + gap * 2).width(lineW).fillMaxHeight().clipToBounds()) {
                        var m = t0
                        while (m <= tEnd) {
                            val x = xOf(m)
                            if (x > -80.dp && x < lineW) Row(Modifier.offset(x = x).align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.width(1.dp).height(14.dp).background(Color.White.copy(alpha = .25f)))
                                Text("  " + hhmm(m), style = ts(14, FontWeight.Bold, Color.White.copy(alpha = .62f)))
                            }
                            m += 30 * 60_000L
                        }
                    }
                }
                Box(Modifier.weight(1f)) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(top = 4.dp, bottom = 80.dp)) {
                        itemsIndexed(chans, key = { _, c -> c.id }) { i, ch ->
                            GuideRow(ch, now, t0, tEnd, leftW, thumbW, gap, lineW, ::xOf, if (i == 0) firstReq else null,
                                onFocusBlock = { s, e -> reveal(s, e) },
                                onPick = { s, live ->
                                    if (live) { val target = Screen.LiveTv(ch.id); if (Store.session == null) nav.go(Screen.Login(target)) else nav.go(target) }
                                    else note = "Se emite a las ${hhmm(s)} · en vivo no se puede adelantar"
                                })
                        }
                    }
                }
            }
            // línea de "ahora": rosa, con un resplandor solo hacia atrás (lo que ya pasó) y el rayito arriba
            val nx = leftW + thumbW + gap * 2 + xOf(now)
            if (xOf(now) >= 0.dp && xOf(now) <= lineW) {
                Box(Modifier.offset(x = nx - 110.dp, y = 46.dp).width(110.dp).fillMaxHeight()
                    .background(Brush.horizontalGradient(listOf(Color.Transparent, Pink.copy(alpha = .10f), Pink.copy(alpha = .26f)))))
                Box(Modifier.offset(x = nx - 1.5.dp, y = 30.dp).width(3.dp).fillMaxHeight().background(Pink))
                Box(Modifier.offset(x = nx - 15.dp, y = 8.dp).size(30.dp).background(Pink, androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
                    Text("⚡", style = ts(14, FontWeight.Bold, Color.White))
                }
            }
        }
        // reloj, como la tele
        Box(Modifier.align(Alignment.TopEnd).padding(top = 104.dp, end = 40.dp).background(Color.White.copy(alpha = .09f), RoundedCornerShape(99.dp)).padding(horizontal = 18.dp, vertical = 8.dp)) {
            Text(hhmm(now), style = ts(16, FontWeight.Bold, Color.White))
        }
        AnimatedVisibility(note.isNotBlank(), Modifier.align(Alignment.BottomCenter).padding(bottom = 36.dp), enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.background(Color(0xEE1A1520), RoundedCornerShape(99.dp)).padding(horizontal = 24.dp, vertical = 12.dp)) {
                Text(note, style = ts(16, FontWeight.Bold, Color.White))
            }
        }
    }
}

@Composable
private fun GuideRow(
    ch: LiveChannel, now: Long, t0: Long, tEnd: Long, leftW: androidx.compose.ui.unit.Dp, thumbW: androidx.compose.ui.unit.Dp,
    gap: androidx.compose.ui.unit.Dp, lineW: androidx.compose.ui.unit.Dp, xOf: (Long) -> androidx.compose.ui.unit.Dp, req: FocusRequester?,
    onFocusBlock: (Long, Long) -> Unit, onPick: (Long, Boolean) -> Unit
) {
    val sc = remember(ch, Store.titles, Store.live) { buildSched(ch, Store.live?.ads ?: emptyList()) }
    val blocks = remember(sc, t0) { blocksBetween(sc, ch, t0, tEnd) }
    val cur = blocks.firstOrNull { now in it.second until it.third }
    val h = 104.dp
    Row(Modifier.fillMaxWidth().height(h), horizontalArrangement = Arrangement.spacedBy(gap)) {
        Box(Modifier.width(leftW).fillMaxHeight().background(Color.White.copy(alpha = .035f), RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
            if (ch.logo.isNotBlank()) Img(ch.logo, Modifier.width(112.dp).height(40.dp), scale = ContentScale.Fit)
            else Text(ch.name.uppercase(), style = ts(15, FontWeight.Bold, Color.White), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 10.dp))
            Text("${ch.number}", style = ts(11, FontWeight.Bold, C.muted), modifier = Modifier.align(Alignment.TopStart).padding(8.dp))
        }
        Box(Modifier.width(thumbW).fillMaxHeight().clip(RoundedCornerShape(6.dp)).background(Color(0xFF1E1A26))) {
            val th = ch.guideImage.ifBlank { cur?.first?.item?.let { itemThumb(it) } ?: "" }
            if (th.isNotBlank()) Img(th, Modifier.fillMaxSize())
        }
        Box(Modifier.width(lineW).fillMaxHeight().clipToBounds()) {
            if (blocks.isEmpty()) Focusable({ onPick(now, true) }, Modifier.fillMaxSize(), shape = RoundedCornerShape(6.dp), scale = 1f, requester = req) { f ->
                Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = if (f) .14f else .05f)), contentAlignment = Alignment.CenterStart) {
                    Text("Sintonizar", style = ts(18, FontWeight.Bold, Color.White), modifier = Modifier.padding(start = 20.dp))
                }
            }
            blocks.forEach { (b, s, e) ->
                val x0 = xOf(maxOf(s, t0)); val x1 = xOf(e)
                val w = (x1 - x0 - 6.dp).coerceAtLeast(0.dp)
                if (w < 10.dp) return@forEach
                val isNow = now in s until e
                Focusable({ onPick(s, isNow) }, Modifier.offset(x = x0).width(w).fillMaxHeight(), shape = RoundedCornerShape(6.dp), scale = 1f,
                    requester = if (isNow) req else null, onFocus = { onFocusBlock(maxOf(s, t0), e) }) { f ->
                    Column(Modifier.fillMaxSize().background(
                        when { f -> Color.White.copy(alpha = .15f); isNow -> Color.White.copy(alpha = .085f); else -> Color.White.copy(alpha = .045f) })
                        .padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val mins = ((e - now) / 60_000L).coerceAtLeast(1)
                        Text(if (isNow) "QUEDAN $mins MIN" else "${hhmm(s)} – ${hhmm(e)}", style = ts(11.5f, FontWeight.Bold, Color.White.copy(alpha = .55f)).copy(letterSpacing = 1.sp), maxLines = 1)
                        Text(itemTitle(b.item), style = ts(18, FontWeight.Bold, Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(itemSub(b.item), style = ts(12.5f, color = Color.White.copy(alpha = .55f)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/* ---------------- mirando un canal ---------------- */

/** Letra de los subtítulos: de ancho fijo y en negrita, como los closed captions analógicos (EIA-608). */
private val CcFont = FontFamily(Font(R.font.cc_mono, FontWeight.Bold))
/** Letra de los subtítulos de DVD de la época (tipo Arial en negrita). */
private val CcSans = FontFamily(Font(R.font.cc_sans, FontWeight.Bold))

/**
 * Estilos de subtítulos de la época (los mismos que se eligen en el panel; ver CC_STYLES en app.js).
 * Medidas en proporción a la altura del cuadro de la imagen, así se ven igual en la vista previa del panel.
 */
class CcSpec(val mono: Boolean, val upper: Boolean, val box: Boolean, val max: Int, val size: Float, val bottom: Float, val bottomLogo: Float, val left: Boolean = false, val outline: Boolean = false, val color: Color = Color.White, val multi: Boolean = false)
fun ccSpec(id: String): CcSpec = when (id) {
    "cc608mixed" -> CcSpec(true, false, true, 32, .0493f, .093f, .173f)
    "rollup" -> CcSpec(true, true, true, 32, .0493f, .093f, .173f, left = true)
    "teletext" -> CcSpec(true, false, true, 36, .046f, .08f, .16f, multi = true)
    "dvd" -> CcSpec(false, false, false, 40, .056f, .06f, .15f, outline = true)
    "dvdyellow" -> CcSpec(false, false, false, 40, .056f, .06f, .15f, outline = true, color = Color(0xFFF7E43A))
    else -> CcSpec(true, true, true, 32, .0493f, .093f, .173f)
}
private val TeletextColors = listOf(Color.White, Color(0xFFFFFF00), Color(0xFF00FFFF))

/** Dibuja las líneas con el estilo elegido, dentro del cuadro de la imagen (h = alto del cuadro). */
@Composable
fun CcBlock(spec: CcSpec, lines: List<String>, w: androidx.compose.ui.unit.Dp, h: androidx.compose.ui.unit.Dp, logoBottom: Boolean, colorIdx: Int, modifier: Modifier = Modifier, scale: Float = 1f) {
    if (lines.isEmpty()) return
    val fs = with(LocalDensity.current) { (h * spec.size * scale).toSp() }
    val color = if (spec.multi) TeletextColors[colorIdx % TeletextColors.size] else spec.color
    val base = TextStyle(fontFamily = if (spec.mono) CcFont else CcSans, fontWeight = FontWeight.Bold, fontSize = fs, color = color, lineHeight = fs * 1.18f)
    Box(modifier.fillMaxSize()) {
        Column(
            Modifier.align(if (spec.left) Alignment.BottomStart else Alignment.BottomCenter)
                .padding(start = if (spec.left) w * .1f else 0.dp, bottom = h * (if (logoBottom) spec.bottomLogo else spec.bottom)),
            horizontalAlignment = if (spec.left) Alignment.Start else Alignment.CenterHorizontally
        ) {
            lines.forEach { ln ->
                if (spec.outline) Box {
                    Text(ln, style = base.copy(color = Color.Black, drawStyle = androidx.compose.ui.graphics.drawscope.Stroke(width = with(LocalDensity.current) { (h * spec.size * scale * .16f).toPx() }, join = androidx.compose.ui.graphics.StrokeJoin.Round)))
                    Text(ln, style = base)
                } else Text(ln, style = base, modifier = if (spec.box) Modifier.background(Color.Black).padding(horizontal = h * .02f) else Modifier)
            }
        }
    }
}

/** Corta el texto en líneas de hasta 32 caracteres (el ancho de la grilla de los closed captions de la época). */
fun ccLines(text: String, max: Int = 32, upper: Boolean = true): List<String> {
    val out = ArrayList<String>()
    (if (upper) text.uppercase() else text).split('\n').forEach { para ->
        var line = ""
        para.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.forEach { w ->
            if (line.isEmpty()) line = w
            else if (line.length + 1 + w.length <= max) line += " $w"
            else { out += line; line = w }
            while (line.length > max) { out += line.take(max); line = line.drop(max) }
        }
        if (line.isNotEmpty()) out += line
    }
    return out.takeLast(4)
}

private class LiveQ { var next = 0; var tag = 0L }

@OptIn(UnstableApi::class)
@Composable
fun LiveWatch(s: Screen.LiveTv, nav: Nav) {
    val chans = liveChannels()
    var chId by remember { mutableStateOf(s.channelId) }
    val ch = chans.firstOrNull { it.id == chId } ?: chans.firstOrNull()
    if (ch == null) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) { Text("SIN SEÑAL", style = TextStyle(fontFamily = CcFont, fontSize = 40.sp, color = Color.White)) }
        return
    }
    val sc = remember(ch, Store.titles, Store.live) { buildSched(ch, Store.live?.ads ?: emptyList()) }
    val ctx = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(ctx).setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(ctx, rvExtractors())).build().apply {
            playWhenReady = true
            trackSelectionParameters = trackSelectionParameters.buildUpon().setPreferredTextLanguage("es").build()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    val q = remember { LiveQ() }
    var segIdx by remember { mutableIntStateOf(-1) }
    var cue by remember { mutableStateOf("") }
    var cueN by remember { mutableIntStateOf(0) }
    var roll by remember { mutableStateOf(listOf<String>()) }
    var fade by remember { mutableFloatStateOf(0f) }
    var failed by remember { mutableStateOf(false) }
    var aspect by remember { mutableFloatStateOf(4f / 3f) }
    var banner by remember { mutableIntStateOf(0) }
    var showBanner by remember { mutableStateOf(false) }

    fun item(i: Int): MediaItem {
        val sg = sc.segs[i]
        val clip = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs((sg.from * 1000).toLong())
            .setEndPositionMs(if (sg.last) M3C.TIME_END_OF_SOURCE else (sg.to * 1000).toLong()).build()
        val b = MediaItem.Builder().setUri(sg.url).setMediaId("${q.tag++}:$i").setClippingConfiguration(clip)
        if (sg.url.substringBefore('?').lowercase().endsWith(".m3u8")) b.setMimeType(MimeTypes.APPLICATION_M3U8)
        if (!sg.ad && ch.captions && sg.subs.startsWith("http")) {
            val mime = if (sg.subs.substringBefore('?').lowercase().endsWith(".srt")) MimeTypes.APPLICATION_SUBRIP else MimeTypes.TEXT_VTT
            b.setSubtitleConfigurations(listOf(MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(sg.subs))
                .setId("rv-cc").setMimeType(mime).setLanguage("es").setSelectionFlags(M3C.SELECTION_FLAG_DEFAULT).build()))
        }
        return b.build()
    }
    fun idxOf(mi: MediaItem?): Int = mi?.mediaId?.substringAfter(':')?.toIntOrNull() ?: -1
    fun fill() {
        val n = sc.segs.size
        while (player.mediaItemCount < 4) { player.addMediaItem(item(q.next)); q.next = (q.next + 1) % n }
    }
    /** Sintoniza: arranca en el tramo y el segundo que se están emitiendo ahora mismo. */
    fun tune() {
        if (sc.segs.isEmpty()) return
        val (i, off, _) = liveAt(sc, ch)
        failed = false; cue = ""
        q.next = i
        player.clearMediaItems()
        val n = sc.segs.size
        val first = item(i); q.next = (i + 1) % n
        player.setMediaItem(first, (off * 1000).toLong())
        fill(); player.prepare(); player.play()
        segIdx = i
    }
    DisposableEffect(player, sc) {
        val l = object : M3Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                segIdx = idxOf(mediaItem); cue = ""
                val ci = player.currentMediaItemIndex
                if (ci > 0) player.removeMediaItems(0, ci)
                fill()
            }
            override fun onCues(cueGroup: androidx.media3.common.text.CueGroup) {
                cue = cueGroup.cues.joinToString("\n") { it.text?.toString().orEmpty() }.trim()
            }
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) aspect = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
            }
            override fun onPlayerError(error: PlaybackException) { failed = true }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }
    // al cambiar de canal (o si cambió la programación en el panel) se vuelve a sintonizar
    LaunchedEffect(sc) { tune(); banner++ }
    // control: si se trabó o se atrasó (internet lento), salta a lo que va en vivo, como la tele
    LaunchedEffect(sc) {
        var stuck = 0
        while (true) {
            delay(4000)
            if (failed) { delay(3000); tune(); continue }
            val st = player.playbackState
            stuck = if (st == M3Player.STATE_BUFFERING) stuck + 1 else 0
            val i = idxOf(player.currentMediaItem)
            if (i < 0 || st == M3Player.STATE_IDLE || st == M3Player.STATE_ENDED || stuck >= 3) { tune(); stuck = 0; continue }
            if (st == M3Player.STATE_READY) {
                val actual = sc.starts[i] + player.currentPosition / 1000.0
                val (_, _, exp) = liveAt(sc, ch)
                var diff = exp - actual
                if (diff > sc.cycle / 2) diff -= sc.cycle
                if (diff < -sc.cycle / 2) diff += sc.cycle
                if (kotlin.math.abs(diff) > 6) tune()
            }
        }
    }
    // la tele en vivo no se pausa: si la app se va al fondo se corta, y al volver sintoniza lo que va ahora
    val owner = LocalContext.current as? androidx.lifecycle.LifecycleOwner
    DisposableEffect(owner, player, sc) {
        val ob = androidx.lifecycle.LifecycleEventObserver { _, e ->
            when (e) {
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> player.stop()
                androidx.lifecycle.Lifecycle.Event.ON_START -> if (player.playbackState == M3Player.STATE_IDLE) tune()
                else -> {}
            }
        }
        owner?.lifecycle?.addObserver(ob)
        onDispose { owner?.lifecycle?.removeObserver(ob) }
    }
    // roll-up: cada frase nueva entra por abajo y empuja las anteriores (quedan las últimas 3 líneas)
    LaunchedEffect(cue) {
        if (cue.isNotBlank()) {
            cueN++
            if (ccSpec(ch.ccStyle).left) roll = (roll + ccLines(cue, 32, true).filter { it !in roll.takeLast(3) }).takeLast(3)
        } else { delay(6000); roll = emptyList() }
    }
    LaunchedEffect(banner) { if (banner > 0) { showBanner = true; delay(5000); showBanner = false } }

    val seg = sc.segs.getOrNull(segIdx)
    val inAd = seg?.ad == true
    // Transiciones como la tele de antes: el logo se va unos segundos antes de que termine el bloque
    // (antes de una tanda o al final del episodio), la imagen y el sonido se van a negro rápido al cortar,
    // y al arrancar el bloque siguiente vuelve la imagen y el logo aparece recién a los 2-3 segundos.
    var segPos by remember { mutableLongStateOf(0L) }
    var segLeft by remember { mutableLongStateOf(Long.MAX_VALUE) }
    LaunchedEffect(player, sc) {
        while (true) {
            val i = idxOf(player.currentMediaItem)
            val s = sc.segs.getOrNull(i)
            if (s != null && player.playbackState == M3Player.STATE_READY) {
                val pos = player.currentPosition.coerceAtLeast(0)
                val len = player.duration.takeIf { it > 0 } ?: (s.len * 1000).toLong()
                segPos = pos; segLeft = (len - pos).coerceAtLeast(0)
            }
            // fundido a negro: los últimos 450 ms de cada bloque y los primeros 350 ms del siguiente
            val black = when {
                segLeft < 450 -> 1f - segLeft / 450f
                segPos < 350 -> 1f - segPos / 350f
                else -> 0f
            }.coerceIn(0f, 1f)
            fade = black
            player.volume = 1f - black
            delay(40)
        }
    }
    val logoOn = seg != null && !inAd && !failed && segPos > 2500 && segLeft > 3000
    val logoA by animateFloatAsState(if (logoOn) ch.logoOpacity else 0f, tween(900), label = "bug")
    val req = remember { FocusRequester() }
    AutoFocus(req)
    fun zap(d: Int) {
        if (chans.size < 2) { banner++; return }
        val k = chans.indexOfFirst { it.id == ch.id }
        chId = chans[(k + d + chans.size) % chans.size].id
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black).focusRequester(req).focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionUp, Key.ChannelUp, Key.PageUp -> { zap(1); true }
                    Key.DirectionDown, Key.ChannelDown, Key.PageDown -> { zap(-1); true }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Info -> { banner++; true }
                    // en vivo no se pausa, ni se adelanta ni se atrasa
                    Key.DirectionLeft, Key.DirectionRight, Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause,
                    Key.MediaFastForward, Key.MediaRewind, Key.MediaNext, Key.MediaPrevious, Key.MediaStop -> true
                    else -> false
                }
            }
    ) {
        AndroidView(modifier = Modifier.fillMaxSize(), factory = { c ->
            PlayerView(c).apply {
                this.player = player; useController = false; keepScreenOn = true
                setBackgroundColor(android.graphics.Color.BLACK)
                subtitleView?.visibility = android.view.View.GONE
                setShutterBackgroundColor(android.graphics.Color.BLACK)
            }
        })
        // el cuadro de la imagen (4:3 con barras negras): el logo y los subtítulos van adentro, como en la señal original.
        // Todo se mide en proporción al cuadro, igual que la vista previa del panel web.
        BoxWithConstraints(Modifier.fillMaxHeight().aspectRatio(4f / 3f, matchHeightConstraintsFirst = true).align(Alignment.Center)) {
            val bw = maxWidth; val bh = maxHeight
            val al = when (ch.logoPos) { "tl" -> Alignment.TopStart; "tr" -> Alignment.TopEnd; "bl" -> Alignment.BottomStart; else -> Alignment.BottomEnd }
            val lm = Modifier.align(al).padding(horizontal = bw * .07f, vertical = bh * .07f).alpha(logoA)
            if (ch.logo.isNotBlank()) Img(ch.logo, lm.height(bh * (ch.logoSize / 1080f)).widthIn(max = bw * .4f), scale = ContentScale.Fit)
            else Text(ch.name.uppercase(), style = ts(with(LocalDensity.current) { (bh * .05f).toSp().value }, FontWeight.Bold, Color.White), modifier = lm)
            val spec = ccSpec(ch.ccStyle)
            val show = !inAd && ch.captions
            val lines = when {
                !show -> emptyList()
                spec.left -> roll
                cue.isNotBlank() -> ccLines(cue, spec.max, spec.upper)
                else -> emptyList()
            }
            CcBlock(spec, lines, bw, bh, ch.logoPos.startsWith("b"), cueN, scale = ch.ccScale)
        }
        if (fade > 0.01f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = fade)))
        if (failed || sc.segs.isEmpty()) Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text("SIN SEÑAL", style = TextStyle(fontFamily = CcFont, fontSize = 44.sp, color = Color.White))
        }
        // cartel de información (al sintonizar, al cambiar de canal o con OK)
        AnimatedVisibility(showBanner, Modifier.align(Alignment.BottomCenter), enter = fadeIn(tween(200)), exit = fadeOut(tween(400))) {
            val now = System.currentTimeMillis()
            val cur = remember(sc, segIdx, banner) { blocksBetween(sc, ch, now - 1, now + 1).firstOrNull { now in it.second until it.third } }
            Row(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .9f))))
                .padding(start = 64.dp, end = 64.dp, top = 60.dp, bottom = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.width(150.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${ch.number}", style = ts(44, FontWeight.Bold, Color.White))
                    Text(ch.name.uppercase(), style = ts(12, FontWeight.Bold, C.muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(28.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.background(Color(0xFFFF3D8B), RoundedCornerShape(99.dp)).padding(horizontal = 10.dp, vertical = 3.dp)) { Text("EN VIVO", style = ts(12, FontWeight.Bold, Color.White)) }
                        if (cur != null) Text("   ${hhmm(cur.second)} – ${hhmm(cur.third)}", style = ts(14, FontWeight.Bold, C.muted))
                    }
                    if (cur != null) {
                        Text(itemTitle(cur.first.item), style = ts(28, FontWeight.Bold, Color.White), modifier = Modifier.padding(top = 6.dp))
                        Text(itemSub(cur.first.item), style = ts(16, color = C.fg2))
                        val p = ((now - cur.second).toFloat() / (cur.third - cur.second)).coerceIn(0f, 1f)
                        Box(Modifier.padding(top = 12.dp).fillMaxWidth(.6f).height(4.dp).background(Color.White.copy(alpha = .2f), RoundedCornerShape(9.dp))) {
                            Box(Modifier.fillMaxWidth(p).fillMaxHeight().background(Color.White, RoundedCornerShape(9.dp)))
                        }
                    }
                }
            }
        }
    }
}
