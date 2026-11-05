package com.realityvault.mobile

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.mediarouter.app.MediaRouteChooserDialog
import androidx.mediarouter.app.MediaRouteControllerDialog
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaTrack
import com.google.android.gms.cast.TextTrackStyle
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastOptions as GCastOptions
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.framework.media.CastMediaOptions
import com.google.android.gms.cast.framework.media.NotificationOptions
import com.google.android.gms.common.images.WebImage
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaRouteSelector
import com.google.android.gms.cast.CastMediaControlIntent

/* ---------------- Chromecast ----------------
   Usa el receptor multimedia estándar de Google: reproduce .mp4 y .m3u8 directo desde el enlace.
   Los .mkv / .ts se mandan en su copia ".web.mp4" (la que arma GitHub), igual que la web. */

/** Configuración que Android lee desde el manifiesto. */
class CastOptions : OptionsProvider {
    override fun getCastOptions(context: Context): GCastOptions =
        GCastOptions.Builder()
            .setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
            .setCastMediaOptions(
                CastMediaOptions.Builder()
                    .setNotificationOptions(NotificationOptions.Builder().setTargetActivityClassName(MainActivity::class.java.name).build())
                    .build()
            )
            .setStopReceiverApplicationWhenEndingSession(true)
            .build()

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}

object Cast {
    private var context: CastContext? = null
    /** Sesión conectada a un Chromecast / TV con Google TV (null = no se está transmitiendo). */
    var session by mutableStateOf<CastSession?>(null); private set
    val available get() = context != null
    val deviceName get() = session?.castDevice?.friendlyName ?: "la TV"
    /** Último minuto conocido en la TV, para seguir en el celular al dejar de transmitir. */
    var lastPosMs = 0L

    private val listener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(s: CastSession, id: String) { session = s }
        override fun onSessionResumed(s: CastSession, wasSuspended: Boolean) { session = s }
        override fun onSessionEnded(s: CastSession, error: Int) { session = null }
        override fun onSessionSuspended(s: CastSession, reason: Int) { session = null }
        override fun onSessionStartFailed(s: CastSession, error: Int) { session = null }
        override fun onSessionResumeFailed(s: CastSession, error: Int) { session = null }
        override fun onSessionStarting(s: CastSession) {}
        override fun onSessionEnding(s: CastSession) {}
        override fun onSessionResuming(s: CastSession, id: String) {}
    }

    /** Sin Google Play Services (algunos celulares) simplemente no aparece el botón. */
    fun init(c: Context) {
        val cc = runCatching { CastContext.getSharedInstance(c.applicationContext) }.getOrNull() ?: return
        context = cc
        cc.sessionManager.addSessionManagerListener(listener, CastSession::class.java)
        session = cc.sessionManager.currentCastSession?.takeIf { it.isConnected }
    }

    fun stop() { runCatching { context?.sessionManager?.endCurrentSession(true) } }

    /** Lista de dispositivos (o, si ya está conectado, el control para desconectar). */
    fun showPicker(activityCtx: Context) {
        val themed = android.view.ContextThemeWrapper(activityCtx, androidx.appcompat.R.style.Theme_AppCompat_NoActionBar)
        runCatching {
            if (session != null) MediaRouteControllerDialog(themed).show()
            else MediaRouteChooserDialog(themed).apply {
                routeSelector = MediaRouteSelector.Builder()
                    .addControlCategory(CastMediaControlIntent.categoryForCast(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID))
                    .addControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK).build()
            }.show()
        }
    }

    fun canCast(url: String) = !Regex("\\.(mkv|ts|m2ts|mts)([?#]|$)", RegexOption.IGNORE_CASE).containsMatchIn(url)

    private fun mime(url: String) = when {
        Regex("\\.m3u8([?#]|$)", RegexOption.IGNORE_CASE).containsMatchIn(url) -> "application/x-mpegurl"
        Regex("\\.webm([?#]|$)", RegexOption.IGNORE_CASE).containsMatchIn(url) -> "video/webm"
        else -> "video/mp4"
    }

    /** Manda el video a la TV, desde [startMs]. Si ya se está viendo ese mismo video allá, no lo reinicia. */
    fun load(url: String, subs: String, title: String, line: String, image: String, startMs: Long) {
        val rmc = session?.remoteMediaClient ?: return
        if (rmc.mediaInfo?.contentId == url) return
        val meta = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
            putString(MediaMetadata.KEY_TITLE, title)
            if (line.isNotBlank()) putString(MediaMetadata.KEY_SUBTITLE, line)
            if (image.isNotBlank()) addImage(WebImage(Uri.parse(image)))
        }
        val tracks = if (subs.isNotBlank()) listOf(
            MediaTrack.Builder(1, MediaTrack.TYPE_TEXT).setName("Español").setSubtype(MediaTrack.SUBTYPE_SUBTITLES)
                .setContentId(subs).setContentType("text/vtt").setLanguage("es").build()
        ) else emptyList()
        val style = TextTrackStyle().apply {
            fontScale = 1.25f; foregroundColor = android.graphics.Color.WHITE
            backgroundColor = 0x99000000.toInt(); edgeType = TextTrackStyle.EDGE_TYPE_DROP_SHADOW; edgeColor = android.graphics.Color.BLACK
        }
        val info = MediaInfo.Builder(url).setStreamType(MediaInfo.STREAM_TYPE_BUFFERED).setContentType(mime(url))
            .setMetadata(meta).setMediaTracks(tracks).setTextTrackStyle(style).build()
        val req = MediaLoadRequestData.Builder().setMediaInfo(info).setAutoplay(true).setCurrentTime(startMs.coerceAtLeast(0))
            .apply { if (tracks.isNotEmpty()) setActiveTrackIds(longArrayOf(1)) }.build()
        rmc.load(req)
    }
}

/** Botón de transmitir (se pone rosa mientras está conectado). */
@Composable
fun CastButton(size: Dp = 40.dp, iconSize: Dp = 24.dp, modifier: Modifier = Modifier) {
    if (!Cast.available) return
    val ctx = LocalContext.current
    Box(modifier.size(size).tap({ Cast.showPicker(ctx) }, .9f), contentAlignment = Alignment.Center) {
        Icon(Ic.cast, "Transmitir a la TV", tint = if (Cast.session != null) C.accent else Color.White, modifier = Modifier.size(iconSize))
    }
}
