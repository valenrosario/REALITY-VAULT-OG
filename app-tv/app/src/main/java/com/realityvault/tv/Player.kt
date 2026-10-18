package com.realityvault.tv

import android.annotation.SuppressLint
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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C as M3C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player as M3Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.core.content.res.ResourcesCompat
import kotlinx.coroutines.delay

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
            t != null && !s.trailer -> safeSub(t.subs)
            else -> ""
        }
    }
    val media = remember(s.url, subsUrl) { mediaFor(s.url, subsUrl) }
    val next = remember(s, t) { if (t != null) Store.nextAfter(t, s.season, s.episode) else null }
    LaunchedEffect(s) { if (!s.trailer) Store.recordWatch(s.titleId, s.season, s.episode) }

    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        when (media) {
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
    var playing by remember { mutableStateOf(true) }
    var ended by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var posMs by remember { mutableLongStateOf(0L) }
    var durMs by remember { mutableLongStateOf(0L) }
    var controlsVisible by remember { mutableStateOf(true) }
    var subsOn by remember(m.subsUrl) { mutableStateOf(m.subsUrl.isNotBlank()) }
    var lastTouch by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val player = remember(m.url, m.subsUrl) {
        ExoPlayer.Builder(ctx).setSeekBackIncrementMs(10_000).setSeekForwardIncrementMs(10_000).build().apply {
            val subs = if (m.subsUrl.isNotBlank()) listOf(
                MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(m.subsUrl))
                    .setMimeType(MimeTypes.TEXT_VTT).setLanguage("es")
                    .setSelectionFlags(M3C.SELECTION_FLAG_DEFAULT).build()
            ) else emptyList()
            val item = MediaItem.Builder().setUri(m.url).setSubtitleConfigurations(subs)
                .apply { if (m.hls) setMimeType(MimeTypes.APPLICATION_M3U8) }.build()
            setMediaItem(item); prepare(); playWhenReady = true
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(M3C.TRACK_TYPE_TEXT, m.subsUrl.isBlank()).build()
        }
    }
    DisposableEffect(player) {
        val l = object : M3Player.Listener {
            override fun onIsPlayingChanged(p: Boolean) { playing = p }
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == M3Player.STATE_BUFFERING
                if (state == M3Player.STATE_READY) durMs = player.duration.coerceAtLeast(0)
                ended = state == M3Player.STATE_ENDED
            }
        }
        player.addListener(l)
        onDispose { player.removeListener(l); player.release() }
    }
    // reloj de progreso
    LaunchedEffect(player) {
        while (true) { posMs = player.currentPosition.coerceAtLeast(0); durMs = player.duration.coerceAtLeast(0); delay(400) }
    }
    // oculta los controles solos si está reproduciendo
    LaunchedEffect(lastTouch, playing, controlsVisible) {
        if (controlsVisible && playing) { delay(4000); if (System.currentTimeMillis() - lastTouch >= 3900) controlsVisible = false }
    }
    fun bump() { lastTouch = System.currentTimeMillis(); controlsVisible = true }
    // pasa sola al próximo capítulo al terminar
    LaunchedEffect(ended) {
        if (ended && next != null && t != null) { delay(1600); nav.replace(Screen.Player(t.id, next.second.url, next.first.number, next.second.number)) }
        else if (ended) controlsVisible = true
    }

    val playReq = remember { FocusRequester() }
    val contReq = remember { FocusRequester() }
    LaunchedEffect(Unit) { AutoFocusOnce(playReq) }
    LaunchedEffect(playing) { if (!playing) AutoFocusOnce(contReq) }

    // -- avisos de "-10 / +10" cuando se adelanta/atrasa con el control, sin tocar los botones --
    var backPulse by remember { mutableIntStateOf(0) }
    var fwdPulse by remember { mutableIntStateOf(0) }
    var showBack by remember { mutableStateOf(false) }
    var showFwd by remember { mutableStateOf(false) }
    LaunchedEffect(backPulse) { if (backPulse > 0) { showBack = true; delay(650); showBack = false } }
    LaunchedEffect(fwdPulse) { if (fwdPulse > 0) { showFwd = true; delay(650); showFwd = false } }

    fun doSeekBack() { player.seekBack(); backPulse++; bump() }
    fun doSeekForward() { player.seekForward(); fwdPulse++; bump() }
    fun doToggle() { if (player.isPlaying) player.pause() else player.play(); bump() }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .onPreviewKeyEvent { e ->
                // se intercepta ANTES que cualquier botón: así el control de la tele
                // (adelantar/atrasar/pausa) funciona siempre, esté lo que esté enfocado,
                // y la tele no muestra su propia ventanita de reproducción encima.
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.MediaPlayPause -> { doToggle(); true }
                    Key.MediaPlay -> { player.play(); bump(); true }
                    Key.MediaPause -> { player.pause(); bump(); true }
                    Key.DirectionLeft, Key.MediaRewind -> { doSeekBack(); true }
                    Key.DirectionRight, Key.MediaFastForward -> { doSeekForward(); true }
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
                    // subtítulos: misma tipografía del sitio, más arriba para no chocar con la barra de abajo
                    subtitleView?.apply {
                        setBottomPaddingFraction(0.16f)
                        setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 21f)
                        setApplyEmbeddedStyles(false)
                        setApplyEmbeddedFontSizes(false)
                        val gg = runCatching { ResourcesCompat.getFont(c, R.font.gg_book) }.getOrNull()
                        setStyle(
                            CaptionStyleCompat(
                                AColor.WHITE,
                                AColor.TRANSPARENT,
                                AColor.TRANSPARENT,
                                CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                                AColor.argb(190, 0, 0, 0),
                                gg
                            )
                        )
                    }
                }
            }
        )
        // capa invisible: cualquier botón del control despierta la interfaz
        if (!controlsVisible) WakeLayer(Modifier.fillMaxSize()) { bump() }

        // avisos de -10 / +10 (tipo Netflix), aparecen con cualquier adelantar/atrasar
        AnimatedVisibility(showBack, Modifier.align(Alignment.CenterStart).padding(start = 170.dp), enter = fadeIn(), exit = fadeOut()) {
            SeekFlash(back = true)
        }
        AnimatedVisibility(showFwd, Modifier.align(Alignment.CenterEnd).padding(end = 170.dp), enter = fadeIn(), exit = fadeOut()) {
            SeekFlash(back = false)
        }

        if (controlsVisible) {
            // en pausa se oscurece más el fondo, para que se note bien que está pausado
            if (!playing) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .5f)))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = .6f), .22f to Color.Transparent, .62f to Color.Transparent, 1f to Color.Black.copy(alpha = .85f))))

            if (playing) {
                // ---- reproduciendo: info arriba, pausa al medio, barra abajo ----
                Box(Modifier.align(Alignment.TopStart).padding(start = 64.dp, top = 44.dp).width(760.dp)) {
                    TopInfo(t, episode, s.season)
                }
                if (m.subsUrl.isNotBlank()) Box(Modifier.align(Alignment.TopEnd).padding(end = 64.dp, top = 44.dp)) {
                    CcButton(subsOn) {
                        subsOn = !subsOn
                        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(M3C.TRACK_TYPE_TEXT, !subsOn).build()
                        bump()
                    }
                }
                Focusable({ doToggle() }, Modifier.align(Alignment.Center).size(84.dp), shape = CircleShape, scale = 1.08f, ring = false, requester = playReq, onFocus = { bump() }) {
                    Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = .96f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Ic.pause, null, tint = Color.Black, modifier = Modifier.size(32.dp))
                    }
                }
                Column(Modifier.align(Alignment.BottomStart).padding(start = 64.dp, end = 64.dp, bottom = 48.dp).fillMaxWidth()) {
                    Scrub(posMs, durMs)
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(fmtTime(posMs), style = ts(13, color = Color.White.copy(alpha = .78f)))
                        Text(fmtTime(durMs), style = ts(13, color = Color.White.copy(alpha = .78f)))
                    }
                }
            } else {
                // ---- en pausa: tarjeta con logo, capítulo y controles, tipo Netflix ----
                if (m.subsUrl.isNotBlank()) Box(Modifier.align(Alignment.TopEnd).padding(end = 64.dp, top = 44.dp)) {
                    CcButton(subsOn) {
                        subsOn = !subsOn
                        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(M3C.TRACK_TYPE_TEXT, !subsOn).build()
                        bump()
                    }
                }
                Column(Modifier.align(Alignment.BottomStart).padding(start = 64.dp, bottom = 56.dp).width(760.dp)) {
                    if (t != null) TitleLogo(t, 220.dp, 42.dp, 26)
                    Spacer(Modifier.height(14.dp))
                    val head = if (episode != null) "T${s.season}:E${episode.number} · ${episode.title.ifBlank { "Episodio ${episode.number}" }}" else null
                    val desc = (episode?.description ?: t?.description).orEmpty()
                    Column {
                        if (head != null) Text(head, style = ts(18, FontWeight.Bold, Color.White))
                        if (desc.isNotBlank()) Text(desc, style = ts(16, FontWeight.Light, Color.White.copy(alpha = .8f)), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(22.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        PillButton("Continuar", { player.play(); bump() }, requester = contReq, onFocus = { bump() })
                        GlassButton(Ic.back) { nav.back() }
                        SmallGlass(back = true) { doSeekBack() }
                        SmallGlass(back = false) { doSeekForward() }
                    }
                }
            }
        }
        if (buffering && !ended) Box(Modifier.align(Alignment.Center)) {
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
    if (episode != null) {
        Text("T$season · E${episode.number}", style = ts(14, FontWeight.Bold, Color.White.copy(alpha = .62f)))
        Spacer(Modifier.height(3.dp))
        Text(episode.title.ifBlank { "Episodio ${episode.number}" }, style = ts(22, FontWeight.Bold, Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis)
    } else if (t != null) {
        Text(t.title, style = ts(22, FontWeight.Bold, Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Botón de subtítulos: siempre con fondo sólido para que se vea sobre cualquier video. */
@Composable
private fun CcButton(on: Boolean, onClick: () -> Unit) {
    Focusable(onClick, Modifier.height(44.dp), shape = RoundedCornerShape(99.dp), scale = 1.08f, ring = false, lift = true) { f ->
        Row(
            Modifier
                .background(if (f) Color.White else Color.Black.copy(alpha = .6f), RoundedCornerShape(99.dp))
                .border(1.3.dp, if (f) Color.White else if (on) C.accent.copy(alpha = .85f) else Color.White.copy(alpha = .4f), RoundedCornerShape(99.dp))
                .padding(horizontal = 16.dp).height(44.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(9.dp).background(if (f) Color.Black else if (on) C.accent else Color.White.copy(alpha = .4f), CircleShape)
            )
            Spacer(Modifier.width(9.dp))
            Text("Subtítulos", style = ts(13, FontWeight.Bold, if (f) Color.Black else Color.White))
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
@Composable
private fun SeekIcon(back: Boolean, color: Color, size: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = this.size.minDimension * .09f
            val sweep = if (back) -230f else 230f
            val start = if (back) 160f else -20f
            drawArc(color, startAngle = start, sweepAngle = sweep, useCenter = false, style = Stroke(sw, cap = StrokeCap.Round))
            val r = this.size.minDimension / 2
            val ang = Math.toRadians((start + sweep).toDouble())
            val cx = this.size.width / 2 + r * Math.cos(ang).toFloat()
            val cy = this.size.height / 2 + r * Math.sin(ang).toFloat()
            val aLen = this.size.minDimension * .17f
            val dir = if (back) -1 else 1
            drawLine(color, Offset(cx, cy), Offset(cx - aLen * dir, cy - aLen * 0.6f), sw * .9f, cap = StrokeCap.Round)
            drawLine(color, Offset(cx, cy), Offset(cx - aLen * dir, cy + aLen * 0.6f), sw * .9f, cap = StrokeCap.Round)
        }
        Text("10", style = ts((size.value * .27f), FontWeight.Bold, color))
    }
}

@Composable
private fun Scrub(posMs: Long, durMs: Long) {
    val frac = if (durMs > 0) (posMs.toFloat() / durMs).coerceIn(0f, 1f) else 0f
    Box(Modifier.fillMaxWidth().height(5.dp).background(Color.White.copy(alpha = .3f), RoundedCornerShape(99.dp))) {
        Box(Modifier.fillMaxWidth(frac).height(5.dp).background(C.accent, RoundedCornerShape(99.dp)))
    }
}

private fun fmtTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600; val m = (total % 3600) / 60; val sec = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

/** Pide foco una vez que el árbol se termine de componer (los botones pueden tardar un frame en existir). */
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
            .onKeyEvent {
                if (it.type == KeyEventType.KeyDown) { onWake(); true } else false
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
