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
    val captions: Boolean, val adsPerBreak: Int, val items: List<LiveItem>
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
            c.optLong("epoch", 0L), c.opt("captions") != false, c.num("adsPerBreak", 2.0).toInt().coerceIn(0, 8), items)
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

@Composable
fun LiveGuide(nav: Nav, firstReq: FocusRequester) {
    val chans = liveChannels()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(15_000); now = System.currentTimeMillis() } }
    if (chans.isEmpty()) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF1C1218), Color.Black))), contentAlignment = Alignment.Center) {
            Text("Todavía no hay canales. Se crean desde el panel web, en la sección Canales en vivo.", style = ts(20, color = C.muted))
        }
        return
    }
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF1C1218), Color.Black))).padding(top = 104.dp)) {
        // ventana de 2 horas que arranca en la media hora actual
        val winStart = now - (now % (30 * 60_000L))
        val winEnd = winStart + 120 * 60_000L
        val leftW = 160.dp; val thumbW = 190.dp; val gap = 6.dp
        BoxWithConstraints(Modifier.fillMaxSize().padding(start = 48.dp)) {
            val lineW = maxWidth - leftW - thumbW - gap * 2
            fun x(ms: Long) = lineW * ((ms - winStart).toFloat() / (winEnd - winStart)).coerceIn(0f, 1f)
            Column {
                // horas
                Box(Modifier.fillMaxWidth().height(34.dp)) {
                    Text("HOY", style = ts(14, FontWeight.Bold, C.muted), modifier = Modifier.padding(top = 6.dp))
                    for (h in 0..3) {
                        val ms = winStart + h * 30 * 60_000L
                        Text(hhmm(ms), style = ts(14, FontWeight.Bold, C.muted),
                            modifier = Modifier.padding(top = 6.dp).offset(x = leftW + thumbW + gap * 2 + x(ms) + 10.dp))
                    }
                }
                Box(Modifier.fillMaxSize()) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 60.dp, end = 48.dp)) {
                        itemsIndexed(chans, key = { _, c -> c.id }) { i, ch ->
                            GuideRow(ch, now, winStart, winEnd, leftW, thumbW, gap, lineW, if (i == 0) firstReq else null) {
                                val target = Screen.LiveTv(ch.id)
                                if (Store.session == null) nav.go(Screen.Login(target)) else nav.go(target)
                            }
                        }
                    }
                    // línea de "ahora"
                    Box(Modifier.offset(x = leftW + thumbW + gap * 2 + x(now) - 1.5.dp).width(3.dp).fillMaxHeight().background(Color(0xFFFF3D8B)))
                }
            }
        }
    }
}

@Composable
private fun GuideRow(
    ch: LiveChannel, now: Long, winStart: Long, winEnd: Long, leftW: androidx.compose.ui.unit.Dp, thumbW: androidx.compose.ui.unit.Dp,
    gap: androidx.compose.ui.unit.Dp, lineW: androidx.compose.ui.unit.Dp, req: FocusRequester?, onOpen: () -> Unit
) {
    val sc = remember(ch, Store.titles, Store.live) { buildSched(ch, Store.live?.ads ?: emptyList()) }
    val blocks = remember(sc, winStart) { blocksBetween(sc, ch, winStart, winEnd) }
    val cur = blocks.firstOrNull { now in it.second until it.third }
    val h = 112.dp
    Row(Modifier.fillMaxWidth().height(h), horizontalArrangement = Arrangement.spacedBy(gap)) {
        Column(Modifier.width(leftW).fillMaxHeight().background(Color.White.copy(alpha = .04f), RoundedCornerShape(6.dp)),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            if (ch.logo.isNotBlank()) Img(ch.logo, Modifier.width(120.dp).height(44.dp), scale = ContentScale.Fit)
            else Text(ch.name, style = ts(17, FontWeight.Bold, Color.White), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("CANAL ${ch.number}", style = ts(12, color = C.muted), modifier = Modifier.padding(top = 4.dp))
        }
        Box(Modifier.width(thumbW).fillMaxHeight().clip(RoundedCornerShape(6.dp)).background(C.surface)) {
            val th = cur?.first?.item?.let { itemThumb(it) } ?: ""
            if (th.isNotBlank()) Img(th, Modifier.fillMaxSize())
        }
        Box(Modifier.width(lineW).fillMaxHeight()) {
            blocks.forEach { (b, s, e) ->
                val x0 = lineW * ((s - winStart).toFloat() / (winEnd - winStart)).coerceIn(0f, 1f)
                val x1 = lineW * ((e - winStart).toFloat() / (winEnd - winStart)).coerceIn(0f, 1f)
                val w = (x1 - x0 - 4.dp).coerceAtLeast(0.dp)
                if (w < 8.dp) return@forEach
                val isNow = cur != null && cur.first === b && cur.second == s
                val inner: @Composable (Boolean) -> Unit = { f ->
                    Column(Modifier.fillMaxSize().background(if (f) Color.White.copy(alpha = .16f) else Color.White.copy(alpha = if (isNow) .09f else .05f))
                        .padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        val mins = ((e - now) / 60_000L).coerceAtLeast(1)
                        Text(if (isNow) "QUEDAN $mins MIN" else "${hhmm(s)} – ${hhmm(e)}", style = ts(12, FontWeight.Bold, C.muted), maxLines = 1)
                        Text(itemTitle(b.item), style = ts(19, FontWeight.Bold, Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(itemSub(b.item), style = ts(13, color = C.muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (isNow) Focusable(onOpen, Modifier.offset(x = x0).width(w).fillMaxHeight(), shape = RoundedCornerShape(6.dp), scale = 1f, requester = req) { f -> inner(f) }
                else Box(Modifier.offset(x = x0).width(w).fillMaxHeight().clip(RoundedCornerShape(6.dp))) { inner(false) }
            }
            if (cur == null) Focusable(onOpen, Modifier.fillMaxSize(), shape = RoundedCornerShape(6.dp), scale = 1f, requester = req) { f ->
                Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = if (f) .16f else .05f)), contentAlignment = Alignment.CenterStart) {
                    Text("   Sintonizar", style = ts(18, FontWeight.Bold, Color.White))
                }
            }
        }
    }
}

/* ---------------- mirando un canal ---------------- */

/** Letra de los subtítulos: de ancho fijo y en negrita, como los closed captions analógicos (EIA-608). */
private val CcFont = FontFamily(Font(R.font.cc_mono, FontWeight.Bold))

/** Corta el texto en líneas de hasta 32 caracteres (el ancho de la grilla de los closed captions de la época). */
fun ccLines(text: String, max: Int = 32): List<String> {
    val out = ArrayList<String>()
    text.uppercase().split('\n').forEach { para ->
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
    LaunchedEffect(banner) { if (banner > 0) { showBanner = true; delay(5000); showBanner = false } }

    val seg = sc.segs.getOrNull(segIdx)
    val inAd = seg?.ad == true
    val logoA by animateFloatAsState(if (seg != null && !inAd && !failed) ch.logoOpacity else 0f, tween(600), label = "bug")
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
        // el cuadro de la imagen (4:3 con barras negras): el logo y los subtítulos van adentro, como en la señal original
        Box(Modifier.fillMaxHeight().aspectRatio(aspect.coerceIn(1f, 2.4f), matchHeightConstraintsFirst = true).align(Alignment.Center)) {
            val pad = 5
            val al = when (ch.logoPos) { "tl" -> Alignment.TopStart; "tr" -> Alignment.TopEnd; "bl" -> Alignment.BottomStart; else -> Alignment.BottomEnd }
            if (ch.logo.isNotBlank()) Img(ch.logo, Modifier.align(al).padding(horizontal = (pad * 8).dp, vertical = (pad * 6).dp)
                .height(ch.logoSize.dp).widthIn(max = (ch.logoSize * 4).dp).alpha(logoA), scale = ContentScale.Fit)
            else Text(ch.name.uppercase(), style = ts(26, FontWeight.Bold, Color.White), modifier = Modifier.align(al).padding(horizontal = 40.dp, vertical = 30.dp).alpha(logoA))
            val lines = if (!inAd && ch.captions && cue.isNotBlank()) ccLines(cue) else emptyList()
            if (lines.isNotEmpty()) BoxWithConstraints(Modifier.fillMaxSize()) {
                // 15 filas en la altura del cuadro, como la grilla de los closed captions
                val rowH = maxHeight / 15
                val fs = with(LocalDensity.current) { (rowH * .74f).toSp() }
                Column(Modifier.align(Alignment.BottomCenter).padding(bottom = rowH * (if (ch.logoPos.startsWith("b")) 2.6f else 1.4f)), horizontalAlignment = Alignment.CenterHorizontally) {
                    lines.forEach { ln ->
                        Text(ln, style = TextStyle(fontFamily = CcFont, fontWeight = FontWeight.Bold, fontSize = fs, color = Color.White, lineHeight = fs * 1.18f),
                            modifier = Modifier.background(Color.Black).padding(horizontal = rowH * .3f))
                    }
                }
            }
        }
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
