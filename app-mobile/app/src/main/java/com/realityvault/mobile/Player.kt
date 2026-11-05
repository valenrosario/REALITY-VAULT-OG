package com.realityvault.mobile

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C as M3C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player as M3Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.realityvault.tv.Episode
import com.realityvault.tv.Media
import com.realityvault.tv.Season
import com.realityvault.tv.Store
import com.realityvault.tv.Title
import com.realityvault.tv.isHttp
import com.realityvault.tv.mediaFor
import com.realityvault.tv.posResumable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.abs

/** Estado compartido con la actividad para la ventanita flotante (imagen en imagen). */
object PipState {
    var active by mutableStateOf(false)   // hay un video reproduciéndose
    var inPip by mutableStateOf(false)    // la app está en la ventanita flotante
}

private fun Context.activity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }
    return null
}

private fun safeSub(u: String) = if (isHttp(u)) u else ""

private fun fmtTime(ms: Long): String {
    val s = (ms.coerceAtLeast(0) / 1000).toInt()
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

/**
 * Los MKV no siempre suenan bien en el celular. Igual que la web: si ya existe la copia ".web.mp4"
 * (la crea GitHub solo), se usa esa; si no, se reproduce el original.
 */
private suspend fun resolveUrl(u: String): String {
    if (!Regex("\\.(mkv|ts|m2ts|mts)([?#]|$)", RegexOption.IGNORE_CASE).containsMatchIn(u)) return u
    val alt = u.replace(Regex("\\.(mkv|ts|m2ts|mts)(?=[?#]|$)", RegexOption.IGNORE_CASE), ".web.mp4")
    return withContext(Dispatchers.IO) {
        runCatching {
            val c = URL(alt).openConnection() as HttpURLConnection
            try { c.requestMethod = "HEAD"; c.connectTimeout = 3000; c.readTimeout = 3000; if (c.responseCode in 200..299) alt else u }
            finally { c.disconnect() }
        }.getOrDefault(u)
    }
}

/* ---------------- pantalla del reproductor ---------------- */

@Composable
fun PlayerScreen(s: Screen.Player, nav: Nav) {
    val ctx = LocalContext.current
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
    val media = remember(s.url, subsUrl) { mediaFor(s.url, subsUrl) }
    val next = remember(s, t) { if (t != null && !s.extra) Store.nextAfter(t, s.season, s.episode) else null }
    LaunchedEffect(s) { if (!s.trailer && !s.extra) Store.recordWatch(s.titleId, s.season, s.episode) }

    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        when (media) {
            is Media.Native -> androidx.compose.runtime.key(s) { NativePlayer(media, t, episode, s, next, nav) }
            // YouTube, Vimeo y demás: sin WebView, se abren en la app o el navegador del celular.
            is Media.Embed -> {
                LaunchedEffect(s.url) {
                    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(s.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    nav.back()
                }
                CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp)
            }
            null -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                Text("Este video no está disponible en este momento.", style = ts(17, FontWeight.Bold, Color.White).copy(textAlign = TextAlign.Center))
                Spacer(Modifier.height(18.dp))
                PillButton("Volver", { nav.back() }, icon = Ic.back)
            }
        }
    }
}

/* ---------------- reproductor propio de Android (Media3 / ExoPlayer) ---------------- */

private data class Opt(val label: String, val selected: Boolean, val onPick: () -> Unit)

@OptIn(UnstableApi::class)
@Composable
fun NativePlayer(m: Media.Native, t: Title?, episode: Episode?, s: Screen.Player, next: Pair<Season, Episode>?, nav: Nav) {
    val ctx = LocalContext.current
    val activity = remember { ctx.activity() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    // 1) qué URL se reproduce de verdad (MKV → copia web si existe)
    val url by produceState<String?>(null, m.url) { value = if (m.hls) m.url else resolveUrl(m.url) }
    if (url == null) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp) }; BackHandler { nav.back() }; return }

    // 2) pantalla completa, horizontal y brillo; se restaura todo al salir
    var portrait by remember { mutableStateOf(false) }
    DisposableEffect(activity) {
        val w = activity?.window
        if (w != null && activity != null) {
            val ctl = WindowCompat.getInsetsController(w, w.decorView)
            ctl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            ctl.hide(WindowInsetsCompat.Type.systemBars())
            w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            if (w != null && activity != null) {
                WindowCompat.getInsetsController(w, w.decorView).show(WindowInsetsCompat.Type.systemBars())
                w.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                val lp = w.attributes; lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE; w.attributes = lp
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
            PipState.active = false
        }
    }
    LaunchedEffect(portrait) {
        activity?.requestedOrientation = if (portrait) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    var userPaused by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var ended by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var stuck by remember { mutableStateOf(false) }
    var fatal by remember { mutableStateOf<String?>(null) }
    var offline by remember { mutableStateOf(false) }
    var posMs by remember { mutableLongStateOf(0L) }
    var durMs by remember { mutableLongStateOf(0L) }
    var bufMs by remember { mutableLongStateOf(0L) }
    var controlsVisible by remember { mutableStateOf(true) }
    var locked by remember { mutableStateOf(false) }
    var scrubbing by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf(false) }
    var subsOn by remember(m.subsUrl) { mutableStateOf(m.subsUrl.isNotBlank()) }
    var lastTouch by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var tracks by remember { mutableStateOf(Tracks.EMPTY) }
    var speed by remember { mutableFloatStateOf(1f) }
    var cueText by remember { mutableStateOf("") }
    var retries by remember { mutableIntStateOf(0) }
    var attempt by remember { mutableIntStateOf(0) }

    val player = remember(url, m.subsUrl, attempt) {
        val http = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true).setConnectTimeoutMs(15_000).setReadTimeoutMs(20_000)
        ExoPlayer.Builder(ctx)
            .setMediaSourceFactory(DefaultMediaSourceFactory(ctx).setDataSourceFactory(http))
            .setSeekBackIncrementMs(10_000).setSeekForwardIncrementMs(10_000)
            .setHandleAudioBecomingNoisy(true)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(M3C.USAGE_MEDIA).setContentType(M3C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
            .build().apply {
                val subs = if (m.subsUrl.isNotBlank()) listOf(
                    MediaItem.SubtitleConfiguration.Builder(Uri.parse(m.subsUrl))
                        .setId("rv-es").setMimeType(MimeTypes.TEXT_VTT).setLanguage("es")
                        .setSelectionFlags(M3C.SELECTION_FLAG_DEFAULT).build()
                ) else emptyList()
                val item = MediaItem.Builder().setUri(url!!).setSubtitleConfigurations(subs)
                    .apply { if (m.hls) setMimeType(MimeTypes.APPLICATION_M3U8) }.build()
                setMediaItem(item); prepare(); playWhenReady = true
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setPreferredTextLanguage("es").setPreferredAudioLanguage("es")
                    .setTrackTypeDisabled(M3C.TRACK_TYPE_TEXT, m.subsUrl.isBlank()).build()
            }
    }

    // --- seguir desde donde te quedaste (igual que la web y la TV) ---
    val track = t != null && !s.trailer && !s.extra
    var resumed by remember(player) { mutableStateOf(false) }
    fun savePos() {
        if (!track) return
        val d = player.duration; val c = player.currentPosition
        if (d <= 0 || d == M3C.TIME_UNSET) return
        val id = t!!.id; val se = s.season; val ep = s.episode
        Store.bg.launch { Store.savePos(id, se, ep, c / 1000, d / 1000) }
    }
    DisposableEffect(player) {
        val l = object : M3Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing; if (!playing && resumed) savePos() }
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) { userPaused = !playWhenReady; PipState.active = playWhenReady }
            override fun onTracksChanged(tr: Tracks) {
                tracks = tr
                // Si el MKV trae subtítulos propios (en inglés, por ejemplo) y hay un .vtt cargado en el panel, el .vtt manda.
                if (m.subsUrl.isBlank()) return
                val g = tr.groups.firstOrNull { it.type == M3C.TRACK_TYPE_TEXT && (0 until it.length).any { i -> it.getTrackFormat(i).id?.endsWith("rv-es") == true } } ?: return
                if (g.isSelected && tr.groups.count { it.type == M3C.TRACK_TYPE_TEXT && it.isSelected } == 1) return
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, 0)).build()
            }
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == M3Player.STATE_BUFFERING
                if (state == M3Player.STATE_READY) {
                    stuck = false; fatal = null; retries = 0
                    durMs = player.duration.coerceAtLeast(0)
                    if (!resumed) {
                        resumed = true
                        val saved = if (track) Store.getPos(t!!.id, s.season, s.episode) else null
                        if (posResumable(saved) && saved!!.first * 1000 < player.duration - 45_000) player.seekTo(saved.first * 1000)
                    }
                }
                ended = state == M3Player.STATE_ENDED
                if (ended) savePos()
            }
            // Recuperación ante errores: si se cortó la red reintenta solo (conservando el minuto); si no, avisa.
            override fun onPlayerError(e: PlaybackException) {
                val netErr = e.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                    e.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
                    e.errorCode == PlaybackException.ERROR_CODE_TIMEOUT
                when {
                    e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> { player.seekToDefaultPosition(); player.prepare() }
                    netErr && retries < 4 -> { retries++; scope.launch { delay(1500L * retries); if (!player.isReleased()) { player.prepare(); player.play() } } }
                    netErr -> fatal = "No hay conexión estable. Revisá tu internet y probá de nuevo."
                    e.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED || e.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES ||
                        e.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED || e.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED ->
                        fatal = "Tu celular no puede reproducir el formato de este video."
                    else -> fatal = "No se pudo reproducir este video."
                }
            }
            // subtítulos propios: tipografía del sitio y cuadro negro, bien arriba del borde
            override fun onCues(cueGroup: CueGroup) { cueText = cueGroup.cues.joinToString("\n") { it.text?.toString().orEmpty() }.trim() }
        }
        player.addListener(l)
        onDispose { if (resumed) savePos(); player.removeListener(l); player.release() }
    }
    // guarda cada 15 segundos (por si se cierra la app o se corta el celular)
    LaunchedEffect(player) { while (true) { delay(15_000); if (resumed && player.isPlaying) savePos() } }
    // reloj de progreso
    LaunchedEffect(player) {
        while (true) {
            if (!scrubbing) posMs = player.currentPosition.coerceAtLeast(0)
            durMs = player.duration.coerceAtLeast(0); bufMs = player.bufferedPosition.coerceAtLeast(0)
            delay(400)
        }
    }
    // "Saltear intro / resumen" cargado en el panel (capítulo, extra o película)
    val skips = remember(s, t, episode) {
        when {
            s.trailer || t == null -> emptyList()
            episode != null -> episode.skips
            s.extra -> t.seasons.flatMap { it.extras }.firstOrNull { it.url == s.url }?.skips ?: emptyList()
            !t.isSerie -> t.skips
            else -> emptyList()
        }
    }
    // buffering eterno: si pasan 25 s sin avanzar, se ofrece reintentar
    LaunchedEffect(buffering, player) { if (buffering) { delay(25_000); if (buffering) stuck = true } else stuck = false }

    // --- ciclo de vida: al minimizar se pausa y se guarda; al volver queda donde estaba ---
    DisposableEffect(lifecycle, player) {
        val obs = LifecycleEventObserver { _, ev ->
            if (ev == Lifecycle.Event.ON_STOP) {
                val inPip = activity?.isInPictureInPictureMode == true
                if (!inPip) { player.pause(); savePos() }
            }
            if (ev == Lifecycle.Event.ON_START) { controlsVisible = true; lastTouch = System.currentTimeMillis() }
        }
        lifecycle.addObserver(obs); onDispose { lifecycle.removeObserver(obs) }
    }
    // --- cambios de red: si volvió internet, se recupera solo ---
    DisposableEffect(player) {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                scope.launch {
                    offline = false
                    if (fatal != null && fatal!!.contains("conexión")) { fatal = null; retries = 0; player.prepare(); player.play() }
                    else if (player.playbackState == M3Player.STATE_IDLE && fatal == null) { player.prepare() }
                }
            }
            override fun onLost(network: Network) { scope.launch { offline = true } }
        }
        runCatching { cm.registerDefaultNetworkCallback(cb) }
        onDispose { runCatching { cm.unregisterNetworkCallback(cb) } }
    }

    // oculta los controles solos mientras reproduce
    LaunchedEffect(lastTouch, isPlaying, controlsVisible, scrubbing, panel, locked) {
        if (controlsVisible && isPlaying && !scrubbing && !panel) { delay(3500); if (System.currentTimeMillis() - lastTouch >= 3400) controlsVisible = false }
    }
    fun bump() { lastTouch = System.currentTimeMillis(); controlsVisible = true }

    // --- siguiente capítulo, como las plataformas de streaming ---
    var nextOff by remember(player) { mutableStateOf(false) }
    val remainMs = durMs - posMs
    val showNext = next != null && t != null && !nextOff && durMs > 120_000 && (ended || (remainMs in 1..20_000))
    fun playNext() { if (next != null && t != null) nav.replace(Screen.Player(t.id, next.second.url, next.first.number, next.second.number)) }
    LaunchedEffect(ended) {
        if (ended && next != null && t != null && !nextOff) { delay(500); if (!nextOff) playNext() }
        else if (ended) { delay(1200); nav.back() }   // nada más para ver: vuelve solo a la ficha
    }

    // --- brillo y volumen con gestos ---
    val audio = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVol = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var volume by remember { mutableFloatStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVol) }
    var brightness by remember {
        mutableFloatStateOf(
            (activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0f })
                ?: (runCatching { Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f }.getOrNull() ?: .5f)
        )
    }
    var hud by remember { mutableStateOf<String?>(null) }   // "vol" | "bri" | null
    var hudTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(hudTick) { if (hudTick > 0) { delay(900); hud = null } }
    fun setBrightness(v: Float) {
        brightness = v.coerceIn(0.02f, 1f)
        activity?.window?.let { w -> val lp = w.attributes; lp.screenBrightness = brightness; w.attributes = lp }
        hud = "bri"; hudTick++
    }
    fun setVolume(v: Float) {
        volume = v.coerceIn(0f, 1f)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (volume * maxVol).toInt(), 0)
        hud = "vol"; hudTick++
    }

    // --- avisos de -10 / +10 ---
    var flash by remember { mutableStateOf(0) }     // -1 atrás, 1 adelante, 0 nada
    var flashTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(flashTick) { if (flashTick > 0) { delay(650); flash = 0 } }
    fun seekBy(dir: Int) {
        if (dir < 0) player.seekBack() else player.seekForward()
        flash = dir; flashTick++; posMs = player.currentPosition; bump()
    }
    fun toggle() { if (player.playWhenReady) player.pause() else { if (ended) player.seekTo(0); player.play() }; bump() }

    BackHandler(enabled = panel) { panel = false }
    BackHandler(enabled = showNext) { nextOff = true }
    BackHandler(enabled = locked) { controlsVisible = true; lastTouch = System.currentTimeMillis() }
    BackHandler(enabled = !panel && !showNext && !locked) { nav.back() }

    val inPip = PipState.inPip
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { c ->
                PlayerView(c).apply {
                    useController = false
                    keepScreenOn = true
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setBackgroundColor(android.graphics.Color.BLACK)
                    // los subtítulos los dibujamos nosotros (misma tipografía que el sitio)
                    subtitleView?.visibility = android.view.View.GONE
                }
            },
            update = { it.player = player }
        )

        if (!inPip) {
            // capa de gestos: tocar = mostrar/ocultar controles, doble toque = ±10 s, arrastrar vertical = brillo (izq.) / volumen (der.)
            Box(
                Modifier.fillMaxSize()
                    .pointerInput(locked) {
                        detectTapGestures(
                            onTap = { if (controlsVisible) controlsVisible = false else bump() },
                            onDoubleTap = { off ->
                                if (locked) return@detectTapGestures
                                when { off.x < size.width * .38f -> seekBy(-1); off.x > size.width * .62f -> seekBy(1); else -> toggle() }
                            }
                        )
                    }
                    .pointerInput(locked) {
                        if (locked) return@pointerInput
                        var left = true
                        detectVerticalDragGestures(
                            onDragStart = { off -> left = off.x < size.width / 2f },
                            onVerticalDrag = { _, dy ->
                                val d = -dy / size.height * 1.3f
                                if (left) setBrightness(brightness + d) else setVolume(volume + d)
                            }
                        )
                    }
            )

            // subtítulos
            if (subsOn && cueText.isNotBlank()) {
                // tamaño según la pantalla (como en las plataformas de streaming): ~6 % del lado corto
                val cfg = androidx.compose.ui.platform.LocalConfiguration.current
                val subSize = (minOf(cfg.screenWidthDp, cfg.screenHeightDp) * .062f).coerceIn(19f, 34f)
                Box(Modifier.align(Alignment.BottomCenter).padding(bottom = if (controlsVisible && !locked) 100.dp else 22.dp).fillMaxWidth(.88f), contentAlignment = Alignment.Center) {
                    Text(cueText, textAlign = TextAlign.Center,
                        style = ts(subSize, FontWeight.Bold, Color.White).copy(
                            lineHeight = (subSize * 1.22f).sp,
                            shadow = androidx.compose.ui.graphics.Shadow(Color.Black, androidx.compose.ui.geometry.Offset(0f, 2f), 7f)
                        ),
                        modifier = Modifier.background(Color.Black.copy(alpha = .5f), RoundedCornerShape(8.dp)).padding(horizontal = 14.dp, vertical = 4.dp))
                }
            }

            // aviso de ±10 s
            AnimatedVisibility(flash != 0, Modifier.align(if (flash < 0) Alignment.CenterStart else Alignment.CenterEnd).padding(horizontal = 56.dp), enter = fadeIn(), exit = fadeOut()) {
                Column(Modifier.background(Color.Black.copy(alpha = .5f), RoundedCornerShape(18.dp)).padding(horizontal = 18.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(if (flash < 0) Ic.replay else Ic.forward, null, tint = Color.White, modifier = Modifier.size(28.dp))
                    Text(if (flash < 0) "−10 s" else "+10 s", style = ts(13, FontWeight.Bold, Color.White))
                }
            }

            // brillo / volumen
            if (hud != null) Level(
                if (hud == "bri") Ic.sun else if (volume <= 0f) Ic.volumeOff else Ic.volume,
                if (hud == "bri") brightness else volume,
                Modifier.align(if (hud == "bri") Alignment.CenterStart else Alignment.CenterEnd).padding(horizontal = 28.dp)
            )

            // buffering discreto
            if ((buffering && !ended) && fatal == null && !stuck) Box(Modifier.align(Alignment.Center)) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp, modifier = Modifier.size(34.dp))
            }
            if (offline && fatal == null) Text(
                "Sin conexión · esperando internet…", style = ts(12, FontWeight.Bold, Color.White),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp).background(Color.Black.copy(alpha = .7f), RoundedCornerShape(99.dp)).padding(horizontal = 14.dp, vertical = 6.dp)
            )

            // error / conexión lenta
            if (fatal != null || stuck) Column(
                Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = .78f), RoundedCornerShape(18.dp)).padding(24.dp).widthIn(max = 340.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(fatal ?: "La conexión está muy lenta.", style = ts(15, FontWeight.Bold, Color.White).copy(textAlign = TextAlign.Center))
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton("Reintentar", { val p = player.currentPosition; fatal = null; stuck = false; retries = 0; player.seekTo(p); player.prepare(); player.play() }, icon = null)
                    PillButton("Volver", { nav.back() }, icon = null, dark = true)
                }
            }

            // botón "Saltear intro / resumen"
            val skip = if (skips.isEmpty() || scrubbing) null else skips.firstOrNull { posMs >= it.fromMs && posMs < it.toMs - 500 }
            AnimatedVisibility(
                skip != null && !panel && !locked && fatal == null && !(showNext && next != null),
                Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = if (controlsVisible) 104.dp else 30.dp),
                enter = fadeIn() + androidx.compose.animation.slideInHorizontally { it / 3 }, exit = fadeOut()
            ) {
                var last by remember { mutableStateOf(skip) }
                if (skip != null) last = skip
                val k = last ?: return@AnimatedVisibility
                Row(
                    Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xD90A080B)).border(1.dp, Color.White.copy(alpha = .55f), RoundedCornerShape(12.dp))
                        .tap({
                            val target = if (durMs > 0) minOf(k.toMs, durMs - 1000) else k.toMs
                            player.seekTo(target); posMs = target; if (!player.playWhenReady) { userPaused = false; player.play() }
                        }, .95f)
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(k.label, style = ts(15, FontWeight.Bold, Color.White))
                    Spacer(Modifier.width(10.dp))
                    Icon(Ic.next, null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }

            // siguiente capítulo
            if (showNext && next != null && t != null) NextCard(next, remainMs, ended, Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 104.dp), { nextOff = true }, { playNext() })

            // ---- controles ----
            if (locked) {
                AnimatedVisibility(controlsVisible, Modifier.align(Alignment.CenterStart).padding(start = 28.dp), enter = fadeIn(), exit = fadeOut()) {
                    GlassButton(Ic.lock, "Desbloquear controles", { locked = false; bump() }, size = 48.dp)
                }
            } else AnimatedVisibility(controlsVisible, Modifier.fillMaxSize(), enter = fadeIn(), exit = fadeOut()) {
                Box(Modifier.fillMaxSize()
                    .background(Brush.verticalGradient(0f to Color.Black.copy(alpha = .65f), .3f to Color.Black.copy(alpha = .15f), .65f to Color.Black.copy(alpha = .15f), 1f to Color.Black.copy(alpha = .85f)))
                    .pointerInput(Unit) { detectTapGestures(onTap = { controlsVisible = false }) }
                ) {
                    // arriba: volver, título y botones
                    Row(Modifier.align(Alignment.TopStart).fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        GlassButton(Ic.back, "Volver", { nav.back() }, size = 40.dp)
                        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                            Text(t?.title ?: "", style = ts(15, FontWeight.Bold, Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val sub = when {
                                episode != null -> "T${s.season}:E${episode.number} · " + episode.title.ifBlank { "Episodio ${episode.number}" }
                                s.extra -> "Extra"; s.trailer -> "Tráiler"; else -> ""
                            }
                            if (sub.isNotBlank()) Text(sub, style = ts(12, color = Color.White.copy(alpha = .75f)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            GlassButton(Ic.settings, "Audio, subtítulos y calidad", { panel = true; controlsVisible = false }, size = 40.dp)
                            GlassButton(Ic.lock, "Bloquear controles", { locked = true; controlsVisible = true; lastTouch = System.currentTimeMillis() }, size = 40.dp)
                            GlassButton(if (portrait) Ic.fullscreen else Ic.fullscreenExit, "Girar pantalla", { portrait = !portrait; bump() }, size = 40.dp)
                        }
                    }
                    // centro: -10 / play-pausa / +10
                    Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(34.dp), verticalAlignment = Alignment.CenterVertically) {
                        SeekBtn(true) { seekBy(-1) }
                        Box(Modifier.size(70.dp).tap({ toggle() }, .92f).clip(CircleShape).background(Color.White.copy(alpha = .96f)), contentAlignment = Alignment.Center) {
                            Icon(if (userPaused || ended) Ic.play else Ic.pause, if (userPaused) "Reproducir" else "Pausar", tint = Color.Black, modifier = Modifier.size(30.dp))
                        }
                        SeekBtn(false) { seekBy(1) }
                    }
                    // abajo: barra de progreso y tiempos
                    Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 18.dp)) {
                        SeekBar(posMs, durMs, bufMs, {
                            scrubbing = true
                        }, { f -> posMs = (f * durMs).toLong() }, { f ->
                            val target = (f * durMs).toLong(); player.seekTo(target); posMs = target; scrubbing = false; bump()
                        })
                        Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(fmtTime(posMs), style = ts(12.5f, color = Color.White.copy(alpha = .85f)))
                            if (next != null && !s.extra) Row(Modifier.tap({ playNext() }).padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Ic.next, null, tint = Color.White, modifier = Modifier.size(15.dp)); Spacer(Modifier.width(6.dp))
                                Text("Siguiente episodio", style = ts(12, FontWeight.Bold, Color.White))
                            }
                            Text("-" + fmtTime((durMs - posMs).coerceAtLeast(0)), style = ts(12.5f, color = Color.White.copy(alpha = .85f)))
                        }
                    }
                }
            }

            // panel de audio / subtítulos / calidad / velocidad
            if (panel) {
                SettingsPanel(
                    Modifier.fillMaxSize(),
                    audio = audioOptions(tracks, player),
                    subs = subtitleOptions(tracks, player, subsOn) { subsOn = it },
                    quality = qualityOptions(tracks, player),
                    speed = listOf(.5f, .75f, 1f, 1.25f, 1.5f, 2f).map { v -> Opt(if (v == 1f) "Normal" else "${v}x".replace(".0x", "x"), speed == v) { speed = v; player.setPlaybackSpeed(v) } },
                    onClose = { panel = false; bump() }
                )
            }
        }
    }
}

private fun ExoPlayer.isReleased(): Boolean = runCatching { playbackState; false }.getOrDefault(true)

/* ---------------- opciones de pistas ---------------- */

private fun trackLabel(f: Format, i: Int, audio: Boolean): String {
    val lang = f.language?.takeIf { it != "und" }?.let { Locale(it).getDisplayLanguage(Locale("es")).replaceFirstChar { c -> c.uppercase() } }
    val base = f.label?.takeIf { it.isNotBlank() } ?: lang ?: if (audio) "Audio ${i + 1}" else "Subtítulo ${i + 1}"
    val ch = if (audio && f.channelCount > 0) when { f.channelCount >= 6 -> " · 5.1"; f.channelCount == 2 -> " · Estéreo"; else -> "" } else ""
    return base + ch
}

@OptIn(UnstableApi::class)
private fun audioOptions(tr: Tracks, p: ExoPlayer): List<Opt> {
    val out = mutableListOf<Opt>()
    tr.groups.filter { it.type == M3C.TRACK_TYPE_AUDIO }.forEach { g ->
        for (i in 0 until g.length) if (g.isTrackSupported(i)) {
            out += Opt(trackLabel(g.getTrackFormat(i), out.size, true), g.isTrackSelected(i)) {
                p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, i)).build()
            }
        }
    }
    return if (out.size > 1) out else emptyList()
}

@OptIn(UnstableApi::class)
private fun subtitleOptions(tr: Tracks, p: ExoPlayer, on: Boolean, setOn: (Boolean) -> Unit): List<Opt> {
    val out = mutableListOf<Opt>()
    var any = false
    tr.groups.filter { it.type == M3C.TRACK_TYPE_TEXT }.forEach { g ->
        for (i in 0 until g.length) if (g.isTrackSupported(i)) {
            any = true
            out += Opt(trackLabel(g.getTrackFormat(i), out.size, false), on && g.isTrackSelected(i)) {
                setOn(true)
                p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().setTrackTypeDisabled(M3C.TRACK_TYPE_TEXT, false)
                    .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, i)).build()
            }
        }
    }
    if (!any) return emptyList()
    return listOf(Opt("Sin subtítulos", !on) {
        setOn(false); p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().setTrackTypeDisabled(M3C.TRACK_TYPE_TEXT, true).build()
    }) + out
}

@OptIn(UnstableApi::class)
private fun qualityOptions(tr: Tracks, p: ExoPlayer): List<Opt> {
    val vids = mutableListOf<Triple<Tracks.Group, Int, Int>>()
    tr.groups.filter { it.type == M3C.TRACK_TYPE_VIDEO }.forEach { g -> for (i in 0 until g.length) if (g.isTrackSupported(i)) vids += Triple(g, i, g.getTrackFormat(i).height) }
    if (vids.size < 2) return emptyList()
    val manual = p.trackSelectionParameters.overrides.keys.any { it.type == M3C.TRACK_TYPE_VIDEO }
    val auto = Opt("Automática", !manual) { p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().clearOverridesOfType(M3C.TRACK_TYPE_VIDEO).build() }
    return listOf(auto) + vids.sortedByDescending { it.third }.map { (g, i, h) ->
        Opt(if (h > 0) "${h}p" else "Pista ${i + 1}", manual && g.isTrackSelected(i)) {
            p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, i)).build()
        }
    }
}

/* ---------------- piezas de la interfaz ---------------- */

/** Ajustes a pantalla completa, en columnas (audio · subtítulos · calidad · velocidad), como en Netflix o Disney+. */
@Composable
private fun SettingsPanel(modifier: Modifier, audio: List<Opt>, subs: List<Opt>, quality: List<Opt>, speed: List<Opt>, onClose: () -> Unit) {
    val cols = listOf("Audio" to audio, "Subtítulos" to subs, "Calidad" to quality, "Velocidad" to speed).filter { it.second.isNotEmpty() }
    Box(modifier.background(Color(0xF00A080B)).pointerInput(Unit) { detectTapGestures { } }) {
        GlassButton(Ic.close, "Cerrar", onClose, Modifier.align(Alignment.TopEnd).padding(top = 16.dp, end = 20.dp), size = 42.dp)
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().padding(start = 28.dp, end = 28.dp, top = 26.dp, bottom = 12.dp)) {
            val gap = 30.dp
            if (maxWidth > 560.dp) {
                val w = ((maxWidth - 70.dp - gap * (cols.size - 1)) / cols.size.coerceAtLeast(1)).coerceAtMost(250.dp)
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally)) {
                    cols.forEach { (n, o) ->
                        Column(Modifier.width(w).fillMaxHeight().verticalScroll(rememberScrollState())) { Section(n, o) }
                    }
                }
            } else Column(Modifier.fillMaxSize().padding(top = 40.dp).verticalScroll(rememberScrollState())) {
                cols.forEach { (n, o) -> Section(n, o); Spacer(Modifier.height(18.dp)) }
            }
        }
    }
}

@Composable
private fun Section(title: String, opts: List<Opt>) {
    Text(title, style = ts(18, FontWeight.Bold, Color.White), modifier = Modifier.padding(top = 10.dp, bottom = 10.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = .12f)))
    Spacer(Modifier.height(6.dp))
    opts.forEach { o ->
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (o.selected) Color.White.copy(alpha = .08f) else Color.Transparent)
                .tap(o.onPick, .98f).padding(horizontal = 10.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) { if (o.selected) Icon(Ic.check, null, tint = C.accent, modifier = Modifier.size(18.dp)) }
            Text(o.label, style = ts(15.5f, if (o.selected) FontWeight.Bold else FontWeight.Normal, if (o.selected) Color.White else Color.White.copy(alpha = .66f)),
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 10.dp))
        }
    }
}

@Composable
private fun SeekBtn(back: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(54.dp).tap(onClick, .9f).clip(CircleShape).background(Color.Black.copy(alpha = .35f)), contentAlignment = Alignment.Center) {
        Icon(if (back) Ic.replay else Ic.forward, if (back) "Retroceder 10 segundos" else "Adelantar 10 segundos", tint = Color.White, modifier = Modifier.size(34.dp))
        Text("10", style = ts(9.5f, FontWeight.Bold, Color.White), modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun Level(icon: ImageVector, level: Float, modifier: Modifier = Modifier) {
    Column(modifier.background(Color.Black.copy(alpha = .55f), RoundedCornerShape(99.dp)).padding(vertical = 12.dp, horizontal = 9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.width(5.dp).height(120.dp).clip(RoundedCornerShape(9.dp)).background(Color.White.copy(alpha = .3f)), contentAlignment = Alignment.BottomCenter) {
            Box(Modifier.fillMaxWidth().fillMaxHeight(level.coerceIn(0f, 1f)).background(Color.White))
        }
        Spacer(Modifier.height(10.dp))
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

/** Barra de progreso: toque para saltar, arrastre para buscar; muestra lo ya cargado. */
@Composable
private fun SeekBar(pos: Long, dur: Long, buffered: Long, onStart: () -> Unit, onScrub: (Float) -> Unit, onEnd: (Float) -> Unit) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val frac = drag ?: if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
    val bufFrac = if (dur > 0) (buffered.toFloat() / dur).coerceIn(0f, 1f) else 0f
    Box(
        Modifier.fillMaxWidth().height(34.dp)
            .pointerInput(dur) { detectTapGestures { off -> if (dur > 0) onEnd((off.x / size.width).coerceIn(0f, 1f)) } }
            .pointerInput(dur) {
                detectHorizontalDragGestures(
                    onDragStart = { off -> drag = (off.x / size.width).coerceIn(0f, 1f); onStart() },
                    onHorizontalDrag = { ch, _ -> val f = (ch.position.x / size.width).coerceIn(0f, 1f); drag = f; onScrub(f) },
                    onDragEnd = { val f = drag; drag = null; if (f != null && dur > 0) onEnd(f) },
                    onDragCancel = { val f = drag; drag = null; if (f != null && dur > 0) onEnd(f) }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(9.dp)).background(Color.White.copy(alpha = .25f))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(bufFrac).background(Color.White.copy(alpha = .35f)))
            Box(Modifier.fillMaxHeight().fillMaxWidth(frac).background(C.accent))
        }
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
            val thumb = if (drag != null) 18.dp else 13.dp
            Box(Modifier.padding(start = (maxWidth * frac - thumb / 2).coerceAtLeast(0.dp)).size(thumb).background(C.accent, CircleShape))
        }
    }
}

@Composable
private fun NextCard(next: Pair<Season, Episode>, remainMs: Long, ended: Boolean, modifier: Modifier, onCancel: () -> Unit, onPlay: () -> Unit) {
    val secs = if (ended) 0 else ((remainMs + 999) / 1000).toInt()
    Column(modifier.widthIn(max = 280.dp).background(Color(0xE6101014), RoundedCornerShape(14.dp)).border(1.dp, Color.White.copy(alpha = .14f), RoundedCornerShape(14.dp)).padding(14.dp)) {
        Text("SIGUIENTE EPISODIO · en $secs s", style = ts(10, FontWeight.Bold, C.accent))
        Text("T${next.first.number}:E${next.second.number} · " + next.second.title.ifBlank { "Episodio ${next.second.number}" }, style = ts(14, FontWeight.Bold, Color.White), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("Reproducir ahora", onPlay, Modifier.height(38.dp), icon = Ic.play)
            PillButton("Cancelar", onCancel, Modifier.height(38.dp), icon = null, dark = true)
        }
    }
}
