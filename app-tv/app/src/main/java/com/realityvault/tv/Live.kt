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
import androidx.compose.foundation.border
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

/* ---------------- la guía (pestaña "En vivo" del menú de arriba) ---------------- */

private val Pink = Color(0xFFFF3D8B)
private const val GUIDE_HOURS = 8
private const val VIEW_MIN = 120          // minutos visibles a la vez

@Composable
fun LiveGuide(nav: Nav, firstReq: FocusRequester) {
    val chans = liveChannels()
    var now by remember { mutableLongStateOf(Clock.now()) }
    LaunchedEffect(Unit) { while (true) { delay(15_000); now = Clock.now() } }
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
            if (blocks.isEmpty()) Focusable({ onPick(now, true) }, Modifier.fillMaxSize(), shape = RoundedCornerShape(6.dp), scale = 1f, ring = false, requester = req) { f ->
                Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = if (f) .16f else .05f)).then(if (f) Modifier.border(2.dp, Color.White, RoundedCornerShape(6.dp)) else Modifier), contentAlignment = Alignment.CenterStart) {
                    Text("Sintonizar", style = ts(18, FontWeight.Bold, Color.White), modifier = Modifier.padding(start = 20.dp))
                }
            }
            blocks.forEach { (b, s, e) ->
                val x0 = xOf(maxOf(s, t0)); val x1 = xOf(e)
                val w = (x1 - x0 - 6.dp).coerceAtLeast(0.dp)
                if (w < 10.dp) return@forEach
                val isNow = now in s until e
                Focusable({ onPick(s, isNow) }, Modifier.offset(x = x0).width(w).fillMaxHeight(), shape = RoundedCornerShape(6.dp), scale = 1f, ring = false,
                    requester = if (isNow) req else null, onFocus = { onFocusBlock(maxOf(s, t0), e) }) { f ->
                    Column(Modifier.fillMaxSize().background(
                        when { f -> Color.White.copy(alpha = .16f); isNow -> Color.White.copy(alpha = .085f); else -> Color.White.copy(alpha = .045f) })
                        .then(if (f) Modifier.border(2.dp, Color.White, RoundedCornerShape(6.dp)) else Modifier)
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
    var banner by remember { mutableIntStateOf(0) }
    var showBanner by remember { mutableStateOf(false) }
    LaunchedEffect(banner) { if (banner > 0) { showBanner = true; delay(5000); showBanner = false } }
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
        // key: al cambiar de canal se arma una señal nueva desde cero
        androidx.compose.runtime.key(ch.id) { LiveSignal(ch, sc, CcFont, CcSans, Modifier.fillMaxSize(), onTune = { banner++ }) }
        // cartel de información (al sintonizar, al cambiar de canal o con OK)
        AnimatedVisibility(showBanner, Modifier.align(Alignment.BottomCenter), enter = fadeIn(tween(200)), exit = fadeOut(tween(400))) {
            val now = Clock.now()
            val cur = remember(sc, banner) { blocksBetween(sc, ch, now - 1, now + 1).firstOrNull { now in it.second until it.third } }
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
