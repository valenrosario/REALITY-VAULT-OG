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
import androidx.compose.ui.graphics.graphicsLayer
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
    val ccScale: Float = 1f,
    /** Tanda entre un programa y el siguiente (si no, solo en los cortes marcados). */
    val podBetween: Boolean = false,
    /** Grilla ya armada por el panel al guardar: la misma para todas las teles y celulares. */
    val plan: List<PlanSeg> = emptyList()
)
/** Un tramo de la grilla publicada (a = publicidad, i = programa de la lista, f/t = segundos del archivo, u = enlace de la publicidad). */
data class PlanSeg(val ad: Boolean, val url: String, val from: Double, val to: Double, val i: Int)
data class LiveData(val ads: List<LiveAd>, val channels: List<LiveChannel>)

private fun JSONObject.str(k: String) = when (val v = opt(k)) { null, JSONObject.NULL -> ""; else -> v.toString().trim() }
private fun JSONObject.num(k: String, d: Double = 0.0) = str(k).toDoubleOrNull() ?: d

private fun parsePlan(p: JSONObject?): List<PlanSeg> {
    val a = p?.optJSONArray("segs") ?: return emptyList()
    return (0 until a.length()).mapNotNull { x ->
        val o = a.optJSONObject(x) ?: return@mapNotNull null
        PlanSeg(o.optInt("a", 0) == 1, o.str("u"), o.optDouble("f", 0.0), o.optDouble("t", 0.0), o.optInt("i", 0))
    }
}

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
            c.str("guideImage"), c.str("ccStyle").ifBlank { "cc608" }, c.num("ccScaleTv", 1.0).toFloat().coerceIn(.6f, 1.8f), c.optBoolean("podBetween", false), parsePlan(c.optJSONObject("plan")))
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

/** Arma la emisión desde la grilla publicada por el panel (una sola programación para todos). */
private fun schedFromPlan(ch: LiveChannel): Sched {
    val segs = ArrayList<Seg>(); val blocks = ArrayList<Block>()
    var t = 0.0; var bStart = -1.0; var bItem: LiveItem? = null
    for (p in ch.plan) {
        val raw = ch.items.getOrNull(p.i) ?: continue
        if (!p.ad && p.from < 0.01) {           // empieza un programa nuevo: cierra el bloque anterior
            bItem?.let { blocks += Block(it, bStart, t) }
            bStart = t; bItem = raw
        }
        if (p.ad) segs += Seg(true, p.url, "", p.from, p.to, p.i, true)
        else {
            val it = resolveItem(raw)
            segs += Seg(false, it.url.ifBlank { raw.url }, it.subs, p.from, p.to, p.i, p.to >= it.dur - 0.5)
        }
        t += p.to - p.from
    }
    bItem?.let { blocks += Block(it, bStart, t) }
    val starts = DoubleArray(segs.size); var acc = 0.0
    segs.forEachIndexed { i, s -> starts[i] = acc; acc += s.len }
    return Sched(segs, starts, acc, blocks)
}

fun buildSched(ch: LiveChannel, ads: List<LiveAd>): Sched {
    if (ch.plan.isNotEmpty()) return schedFromPlan(ch)
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
        add(Seg(false, it.url, it.subs, prev, it.dur, i, true)); pod()   // entre un programa y el siguiente siempre va una tanda
        blocks += Block(it, start, t)
    }
    val starts = DoubleArray(segs.size); var acc = 0.0
    segs.forEachIndexed { i, s -> starts[i] = acc; acc += s.len }
    return Sched(segs, starts, acc, blocks)
}

/** Qué va ahora: (índice del tramo, segundos ya emitidos de ese tramo, segundo del ciclo). */
fun liveAt(sc: Sched, ch: LiveChannel, nowMs: Long = Clock.now()): Triple<Int, Double, Double> {
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
fun hhmm(ms: Long): String { val c = Calendar.getInstance().apply { timeInMillis = ms }; return "%02d:%02d".format(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE)) }

/** Programas de un canal entre dos horas (en milisegundos reales), repitiendo el ciclo. */
fun blocksBetween(sc: Sched, ch: LiveChannel, fromMs: Long, toMs: Long): List<Triple<Block, Long, Long>> {
    if (sc.cycle <= 0 || sc.blocks.isEmpty()) return emptyList()
    val now = Clock.now()
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


/* ---------------- subtítulos y señal en vivo (compartido con la app del celular) ---------------- */

/** Letra de los subtítulos: de ancho fijo y en negrita, como los closed captions analógicos (EIA-608). */

/**
 * Estilos de subtítulos de la época (los mismos que se eligen en el panel; ver CC_STYLES en app.js).
 * Medidas en proporción a la altura del cuadro de la imagen, así se ven igual en la vista previa del panel.
 */
class CcSpec(val mono: Boolean, val upper: Boolean, val box: Boolean, val max: Int, val size: Float, val bottom: Float, val bottomLogo: Float, val left: Boolean = false, val outline: Boolean = false, val color: Color = Color.White, val multi: Boolean = false,
    /** Kabel (estilo MTV), alto de línea, estiramiento vertical y grosor del borde (proporción de la letra). */
    val kabel: Boolean = false, val lh: Float = 1.18f, val sy: Float = 1f, val stroke: Float = .16f)
fun ccSpec(id: String): CcSpec = when (id) {
    "cc608mixed" -> CcSpec(true, false, true, 32, .0493f, .093f, .173f)
    "rollup" -> CcSpec(true, true, true, 32, .0493f, .093f, .173f, left = true)
    "teletext" -> CcSpec(true, false, true, 36, .046f, .08f, .16f, multi = true)
    "dvd" -> CcSpec(false, false, false, 40, .056f, .06f, .15f, outline = true)
    "dvdyellow" -> CcSpec(false, false, false, 40, .056f, .06f, .15f, outline = true, color = Color(0xFFF7E43A))
    // MTV (especiales y documentales): Kabel Medium blanca con borde negro fino, medida sobre capturas en 1080p:
    // letra de ~61 px, renglones cada 104 px, la última línea apoyada a 118 px del borde de abajo y corrida
    // un poco a la derecha del centro (igual que en las capturas)
    "mtv" -> CcSpec(false, false, false, 42, .0569f, .0892f, .0892f, outline = true, kabel = true, lh = 1.72f, sy = 1.07f, stroke = .116f)
    else -> CcSpec(true, true, true, 32, .0493f, .093f, .173f)
}
private val CcKabel = FontFamily(Font(R.font.cc_kabel, FontWeight.Normal))
private val TeletextColors = listOf(Color.White, Color(0xFFFFFF00), Color(0xFF00FFFF))

/** Dibuja las líneas con el estilo elegido, dentro del cuadro de la imagen (h = alto del cuadro). */
@Composable
fun CcBlock(spec: CcSpec, lines: List<String>, w: androidx.compose.ui.unit.Dp, h: androidx.compose.ui.unit.Dp, logoBottom: Boolean, colorIdx: Int, mono: FontFamily, sans: FontFamily, modifier: Modifier = Modifier, scale: Float = 1f) {
    if (lines.isEmpty()) return
    val fs = with(LocalDensity.current) { (h * spec.size * scale).toSp() }
    val color = if (spec.multi) TeletextColors[colorIdx % TeletextColors.size] else spec.color
    val base = TextStyle(
        fontFamily = when { spec.kabel -> CcKabel; spec.mono -> mono; else -> sans },
        fontWeight = if (spec.kabel) FontWeight.Normal else FontWeight.Bold, fontSize = fs, color = color, lineHeight = fs * spec.lh,
        textAlign = if (spec.left) androidx.compose.ui.text.style.TextAlign.Start else androidx.compose.ui.text.style.TextAlign.Center,
        lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
            androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center, androidx.compose.ui.text.style.LineHeightStyle.Trim.Both)
    )
    val bottomPad = h * (if (logoBottom) spec.bottomLogo else spec.bottom)
    Box(modifier.fillMaxSize()) {
        if (spec.outline) {
            // texto con borde: un solo bloque centrado (el borde se dibuja debajo del relleno)
            val txt = lines.joinToString("\n")
            val sw = with(LocalDensity.current) { (h * spec.size * scale * spec.stroke).toPx() }
            Box(Modifier.align(Alignment.BottomCenter).padding(start = if (spec.kabel) w * .032f else 0.dp, bottom = bottomPad)
                .graphicsLayer { scaleY = spec.sy; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(.5f, 1f) }) {
                Text(txt, style = base.copy(color = Color.Black, drawStyle = androidx.compose.ui.graphics.drawscope.Stroke(width = sw, join = androidx.compose.ui.graphics.StrokeJoin.Round)))
                Text(txt, style = base)
            }
        } else Column(
            Modifier.align(if (spec.left) Alignment.BottomStart else Alignment.BottomCenter)
                .padding(start = if (spec.left) w * .1f else 0.dp, bottom = bottomPad),
            horizontalAlignment = if (spec.left) Alignment.Start else Alignment.CenterHorizontally
        ) {
            lines.forEach { ln -> Text(ln, style = base, modifier = if (spec.box) Modifier.background(Color.Black).padding(horizontal = h * .02f) else Modifier) }
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

/**
 * La señal en vivo de un canal: reproductor, sintonía con el horario, tandas, fundidos, logo y subtítulos.
 * La usan la app de la tele y la del celular (cada una pone sus controles y su cartel de información).
 * mono / sans: letras de los subtítulos (cada app tiene sus propias fuentes).
 */
@OptIn(UnstableApi::class)
@Composable
fun LiveSignal(ch: LiveChannel, sc: Sched, mono: FontFamily, sans: FontFamily, modifier: Modifier = Modifier, onTune: () -> Unit = {}) {
    // huella de la grilla: si el catálogo se recarga y la programación es la misma, no se toca la emisión
    val schedKey = remember(sc, ch) { ch.id + ":" + ch.epoch + ":" + sc.segs.joinToString("|") { "${it.url}@${it.from}-${it.to}" }.hashCode() }
    val ctx = LocalContext.current
    val player = remember {
        // buffer medido en tiempo y no en bytes: con video 1080p el tope por bytes cortaba la precarga a ~20 s
        // y el bloque siguiente (la vuelta de la tanda) arrancaba sin cargar. Así lo que sigue ya está listo.
        val load = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(40_000, 120_000, 1_500, 3_000)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        ExoPlayer.Builder(ctx).setLoadControl(load).setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(ctx, rvExtractors())).build().apply {
            playWhenReady = true
            trackSelectionParameters = trackSelectionParameters.buildUpon().setPreferredTextLanguage("es").build()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    // trae la grilla y la hora del servidor al sintonizar y cada 2 minutos (si cambió, se resintoniza sola)
    LaunchedEffect(Unit) { while (true) { Store.refreshLive(); delay(120_000) } }
    val q = remember { LiveQ() }
    var segIdx by remember { mutableIntStateOf(-1) }
    var cue by remember { mutableStateOf("") }
    var cueN by remember { mutableIntStateOf(0) }
    var roll by remember { mutableStateOf(listOf<String>()) }
    var fade by remember { mutableFloatStateOf(0f) }
    var failed by remember { mutableStateOf(false) }

    fun item(i: Int): MediaItem {
        val sg = sc.segs[i]
        val clip = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs((sg.from * 1000).toLong())
            .setEndPositionMs(if (sg.to > sg.from) (sg.to * 1000).toLong() else M3C.TIME_END_OF_SOURCE).build()
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
    DisposableEffect(player, schedKey) {
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
            override fun onPlayerError(error: PlaybackException) { failed = true }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }
    // al cambiar de canal (o si cambió la programación en el panel) se vuelve a sintonizar
    LaunchedEffect(schedKey) { tune(); onTune() }
    // Control de sincronía con el horario. Nunca vuelve para atrás en medio de un programa (eso repetía escenas):
    //  - en una tanda o en los primeros segundos de un bloque corrige enseguida (no se nota);
    //  - en medio de un programa solo salta si quedó muy atrasado (internet lento) o muy desfasado;
    //  - si se queda cargando más de 16 s, salta a lo que va en vivo, como la tele.
    LaunchedEffect(schedKey) {
        var stuck = 0
        while (true) {
            delay(4000)
            if (failed) { delay(3000); tune(); continue }
            val st = player.playbackState
            stuck = if (st == M3Player.STATE_BUFFERING) stuck + 1 else 0
            val i = idxOf(player.currentMediaItem)
            if (i < 0 || st == M3Player.STATE_IDLE || st == M3Player.STATE_ENDED || stuck >= 4) { tune(); stuck = 0; continue }
            if (st == M3Player.STATE_READY) {
                val pos = player.currentPosition / 1000.0
                val actual = sc.starts[i] + pos
                val (_, _, exp) = liveAt(sc, ch)
                var diff = exp - actual
                if (diff > sc.cycle / 2) diff -= sc.cycle
                if (diff < -sc.cycle / 2) diff += sc.cycle
                val s = sc.segs[i]
                val fix = when {
                    s.ad -> kotlin.math.abs(diff) > 3
                    pos < 5 -> diff > 3
                    else -> diff > 30 || diff < -150
                }
                if (fix) tune()
            }
        }
    }
    // la tele en vivo no se pausa: si la app se va al fondo se corta, y al volver sintoniza lo que va ahora
    val owner = LocalContext.current as? androidx.lifecycle.LifecycleOwner
    DisposableEffect(owner, player, schedKey) {
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

    val seg = sc.segs.getOrNull(segIdx)
    val inAd = seg?.ad == true
    // Transiciones como la tele de antes: el logo se va unos segundos antes de que termine el bloque
    // (antes de una tanda o al final del episodio), la imagen y el sonido se van a negro rápido al cortar,
    // y al arrancar el bloque siguiente vuelve la imagen y el logo aparece recién a los 2-3 segundos.
    var segPos by remember { mutableLongStateOf(0L) }
    var segLeft by remember { mutableLongStateOf(Long.MAX_VALUE) }
    LaunchedEffect(player, schedKey) {
        while (true) {
            val i = idxOf(player.currentMediaItem)
            val s = sc.segs.getOrNull(i)
            if (s != null && player.playbackState == M3Player.STATE_READY) {
                val pos = player.currentPosition.coerceAtLeast(0)
                val len = player.duration.takeIf { it > 0 } ?: (s.len * 1000).toLong()
                segPos = pos; segLeft = (len - pos).coerceAtLeast(0)
            }
            // fundido a negro rápido solo al pasar de programa a tanda o de tanda a programa;
            // entre una publicidad y la siguiente es corte directo, como en la tele
            val n = sc.segs.size
            val cur = sc.segs.getOrNull(i)
            val fadeOut = cur != null && (!cur.ad || !sc.segs[(i + 1) % n].ad)
            val fadeIn = cur != null && (!cur.ad || !sc.segs[(i - 1 + n) % n].ad)
            val black = when {
                fadeOut && segLeft < 450 -> 1f - segLeft / 450f
                fadeIn && segPos < 350 -> 1f - segPos / 350f
                else -> 0f
            }.coerceIn(0f, 1f)
            fade = black
            player.volume = 1f - black
            delay(40)
        }
    }
    val logoOn = seg != null && !inAd && !failed && segPos > 2500 && segLeft > 3000
    val logoA by animateFloatAsState(if (logoOn) ch.logoOpacity else 0f, tween(900), label = "bug")

    Box(modifier.background(Color.Black)) {
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
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val fitW = maxHeight * (4f / 3f) <= maxWidth
            val bw = if (fitW) maxHeight * (4f / 3f) else maxWidth
            val bh = if (fitW) maxHeight else maxWidth * (3f / 4f)
            Box(Modifier.size(bw, bh)) {
                val al = when (ch.logoPos) { "tl" -> Alignment.TopStart; "tr" -> Alignment.TopEnd; "bl" -> Alignment.BottomStart; else -> Alignment.BottomEnd }
                val lm = Modifier.align(al).padding(horizontal = bw * .07f, vertical = bh * .07f).alpha(logoA)
                if (ch.logo.isNotBlank()) coil.compose.AsyncImage(model = ch.logo, contentDescription = null, contentScale = ContentScale.Fit,
                    modifier = lm.height(bh * (ch.logoSize / 1080f)).widthIn(max = bw * .4f))
                else Text(ch.name.uppercase(), style = TextStyle(fontFamily = sans, fontWeight = FontWeight.Bold, color = Color.White,
                    fontSize = with(LocalDensity.current) { (bh * .05f).toSp() }), modifier = lm)
                val spec = ccSpec(ch.ccStyle)
                val lines = when {
                    inAd || !ch.captions -> emptyList()
                    spec.left -> roll
                    cue.isNotBlank() -> ccLines(cue, spec.max, spec.upper)
                    else -> emptyList()
                }
                CcBlock(spec, lines, bw, bh, ch.logoPos.startsWith("b"), cueN, mono, sans, scale = ch.ccScale)
            }
        }
        if (fade > 0.01f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = fade)))
        if (failed || sc.segs.isEmpty()) Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text("SIN SEÑAL", style = TextStyle(fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 40.sp, color = Color.White))
        }
    }
}
