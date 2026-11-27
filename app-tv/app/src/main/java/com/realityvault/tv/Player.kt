package com.realityvault.tv

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import android.graphics.Color as AColor
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C as M3C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player as M3Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/* ---------------- pantalla del reproductor ---------------- */

@Composable
fun PlayerScreen(s: Screen.Player, nav: Nav) {
    val t = remember(s.titleId) { Store.byId(s.titleId) }
    val episode = remember(s, t) {
        if (t != null && s.season != null && s.episode != null)
            t.seasons.firstOrNull { it.number == s.season }?.episodes?.firstOrNull { it.number == s.episode }
        else null
    }
    val subsUrl = remember(s, t, episode) {
        when {
            episode != null -> safeSub(episode.subs)
            t != null && s.extra -> safeSub(t.seasons.flatMap { it.extras }.firstOrNull { it.url == s.url }?.subs ?: "")
            t != null && !s.trailer -> safeSub(t.subs)
            else -> ""
        }
    }
    // El subtítulo se baja antes y se pasa siempre a WebVTT (hay .srt y .txt cargados): si viene mal, el video se ve igual.
    val subsVtt by androidx.compose.runtime.produceState<String?>(if (subsUrl.isBlank()) "" else null, subsUrl) {
        if (subsUrl.isNotBlank()) value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { subsAsVtt(subsUrl) }
    }
    val media = remember(s.url, subsVtt) { mediaFor(s.url, subsVtt ?: "") }
    val next = remember(s, t) { if (t != null && !s.extra) Store.nextAfter(t, s.season, s.episode) else null }
    LaunchedEffect(s) { if (!s.trailer && !s.extra) Store.recordWatch(s.titleId, s.season, s.episode) }

    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        if (subsVtt != null) when (media) {
            is Media.Native -> NativePlayer(media, t, episode, s, next, nav)
            is Media.Embed -> EmbedPlayerScreen(media.url, t, episode, nav)
            null -> Unavailable(nav)
        }
    }
}

private fun safeSub(u: String) = if (isHttp(u)) u else ""

@Composable
fun Unavailable(nav: Nav) {
    val req = remember { FocusRequester() }
    AutoFocus(req)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Esta serie no está disponible en este momento.", style = ts(20, FontWeight.Bold, Color.White))
        Spacer(Modifier.height(18.dp))
        TextButtonTv("Volver", { nav.back() }, requester = req)
    }
}

/* ---------------- reproductor propio de Android ---------------- */

@OptIn(UnstableApi::class)
@Composable
fun NativePlayer(m: Media.Native, t: Title?, episode: Episode?, s: Screen.Player, next: Pair<Season, Episode>?, nav: Nav) {
    val ctx = LocalContext.current
    // Solo cambia cuando el usuario realmente pausa o reanuda — no mientras bufferea o adelanta
    // (eso también pone en falso el "isPlaying" real del reproductor un instante). Todo lo visual
    // que debería reaccionar a "está en pausa de verdad" (el oscurecimiento, la tarjeta de pausa,
    // a qué botón vuelve el foco) usa esto.
    var userPaused by remember { mutableStateOf(false) }
    var ended by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    // el video no se pudo abrir (enlace caído, sin internet, formato que la tele no lee…)
    var failed by remember { mutableStateOf(false) }
    var posMs by remember { mutableLongStateOf(0L) }
    var durMs by remember { mutableLongStateOf(0L) }
    var controlsVisible by remember { mutableStateOf(true) }
    // Como el reproductor de Apple TV: 0 = barra, 1 = botones de arriba (subtítulos/audio), 2 = menú de subtítulos,
    // 3 = info abierta, 4 = píldoras de abajo (Info / Siguiente episodio), 5 = menú de audio
    var panel by remember { mutableIntStateOf(0) }
    var textTracks by remember { mutableStateOf(listOf<TrackOpt>()) }
    var audioTracks by remember { mutableStateOf(listOf<TrackOpt>()) }
    var userTrack by remember { mutableStateOf(false) }
    var subsOn by remember(m.subsUrl) { mutableStateOf(m.subsUrl.isNotBlank()) }
    var lastTouch by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val player = remember(m.url, m.subsUrl) {
        ExoPlayer.Builder(ctx, com.realityvault.tv.rvRenderers(ctx)).setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(ctx, rvExtractors())).setSeekBackIncrementMs(10_000).setSeekForwardIncrementMs(10_000).build().apply {
            val subs = if (m.subsUrl.isNotBlank()) listOf(
                MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(m.subsUrl))
                    .setId("rv-es").setMimeType(MimeTypes.TEXT_VTT).setLanguage("es")
                    .setSelectionFlags(M3C.SELECTION_FLAG_DEFAULT).build()
            ) else emptyList()
            val item = MediaItem.Builder().setUri(m.url).setSubtitleConfigurations(subs)
                .apply { if (m.hls) setMimeType(MimeTypes.APPLICATION_M3U8) }.build()
            setMediaItem(item); prepare(); playWhenReady = true
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setPreferredTextLanguage("es")
                .setTrackTypeDisabled(M3C.TRACK_TYPE_TEXT, m.subsUrl.isBlank()).build()
        }
    }
    var cueText by remember { mutableStateOf("") }
    PauseInBackground(player)
    // --- seguir desde donde te quedaste (igual que la web) ---
    val track = t != null && !s.trailer && !s.extra
    fun savePos() {
        if (!track) return
        val d = player.duration; val c = player.currentPosition
        if (d <= 0 || d == androidx.media3.common.C.TIME_UNSET) return
        val id = t!!.id; val se = s.season; val ep = s.episode
        Store.bg.launch { Store.savePos(id, se, ep, c / 1000, d / 1000) }
    }
    var resumed by remember(player) { mutableStateOf(false) }
    DisposableEffect(player) {
        val l = object : M3Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { if (!isPlaying && resumed) savePos() }
            // Si el MKV trae subtítulos propios (en inglés, por ejemplo), el reproductor puede elegir
            // uno de esos. Cuando hay un .vtt cargado en el panel, ese es el que manda: lo forzamos.
            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                textTracks = tracks.groups.filter { it.type == M3C.TRACK_TYPE_TEXT && it.length > 0 }.mapIndexed { i, g ->
                    val f = g.getTrackFormat(0)
                    val lang = f.language?.takeIf { it.isNotBlank() && it != "und" }?.let { l -> java.util.Locale.forLanguageTag(l).getDisplayLanguage(java.util.Locale("es")).replaceFirstChar { c -> c.uppercase() } }
                    TrackOpt(g, f.label?.takeIf { it.isNotBlank() } ?: lang ?: "Subtítulos ${i + 1}", g.isSelected)
                }
                audioTracks = tracks.groups.filter { it.type == M3C.TRACK_TYPE_AUDIO && it.length > 0 }.mapIndexed { i, g ->
                    val f = g.getTrackFormat(0)
                    val lang = f.language?.takeIf { it.isNotBlank() && it != "und" }?.let { l -> java.util.Locale.forLanguageTag(l).getDisplayLanguage(java.util.Locale("es")).replaceFirstChar { c -> c.uppercase() } }
                    TrackOpt(g, f.label?.takeIf { it.isNotBlank() } ?: lang ?: "Audio ${i + 1}", g.isSelected)
                }
                if (m.subsUrl.isBlank() || userTrack) return
                val g = tracks.groups.firstOrNull { it.type == M3C.TRACK_TYPE_TEXT && (0 until it.length).any { i -> it.getTrackFormat(i).id?.endsWith("rv-es") == true } } ?: return
                if (g.isSelected && tracks.groups.count { it.type == M3C.TRACK_TYPE_TEXT && it.isSelected } == 1) return
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .setOverrideForType(androidx.media3.common.TrackSelectionOverride(g.mediaTrackGroup, 0)).build()
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) { failed = true; buffering = false }
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) { userPaused = !playWhenReady }
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == M3Player.STATE_BUFFERING
                if (state == M3Player.STATE_READY) {
                    failed = false
                    durMs = player.duration.coerceAtLeast(0)
                    if (!resumed) {
                        resumed = true
                        val saved = if (track && !s.restart) Store.getPos(t!!.id, s.season, s.episode) else null
                        if (posResumable(saved) && saved!!.first * 1000 < player.duration - 45_000) player.seekTo(saved.first * 1000)
                    }
                }
                ended = state == M3Player.STATE_ENDED
                if (ended) savePos()
            }
            // subtítulos propios: no usamos la vista nativa (queda pegada abajo del todo y
            // no respeta nuestra tipografía); tomamos el texto del cue y lo dibujamos nosotros.
            override fun onCues(cueGroup: androidx.media3.common.text.CueGroup) {
                cueText = cueGroup.cues.joinToString("\n") { it.text?.toString().orEmpty() }.trim()
            }
        }
        player.addListener(l)
        onDispose { if (resumed) savePos(); player.removeListener(l); player.release() }
    }
    // guarda cada 15 segundos mientras se reproduce (por si se corta la luz o se cierra la app)
    LaunchedEffect(player) { while (true) { delay(15_000); if (resumed && player.isPlaying) savePos() } }
    // reloj de progreso
    LaunchedEffect(player) {
        while (true) { posMs = player.currentPosition.coerceAtLeast(0); durMs = player.duration.coerceAtLeast(0); delay(400) }
    }
    // oculta los controles solos si está reproduciendo de verdad (no mientras bufferea o adelanta)
    LaunchedEffect(lastTouch, userPaused, controlsVisible, panel) {
        if (controlsVisible && panel == 0 && !userPaused && !failed) { delay(4000); if (System.currentTimeMillis() - lastTouch >= 3900) controlsVisible = false }
    }
    fun bump() { lastTouch = System.currentTimeMillis(); controlsVisible = true }
    // Siguiente episodio, como las plataformas de streaming: en los últimos 20 s aparece una tarjeta
    // con cuenta regresiva; al llegar a cero (o con "Reproducir ahora") pasa solo. "Atrás" la cancela.
    var nextOff by remember(player) { mutableStateOf(false) }
    val remainMs = durMs - posMs
    val showNext = next != null && t != null && !nextOff && durMs > 120_000 && (ended || (remainMs in 1..20_000))
    val nextReq = remember { FocusRequester() }
    fun playNext() { if (next != null && t != null) nav.replace(Screen.Player(t.id, next.second.url, next.first.number, next.second.number)) }
    LaunchedEffect(showNext) { if (showNext) AutoFocusOnce(nextReq) }
    LaunchedEffect(ended) {
        if (ended && next != null && t != null && !nextOff) { delay(500); if (!nextOff) playNext() }
        // sin capítulo siguiente (final de temporada, un extra, una película…): vuelve solo a la ficha
        else if (ended) { delay(1200); nav.back() }
    }

    // "Saltear intro / resumen" cargado en el panel (capítulo, extra o película).
    // Con los controles ocultos, OK del control salta directo (como Netflix); con los controles
    // en pantalla, "abajo" lleva el foco al botón.
    val skips = remember(s, t, episode) {
        when {
            s.trailer || t == null -> emptyList()
            episode != null -> episode.skips
            s.extra -> t.seasons.flatMap { it.extras }.firstOrNull { it.url == s.url }?.skips ?: emptyList()
            !t.isSerie -> t.skips
            else -> emptyList()
        }
    }
    val skip = if (showNext) null else skips.firstOrNull { posMs >= it.fromMs && posMs < it.toMs - 500 }
    val skipReq = remember { FocusRequester() }
    fun doSkip(k: Skip) {
        val target = if (durMs > 0) minOf(k.toMs, durMs - 1000) else k.toMs
        player.seekTo(target); posMs = target; if (!player.playWhenReady) player.play()
    }

    // Foco: siempre hay algo enfocado para que el control funcione (sin foco las teclas no llegan).
    // Sin paneles abiertos lo tiene un punto invisible; con un panel, su primer botón.
    val holdReq = remember { FocusRequester() }
    val ccReq = remember { FocusRequester() }
    val audReq = remember { FocusRequester() }
    val menuReq = remember { FocusRequester() }
    val pillReq = remember { FocusRequester() }
    val infoReq = remember { FocusRequester() }
    val hasCc = textTracks.isNotEmpty() || m.subsUrl.isNotBlank()
    val hasAudio = audioTracks.size > 1
    LaunchedEffect(panel, showNext, failed) {
        if (failed || showNext) return@LaunchedEffect
        when (panel) {
            0 -> AutoFocusOnce(holdReq)
            1 -> AutoFocusOnce(if (hasCc) ccReq else audReq)
            2, 5 -> AutoFocusOnce(menuReq)
            3 -> AutoFocusOnce(infoReq)
            else -> AutoFocusOnce(pillReq)
        }
    }
    fun pickSub(o: TrackOpt?) {
        if (o == null) { subsOn = false; player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(M3C.TRACK_TYPE_TEXT, true).build() }
        else { subsOn = true; userTrack = true
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(M3C.TRACK_TYPE_TEXT, false)
                .setOverrideForType(androidx.media3.common.TrackSelectionOverride(o.group.mediaTrackGroup, 0)).build() }
    }
    fun pickAudio(o: TrackOpt) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setOverrideForType(androidx.media3.common.TrackSelectionOverride(o.group.mediaTrackGroup, 0)).build()
    }

    // -- avisos de "-10 / +10" cuando se adelanta/atrasa con el control, sin tocar los botones --
    var backPulse by remember { mutableIntStateOf(0) }
    var fwdPulse by remember { mutableIntStateOf(0) }
    var showBack by remember { mutableStateOf(false) }
    var showFwd by remember { mutableStateOf(false) }
    LaunchedEffect(backPulse) { if (backPulse > 0) { showBack = true; delay(650); showBack = false } }
    LaunchedEffect(fwdPulse) { if (fwdPulse > 0) { showFwd = true; delay(650); showFwd = false } }

    fun doSeekBack() { player.seekBack(); backPulse++; bump() }
    fun doSeekForward() { player.seekForward(); fwdPulse++; bump() }
    fun doToggle() { if (player.playWhenReady) player.pause() else player.play(); bump() }

    // "Atrás" del control: si los botones están en pantalla, lo primero es ocultarlos — recién
    // si ya estaban ocultos, "atrás" sale de verdad del reproductor (vuelve al detalle).
    BackHandler(enabled = panel != 0) { panel = when (panel) { 2, 5 -> 1; 3 -> 4; else -> 0 }; lastTouch = System.currentTimeMillis() }
    BackHandler(enabled = controlsVisible && panel == 0) { controlsVisible = false }
    BackHandler(enabled = showNext) { nextOff = true; controlsVisible = false }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .onPreviewKeyEvent { e ->
                // se intercepta ANTES que cualquier botón: así el control de la tele
                // (adelantar/atrasar/pausa) funciona siempre, esté lo que esté enfocado,
                // y la tele no muestra su propia ventanita de reproducción encima.
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (failed) return@onPreviewKeyEvent false // con el aviso de error, las flechas mueven entre sus botones
                if (showNext) return@onPreviewKeyEvent when (e.key) {
                    Key.DirectionLeft, Key.MediaRewind -> { doSeekBack(); true }
                    Key.DirectionRight, Key.MediaFastForward -> { doSeekForward(); true }
                    else -> false
                }
                // con un panel abierto, las flechas mueven el foco entre sus botones
                if (panel != 0) return@onPreviewKeyEvent when (e.key) {
                    Key.MediaPlayPause -> { doToggle(); true }
                    Key.DirectionDown -> when (panel) { 1 -> { panel = 0; bump(); true }; 4 -> true; else -> false }
                    Key.DirectionUp -> when (panel) { 4 -> { panel = 0; bump(); true }; 1 -> true; 3 -> { panel = 4; true }; else -> false }
                    else -> false
                }
                when (e.key) {
                    Key.MediaPlayPause -> { doToggle(); true }
                    Key.MediaPlay -> { player.play(); bump(); true }
                    Key.MediaPause -> { player.pause(); bump(); true }
                    Key.DirectionLeft, Key.MediaRewind -> { doSeekBack(); true }
                    Key.DirectionRight, Key.MediaFastForward -> { doSeekForward(); true }
                    // OK: saltea la intro si hay una; si no, pausa / reanuda (como Apple TV)
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { if (skip != null) doSkip(skip) else doToggle(); true }
                    // arriba: botones de subtítulos y audio; abajo: Info / Siguiente episodio
                    Key.DirectionUp -> { if (controlsVisible && (hasCc || hasAudio)) panel = 1; bump(); true }
                    Key.DirectionDown -> { if (controlsVisible) panel = 4; bump(); true }
                    else -> false
                }
            }
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { c ->
                PlayerView(c).apply {
                    this.player = player
                    useController = false
                    keepScreenOn = true
                    setBackgroundColor(AColor.BLACK)
                    // ocultamos la vista nativa de subtítulos: la posición de sus cues ignora
                    // nuestro padding y queda pegada abajo del todo. Dibujamos los nuestros en Compose.
                    subtitleView?.visibility = android.view.View.GONE
                }
            }
        )
        // capa invisible: cualquier botón del control despierta la interfaz
        if (!failed) Box(Modifier.size(1.dp).focusRequester(holdReq).focusable().onKeyEvent { ev -> if (ev.type == KeyEventType.KeyDown && ev.key != Key.Back) { bump(); true } else false })

        // botón "Saltear intro / resumen"
        AnimatedVisibility(
            skip != null && panel != 3, Modifier.align(Alignment.BottomEnd).padding(end = 48.dp, bottom = if (controlsVisible || panel != 0) 200.dp else 58.dp),
            enter = fadeIn() + androidx.compose.animation.slideInHorizontally { it / 3 }, exit = fadeOut()
        ) {
            var last by remember { mutableStateOf(skip) }
            if (skip != null) last = skip
            val k = last ?: return@AnimatedVisibility
            Focusable({ doSkip(k); bump() }, Modifier.height(34.dp), shape = RoundedCornerShape(99.dp), scale = 1.06f, ring = false, requester = skipReq) { _ ->
                Box(Modifier.background(Color.White.copy(alpha = .92f), RoundedCornerShape(99.dp)).padding(horizontal = 18.dp).height(34.dp), contentAlignment = Alignment.Center) {
                    Text(k.label, style = ts(14, FontWeight.Bold, Color(0xFF111111)), maxLines = 1)
                }
            }
        }

        if (showNext && next != null && t != null) NextEpisodeCard(
            t, next, remainMs, ended, nextReq,
            Modifier.align(Alignment.BottomEnd).padding(end = 48.dp, bottom = 118.dp)
        ) { playNext() }

        // subtítulos propios: estilo elegido en el panel (MTV por defecto) o el cuadro negro de siempre
        if (subsOn && cueText.isNotBlank() && (panel == 0 || panel == 1 || panel == 4) && Store.subStyle != "classic") {
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
                VodCaptions(cueText, Store.subStyle, Store.subScaleTv, Store.subLift, if (controlsVisible || panel != 0) 195.dp.value / maxHeight.value else 0f)
            }
        } else if (subsOn && cueText.isNotBlank() && (panel == 0 || panel == 1 || panel == 4) && Store.subStyle == "classic") {
            Box(
                Modifier.align(Alignment.BottomCenter)
                    .padding(bottom = if (controlsVisible) 150.dp else 64.dp)
                    .widthIn(max = 1300.dp)
                    .padding(horizontal = 56.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    cueText, style = ts(24, FontWeight.Bold, Color.White), textAlign = TextAlign.Center,
                    modifier = Modifier.background(Color.Black.copy(alpha = .68f), RoundedCornerShape(8.dp)).padding(horizontal = 16.dp, vertical = 7.dp)
                )
            }
        }

        if (controlsVisible || panel != 0) {
            val pauseDim by animateFloatAsState(if (userPaused || panel == 3) .25f else 0f, tween(420), label = "pauseDim")
            if (pauseDim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = pauseDim)))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, .42f to Color.Transparent, .68f to Color.Black.copy(alpha = .55f), 1f to Color.Black.copy(alpha = .88f))))
            Column(Modifier.align(Alignment.BottomStart).padding(start = 48.dp, end = 48.dp, bottom = 30.dp).fillMaxWidth()) {
                if (episode != null) Text("T${s.season} · E${episode.number}" + " • " + episode.title.ifBlank { "Episodio ${episode.number}" },
                    style = ts(13, color = Color.White.copy(alpha = .85f)), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 6.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Box(Modifier.weight(1f)) { if (t != null) TitleLogo(t, 300.dp, 44.dp, 26) }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (hasCc) RoundBtn(SubsIcon, ccReq, panel == 2) { panel = 2 }
                        if (hasAudio) RoundBtn(AudioIcon, audReq, panel == 5) { panel = 5 }
                    }
                }
                Spacer(Modifier.height(14.dp))
                AppleBar(posMs, durMs, thick = userPaused)
                AppleTimes(posMs, durMs, userPaused)
                if (panel == 3) {
                    val desc = (episode?.description?.takeIf { it.isNotBlank() } ?: t?.description).orEmpty()
                    val thumb = (episode?.thumb ?: "").ifBlank { t?.backdrop.orEmpty() }
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        Box(Modifier.width(200.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = .1f))) {
                            if (thumb.isNotBlank()) Img(thumb, Modifier.fillMaxSize())
                        }
                        Column(Modifier.widthIn(max = 520.dp)) {
                            Text(episode?.title?.ifBlank { null } ?: if (episode != null) "Episodio ${episode.number}" else t?.title.orEmpty(), style = ts(18, FontWeight.Bold, Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val meta = listOfNotNull(if (episode != null) "T${s.season} · E${episode.number}" else null, if (durMs > 0) "${(durMs / 60000).coerceAtLeast(1)} min" else null).joinToString(" · ")
                            if (meta.isNotBlank()) Text(meta, style = ts(13, color = Color.White.copy(alpha = .7f)), modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
                            if (desc.isNotBlank()) Text(desc, style = ts(13, color = Color.White.copy(alpha = .9f)).copy(lineHeight = 19.sp), maxLines = 3, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(12.dp))
                            ApplePill("Empezar desde el principio", infoReq) { player.seekTo(0); if (!player.playWhenReady) player.play(); panel = 0; bump() }
                        }
                    }
                } else Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ApplePill("Info", pillReq) { panel = 3 }
                    if (next != null && t != null) ApplePill("Siguiente episodio", null) { playNext() }
                }
            }
            // menú de subtítulos / audio: sale justo arriba de los botones, como en Apple TV
            if (panel == 2 || panel == 5) {
                val shape = RoundedCornerShape(18.dp)
                Column(
                    Modifier.align(Alignment.BottomEnd).padding(end = 48.dp, bottom = 168.dp).width(250.dp)
                        .shadow(24.dp, shape).background(Color(0xDB26262A), shape).padding(8.dp)
                ) {
                    Text(if (panel == 2) "Subtítulos" else "Audio", style = ts(12, color = Color.White.copy(alpha = .55f)), modifier = Modifier.padding(start = 34.dp, top = 6.dp, bottom = 6.dp))
                    if (panel == 2) {
                        val selIdx = if (!subsOn) -1 else textTracks.indexOfFirst { it.selected }.let { if (it < 0 && textTracks.isNotEmpty()) 0 else it }
                        SubRow("Desactivados", selIdx < 0, if (selIdx < 0) menuReq else null) { pickSub(null); panel = 1 }
                        textTracks.forEachIndexed { i, o -> SubRow(o.label, i == selIdx, if (i == selIdx) menuReq else null) { pickSub(o); panel = 1 } }
                    } else {
                        val selIdx = audioTracks.indexOfFirst { it.selected }.coerceAtLeast(0)
                        audioTracks.forEachIndexed { i, o -> SubRow(o.label, i == selIdx, if (i == selIdx) menuReq else null) { pickAudio(o); panel = 1 } }
                    }
                }
            }
        }
        if (failed) {
            val retryReq = remember { FocusRequester() }
            LaunchedEffect(Unit) { AutoFocusOnce(retryReq) }
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .78f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No se pudo reproducir este video.", style = ts(22, FontWeight.Bold, Color.White))
                    Text("Revisá la conexión de la tele y probá de nuevo.", style = ts(15, color = C.fg2), modifier = Modifier.padding(top = 6.dp))
                    Spacer(Modifier.height(22.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        PillButton("Reintentar", { failed = false; buffering = true; player.prepare(); player.play() }, requester = retryReq)
                        TextButtonTv("Volver", { nav.back() })
                    }
                }
            }
        }
        if (buffering && !ended && !failed) Box(Modifier.align(Alignment.Center)) {
            androidx.compose.material3.CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp)
        }
    }
}

/** El aviso "-10" / "+10" que aparece un instante al adelantar o atrasar. */
@Composable
private fun SeekFlash(back: Boolean) {
    Box(Modifier.size(84.dp).background(Color.Black.copy(alpha = .55f), CircleShape), contentAlignment = Alignment.Center) {
        SeekIcon(back, Color.White, 44.dp)
    }
}

@Composable
private fun TopInfo(t: Title?, episode: Episode?, season: Int?) {
    // Solo el logo arriba mientras reproduce — sin la línea de temporada/episodio debajo.
    if (t != null) TitleLogo(t, 300.dp, 56.dp, 26)
}

/** Botón de subtítulos con el ícono "CC" clásico: recuadro de esquinas redondeadas con las letras
 * adentro. Activado = recuadro lleno con las letras recortadas; apagado = solo contorno. */
@Composable
private fun CcButton(on: Boolean, onClick: () -> Unit) {
    Focusable(onClick, Modifier.size(width = 50.dp, height = 38.dp), shape = RoundedCornerShape(9.dp), scale = 1.1f, ring = true, lift = true) { f ->
        Box(
            Modifier.fillMaxSize()
                .background(if (on) Color.White else Color.Black.copy(alpha = .55f), RoundedCornerShape(9.dp))
                .border(2.5.dp, Color.White, RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("CC", style = ts(16, FontWeight.Black, if (on) Color(0xFF111111) else Color.White))
        }
    }
}

@Composable
private fun SmallGlass(back: Boolean, onClick: () -> Unit) {
    Focusable(onClick, Modifier.size(52.dp), shape = CircleShape, scale = 1.12f, ring = false, lift = true) { f ->
        Box(
            Modifier.fillMaxSize().background(if (f) Color.White else Color.Black.copy(alpha = .55f), CircleShape)
                .border(1.dp, if (f) Color.White else Color.White.copy(alpha = .35f), CircleShape),
            contentAlignment = Alignment.Center
        ) { SeekIcon(back, if (f) Color.Black else Color.White, 26.dp) }
    }
}

/** Flecha circular de 10 segundos (atrasar / adelantar), dibujada a mano. */
// Antes esto se dibujaba a mano con arcos y líneas calculadas por trigonometría: en la tele
// real se veía como un garabato ilegible (parecía una letra random, no una flecha). Se
// reemplaza por el mismo ícono de "retroceder/adelantar" que usan YouTube/Material Design —
// una forma ya probada y reconocible — con el "10" aparte abajo, sin pisarse con la flecha.
private val SeekBackIcon: ImageVector = ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
    .addPath(pathData = addPathNodes("M12 5V1L7 6l5 5V7c3.31 0 6 2.69 6 6s-2.69 6-6 6-6-2.69-6-6H4c0 4.42 3.58 8 8 8s8-3.58 8-8-3.58-8-8-8z"), fill = SolidColor(Color.White))
    .build()

private val SeekFwdIcon: ImageVector = ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
    .addPath(pathData = addPathNodes("M18 13c0 3.31-2.69 6-6 6s-6-2.69-6-6 2.69-6 6-6v4l5-5-5-5v4c-4.42 0-8 3.58-8 8s3.58 8 8 8 8-3.58 8-8h-2z"), fill = SolidColor(Color.White))
    .build()

@Composable
private fun SeekIcon(back: Boolean, color: Color, size: androidx.compose.ui.unit.Dp) {
    Column(Modifier.size(size), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(if (back) SeekBackIcon else SeekFwdIcon, null, tint = color, modifier = Modifier.size(size * .58f))
        Text("10", style = ts(size.value * .3f, FontWeight.Bold, color))
    }
}

@Composable
private fun Scrub(posMs: Long, durMs: Long, thick: Boolean = false) {
    val frac = if (durMs > 0) (posMs.toFloat() / durMs).coerceIn(0f, 1f) else 0f
    val h by animateFloatAsState(if (thick) 10f else 6f, tween(250), label = "scrub-h")
    Box(Modifier.fillMaxWidth().height(h.dp).background(Color.White.copy(alpha = .3f), RoundedCornerShape(99.dp))) {
        Box(Modifier.fillMaxWidth(frac).height(h.dp).background(Color.White, RoundedCornerShape(99.dp)))
    }
}

/** Un subtítulo elegible en el menú. */
class TrackOpt(val group: androidx.media3.common.Tracks.Group, val label: String, val selected: Boolean)

@Composable
private fun SubRow(text: String, selected: Boolean, req: FocusRequester?, onClick: () -> Unit) {
    Focusable(onClick, Modifier.fillMaxWidth().height(38.dp), shape = RoundedCornerShape(10.dp), scale = 1f, ring = false, requester = req) { f ->
        Row(Modifier.fillMaxSize().background(if (f) Color.White.copy(alpha = .92f) else Color.Transparent).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(24.dp)) { if (selected) Icon(Ic.check, null, tint = if (f) Color(0xFF111111) else Color.White, modifier = Modifier.size(15.dp)) }
            Text(text, style = ts(15, color = if (f) Color(0xFF111111) else Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Botón redondo chico de vidrio (subtítulos, audio), como los de Apple TV. */
@Composable
private fun RoundBtn(icon: ImageVector, req: FocusRequester, open: Boolean, onClick: () -> Unit) {
    Focusable(onClick, Modifier.size(36.dp), shape = CircleShape, scale = 1.15f, ring = false, requester = req) { f ->
        val on = f || open
        Box(Modifier.fillMaxSize().background(if (on) Color.White else Color.White.copy(alpha = .16f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = if (on) Color(0xFF111111) else Color.White, modifier = Modifier.size(18.dp))
        }
    }
}

/** Píldora de abajo (Info, Siguiente episodio, Empezar desde el principio). */
@Composable
private fun ApplePill(text: String, req: FocusRequester?, onClick: () -> Unit) {
    Focusable(onClick, Modifier.height(28.dp), shape = RoundedCornerShape(99.dp), scale = 1.08f, ring = false, requester = req) { f ->
        Box(Modifier.background(if (f) Color.White else Color.White.copy(alpha = .14f), RoundedCornerShape(99.dp)).padding(horizontal = 14.dp).height(28.dp), contentAlignment = Alignment.Center) {
            Text(text, style = ts(13, if (f) FontWeight.Bold else FontWeight.Normal, if (f) Color(0xFF111111) else Color.White), maxLines = 1)
        }
    }
}

/** Barra de progreso de Apple TV: finita, relleno gris claro y una rayita blanca donde va el video. */
@Composable
private fun AppleBar(posMs: Long, durMs: Long, thick: Boolean) {
    val frac = if (durMs > 0) (posMs.toFloat() / durMs).coerceIn(0f, 1f) else 0f
    val h by animateFloatAsState(if (thick) 9f else 5f, tween(220), label = "bar-h")
    androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(h.dp)) {
        val r = androidx.compose.ui.geometry.CornerRadius(size.height / 2, size.height / 2)
        drawRoundRect(Color.White.copy(alpha = .22f), cornerRadius = r)
        if (frac > 0f) drawRoundRect(Color.White.copy(alpha = .62f), size = androidx.compose.ui.geometry.Size(size.width * frac, size.height), cornerRadius = r)
        val x = size.width * frac
        drawRect(Color.White, topLeft = androidx.compose.ui.geometry.Offset((x - 1.dp.toPx()).coerceIn(0f, size.width - 2.dp.toPx()), 0f), size = androidx.compose.ui.geometry.Size(2.dp.toPx(), size.height))
    }
}

/** Tiempo transcurrido debajo de la rayita (con el ícono de pausa al lado si está en pausa) y lo que falta a la derecha. */
@Composable
private fun AppleTimes(posMs: Long, durMs: Long, paused: Boolean) {
    val frac = if (durMs > 0) (posMs.toFloat() / durMs).coerceIn(0f, 1f) else 0f
    val st = ts(12, FontWeight.Bold, Color.White.copy(alpha = .92f))
    androidx.compose.ui.layout.Layout(
        content = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(fmtTime(posMs), style = st)
                if (paused) Box(Modifier.padding(start = 6.dp).size(14.dp).border(1.4.dp, Color.White, CircleShape), contentAlignment = Alignment.Center) {
                    Row(horizontalArrangement = Arrangement.spacedBy(1.6.dp)) { repeat(2) { Box(Modifier.width(1.6.dp).height(6.dp).background(Color.White)) } }
                }
            }
            Text("-" + fmtTime((durMs - posMs).coerceAtLeast(0)), style = st)
        },
        modifier = Modifier.fillMaxWidth().padding(top = 7.dp).height(22.dp)
    ) { ms, c ->
        val free = c.copy(minWidth = 0, minHeight = 0)
        val a = ms[0].measure(free); val b = ms[1].measure(free)
        layout(c.maxWidth, c.maxHeight) {
            val maxX = (c.maxWidth - b.width - a.width - 12.dp.roundToPx()).coerceAtLeast(0)
            a.place((c.maxWidth * frac - a.width / 2f).toInt().coerceIn(0, maxX), 0)
            b.place(c.maxWidth - b.width, 0)
        }
    }
}

private val SubsIcon: ImageVector = ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
    .addPath(addPathNodes("M5 4.5h14a2.5 2.5 0 0 1 2.5 2.5v8a2.5 2.5 0 0 1-2.5 2.5h-8l-4.2 3.3v-3.3H5A2.5 2.5 0 0 1 2.5 15V7A2.5 2.5 0 0 1 5 4.5z"),
        fill = null, stroke = SolidColor(Color.White), strokeLineWidth = 1.9f, strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round)
    .addPath(addPathNodes("M7 10h4M13 10h4M7 13h7M16 13h1"), fill = null, stroke = SolidColor(Color.White), strokeLineWidth = 1.9f, strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round)
    .build()

private val AudioIcon: ImageVector = ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
    .addPath(addPathNodes("M4 10v4M8 7v10M12 4v16M16 8v8M20 10.5v3"), fill = null, stroke = SolidColor(Color.White), strokeLineWidth = 2f, strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round)
    .build()

private fun fmtTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600; val m = (total % 3600) / 60; val sec = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

/** Pide foco una vez que el árbol se termine de componer (los botones pueden tardar un frame en existir). */
@Composable
private fun NextEpisodeCard(t: Title, next: Pair<Season, Episode>, remainMs: Long, ended: Boolean, req: FocusRequester, modifier: Modifier, onPlay: () -> Unit) {
    val secs = if (ended) 0 else ((remainMs + 999) / 1000).toInt()
    val ep = next.second
    val img = ep.thumb.ifBlank { t.backdrop }.ifBlank { t.poster }
    val frac = if (ended) 1f else ((20_000 - remainMs) / 20_000f).coerceIn(0f, 1f)
    val shape = RoundedCornerShape(14.dp)
    Box(modifier.width(400.dp).clip(shape).background(Color(0xEB0A0809)).border(1.dp, Color.White.copy(alpha = .16f), shape)) {
        Row(Modifier.padding(start = 12.dp, top = 12.dp, end = 14.dp, bottom = 15.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(118.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(C.surface)) {
                if (img.isNotBlank()) Img(img, Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Siguiente episodio · en $secs s", style = ts(11, color = Color.White.copy(alpha = .7f)))
                Text(
                    "T${next.first.number}:E${ep.number}" + (if (ep.title.isNotBlank()) " · ${ep.title}" else ""),
                    style = ts(14, FontWeight.Bold, Color.White), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp)
                )
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    PlayPill("Reproducir ahora", null, req) { onPlay() }
                    Text("Atrás: cancelar", style = ts(10, color = Color.White.copy(alpha = .55f)), modifier = Modifier.padding(start = 10.dp))
                }
            }
        }
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = .15f))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(frac).background(C.accent))
        }
    }
}

suspend fun AutoFocusOnce(req: FocusRequester) {
    repeat(6) { if (runCatching { req.requestFocus() }.isSuccess) return; delay(60) }
}

/** Capa transparente para cuando los controles están ocultos: cualquier tecla del control los despierta. */
@Composable
private fun WakeLayer(modifier: Modifier = Modifier, onWake: () -> Unit) {
    val req = remember { FocusRequester() }
    LaunchedEffect(Unit) { AutoFocusOnce(req) }
    Box(
        modifier
            .focusRequester(req)
            .focusable()
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                // "Atrás" NO se consume acá: tiene que seguir de largo para que pueda cerrar el
                // reproductor de verdad. Si lo tragamos como cualquier otra tecla, la primera vez
                // que aparecen los controles ocultos, "atrás" nunca llega a salir del reproductor.
                if (e.key == Key.Back) return@onKeyEvent false
                onWake(); true
            }
    )
}

/* ---------------- reproductor del servicio donde está el video (Drive, OK.ru, YouTube…) ---------------- */

@Composable
fun EmbedPlayerScreen(url: String, t: Title?, episode: Episode?, nav: Nav) {
    Box(Modifier.fillMaxSize()) {
        EmbedPlayer(url)
        Box(Modifier.align(Alignment.TopStart).padding(24.dp)) { GlassButton(Ic.back) { nav.back() } }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun EmbedPlayer(url: String) {
    val html = remember(url) {
        val safe = url.replace("\"", "%22")
        """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
        <style>html,body{margin:0;height:100%;background:#000;overflow:hidden}iframe{border:0;width:100%;height:100%}</style></head>
        <body><iframe src="$safe" allow="autoplay; fullscreen; encrypted-media; picture-in-picture" allowfullscreen referrerpolicy="strict-origin-when-cross-origin"></iframe></body></html>"""
    }
    val holder = remember { arrayOfNulls<WebView>(1) }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { c ->
            val frame = FrameLayout(c).apply { setBackgroundColor(AColor.BLACK) }
            val w = WebView(c).apply {
                setBackgroundColor(AColor.BLACK)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                webViewClient = WebViewClient()
                keepScreenOn = true
                isFocusable = true; isFocusableInTouchMode = true
            }
            w.webChromeClient = object : WebChromeClient() {
                var full: View? = null
                override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                    full = view
                    frame.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                    w.visibility = View.GONE
                    view.requestFocus()
                }
                override fun onHideCustomView() {
                    full?.let { frame.removeView(it) }; full = null
                    w.visibility = View.VISIBLE; w.requestFocus()
                }
            }
            frame.addView(w, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            w.loadDataWithBaseURL(BuildConfig.SITE_URL, html, "text/html", "utf-8", null)
            w.post { w.requestFocus() }
            holder[0] = w
            frame
        }
    )
    DisposableEffect(url) {
        onDispose { holder[0]?.apply { stopLoading(); loadUrl("about:blank"); destroy() } }
    }
}
