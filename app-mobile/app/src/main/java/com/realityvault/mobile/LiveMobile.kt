package com.realityvault.mobile

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.realityvault.tv.LiveChannel
import com.realityvault.tv.R
import com.realityvault.tv.LiveSignal
import com.realityvault.tv.Store
import com.realityvault.tv.blocksBetween
import com.realityvault.tv.buildSched
import com.realityvault.tv.hhmm
import com.realityvault.tv.itemSub
import com.realityvault.tv.itemThumb
import com.realityvault.tv.itemTitle
import com.realityvault.tv.liveChannels
import kotlinx.coroutines.delay

/* ======================================================================
   TV en vivo en el celular: la misma emisión que la tele (LiveSignal, compartido).
   ====================================================================== */

private val LivePink = Color(0xFFFF3D8B)
private val CcMono = FontFamily(Font(R.font.cc_mono, FontWeight.Bold))
private val CcSans = FontFamily(Font(R.font.cc_sans, FontWeight.Bold))

/** Pestaña "En vivo": los canales con lo que se está emitiendo y lo que sigue. */
@Composable
fun LiveGuideScreen(nav: Nav) {
    val chans = liveChannels()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(15_000); now = System.currentTimeMillis() } }
    // canales nuevos o cambios de programación: al abrir y cada minuto
    LaunchedEffect(Unit) { while (true) { Store.refreshLive(); delay(60_000) } }
    var note by remember { mutableStateOf("") }
    LaunchedEffect(note) { if (note.isNotBlank()) { delay(3000); note = "" } }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF2A1430), Color(0xFF141120), Color.Black)))) {
        LazyColumn(
            Modifier.fillMaxSize(), state = nav.memory.list("live"),
            contentPadding = PaddingValues(top = 0.dp, bottom = 140.dp), verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            item(key = "head") {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("En vivo", style = ts(30, FontWeight.Bold, Color.White))
                        Text("Como la tele: no se pausa ni se adelanta", style = ts(13, color = Color.White.copy(alpha = .6f)))
                    }
                    Box(Modifier.background(Color.White.copy(alpha = .1f), RoundedCornerShape(99.dp)).padding(horizontal = 14.dp, vertical = 7.dp)) {
                        Text(hhmm(now), style = ts(14, FontWeight.Bold, Color.White))
                    }
                }
            }
            if (chans.isEmpty()) item(key = "empty") {
                Box(Modifier.fillMaxWidth().padding(top = 120.dp, start = 30.dp, end = 30.dp), contentAlignment = Alignment.Center) {
                    Text("Todavía no hay canales al aire.", style = ts(16, color = Color.White.copy(alpha = .7f)))
                }
            }
            items(chans, key = { it.id }) { ch ->
                ChannelCard(ch, now, onWatch = {
                    val target = Screen.LiveTv(ch.id)
                    if (Store.session == null) nav.go(Screen.Login(target)) else nav.go(target)
                }, onFuture = { s -> note = "Se emite a las ${hhmm(s)} · en vivo no se puede adelantar" })
            }
        }
        AnimatedVisibility(note.isNotBlank(), Modifier.align(Alignment.BottomCenter).padding(bottom = 110.dp), enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.padding(horizontal = 20.dp).background(Color(0xEE1A1520), RoundedCornerShape(99.dp)).padding(horizontal = 18.dp, vertical = 11.dp)) {
                Text(note, style = ts(13.5f, FontWeight.Bold, Color.White))
            }
        }
    }
}

@Composable
private fun ChannelCard(ch: LiveChannel, now: Long, onWatch: () -> Unit, onFuture: (Long) -> Unit) {
    val sc = remember(ch, Store.titles, Store.live) { buildSched(ch, Store.live?.ads ?: emptyList()) }
    val blocks = remember(sc, now / 60_000) { blocksBetween(sc, ch, now, now + 6 * 3600_000L) }
    val cur = blocks.firstOrNull { now in it.second until it.third }
    Column(Modifier.fillMaxWidth()) {
        // canal: logo / nombre y número
        Row(Modifier.padding(horizontal = 20.dp).padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (ch.logo.isNotBlank()) Img(ch.logo, Modifier.height(26.dp).widthIn(max = 120.dp), scale = ContentScale.Fit, alignment = Alignment.CenterStart)
            else Text(ch.name, style = ts(17, FontWeight.Bold, Color.White))
            Text("  ·  Canal ${ch.number}", style = ts(13, color = Color.White.copy(alpha = .55f)))
        }
        // lo que va ahora: imagen grande con el progreso
        Box(Modifier.padding(horizontal = 20.dp).fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp)).background(Color(0xFF1E1A26)).tap(onWatch, .97f)) {
            val th = ch.guideImage.ifBlank { cur?.first?.item?.let { itemThumb(it) } ?: "" }
            if (th.isNotBlank()) Img(th, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, .45f to Color.Transparent, 1f to Color.Black.copy(alpha = .88f))))
            Box(Modifier.align(Alignment.TopStart).padding(12.dp).background(LivePink, RoundedCornerShape(99.dp)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                Text("● EN VIVO", style = ts(11, FontWeight.Bold, Color.White))
            }
            Box(Modifier.align(Alignment.Center).size(58.dp).background(Color.Black.copy(alpha = .35f), CircleShape).border(1.5.dp, Color.White.copy(alpha = .7f), CircleShape), contentAlignment = Alignment.Center) {
                Text("▶", style = ts(22, color = Color.White))
            }
            if (cur != null) Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
                val mins = ((cur.third - now) / 60_000L).coerceAtLeast(1)
                Text(itemTitle(cur.first.item), style = ts(19, FontWeight.Bold, Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${itemSub(cur.first.item)} · quedan $mins min", style = ts(12.5f, color = Color.White.copy(alpha = .75f)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                val p = ((now - cur.second).toFloat() / (cur.third - cur.second)).coerceIn(0f, 1f)
                Box(Modifier.padding(top = 9.dp).fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = .22f), RoundedCornerShape(9.dp))) {
                    Box(Modifier.fillMaxWidth(p).fillMaxHeight().background(LivePink, RoundedCornerShape(9.dp)))
                }
            }
        }
        // lo que sigue (se puede ver la programación, pero no adelantar)
        val next = blocks.filter { it.second >= (cur?.third ?: now) - 1000 }.take(10)
        if (next.isNotEmpty()) LazyRow(
            Modifier.padding(top = 10.dp), contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(next, key = { "${it.second}" }) { (b, s, e) ->
                Column(Modifier.width(170.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = .06f)).tap({ onFuture(s) }, .97f).padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Text("${hhmm(s)} – ${hhmm(e)}", style = ts(11, FontWeight.Bold, Color.White.copy(alpha = .55f)))
                    Text(itemTitle(b.item), style = ts(14, FontWeight.Bold, Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
                    Text(itemSub(b.item), style = ts(11.5f, color = Color.White.copy(alpha = .55f)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Mirando un canal: pantalla completa y horizontal. Tocar muestra la información; deslizar arriba/abajo cambia de canal. */
@Composable
fun LiveWatchScreen(s: Screen.LiveTv, nav: Nav) {
    val chans = liveChannels()
    var chId by remember { mutableStateOf(s.channelId) }
    val ch = chans.firstOrNull { it.id == chId } ?: chans.firstOrNull()
    val activity = LocalContext.current as? Activity
    DisposableEffect(activity) {
        val w = activity?.window
        if (w != null) {
            val ctl = WindowCompat.getInsetsController(w, w.decorView)
            ctl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            ctl.hide(WindowInsetsCompat.Type.systemBars())
            w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        onDispose {
            if (w != null) {
                WindowCompat.getInsetsController(w, w.decorView).show(WindowInsetsCompat.Type.systemBars())
                w.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }
    BackHandler { nav.back() }
    if (ch == null) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) { Text("SIN SEÑAL", style = ts(28, FontWeight.Bold, Color.White)) }
        return
    }
    val sc = remember(ch, Store.titles, Store.live) { buildSched(ch, Store.live?.ads ?: emptyList()) }
    var info by remember { mutableIntStateOf(0) }
    var showInfo by remember { mutableStateOf(false) }
    LaunchedEffect(info) { if (info > 0) { showInfo = true; delay(5000); showInfo = false } }
    fun zap(d: Int) {
        if (chans.size < 2) { info++; return }
        val k = chans.indexOfFirst { it.id == ch.id }
        chId = chans[(k + d + chans.size) % chans.size].id
    }
    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .pointerInput(Unit) { detectTapGestures { if (showInfo) showInfo = false else info++ } }
            .pointerInput(chans.size, ch.id) {
                var acc = 0f
                detectVerticalDragGestures(onDragStart = { acc = 0f }, onDragEnd = { if (acc < -120) zap(1) else if (acc > 120) zap(-1) }) { _, dy -> acc += dy }
            }
    ) {
        key(ch.id) { LiveSignal(ch, sc, CcMono, CcSans, Modifier.fillMaxSize(), onTune = { info++ }) }
        // información: canal, programa y progreso (sin pausa ni barra para adelantar: es en vivo)
        AnimatedVisibility(showInfo, Modifier.fillMaxSize(), enter = fadeIn(tween(180)), exit = fadeOut(tween(350))) {
            val now = System.currentTimeMillis()
            val cur = remember(sc, info) { blocksBetween(sc, ch, now - 1, now + 1).firstOrNull { now in it.second until it.third } }
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().height(110.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .7f), Color.Transparent))))
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = .4f)).tap({ nav.back() }, .9f), contentAlignment = Alignment.Center) {
                        Text("✕", style = ts(18, FontWeight.Bold, Color.White))
                    }
                    Spacer(Modifier.weight(1f))
                    if (chans.size > 1) Text("Deslizá arriba o abajo para cambiar de canal", style = ts(12, color = Color.White.copy(alpha = .7f)))
                }
                Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .88f))))
                    .padding(start = 28.dp, end = 28.dp, top = 40.dp, bottom = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.width(96.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${ch.number}", style = ts(34, FontWeight.Bold, Color.White))
                        Text(ch.name.uppercase(), style = ts(10, FontWeight.Bold, Color.White.copy(alpha = .6f)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.width(18.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.background(LivePink, RoundedCornerShape(99.dp)).padding(horizontal = 9.dp, vertical = 3.dp)) { Text("EN VIVO", style = ts(10.5f, FontWeight.Bold, Color.White)) }
                            if (cur != null) Text("   ${hhmm(cur.second)} – ${hhmm(cur.third)}", style = ts(12.5f, FontWeight.Bold, Color.White.copy(alpha = .65f)))
                        }
                        if (cur != null) {
                            Text(itemTitle(cur.first.item), style = ts(22, FontWeight.Bold, Color.White), modifier = Modifier.padding(top = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(itemSub(cur.first.item), style = ts(13.5f, color = Color.White.copy(alpha = .75f)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val p = ((now - cur.second).toFloat() / (cur.third - cur.second)).coerceIn(0f, 1f)
                            Box(Modifier.padding(top = 10.dp).fillMaxWidth(.7f).height(3.dp).background(Color.White.copy(alpha = .2f), RoundedCornerShape(9.dp))) {
                                Box(Modifier.fillMaxWidth(p).fillMaxHeight().background(Color.White, RoundedCornerShape(9.dp)))
                            }
                        }
                    }
                }
            }
        }
    }
}
