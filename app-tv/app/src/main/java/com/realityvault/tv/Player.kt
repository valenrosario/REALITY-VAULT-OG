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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

@Composable
fun PlayerScreen(s: Screen.Player, nav: Nav) {
    val media = remember(s.url) { mediaFor(s.url) }
    LaunchedEffect(s) {
        if (!s.trailer) Store.recordWatch(s.titleId, s.season, s.episode)
    }
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        when (media) {
            is Media.Native -> NativePlayer(media)
            is Media.Embed -> EmbedPlayer(media.url)
            null -> Text("Este enlace no se puede reproducir.", style = ts(20, color = C.fg2))
        }
    }
}

/** Reproductor propio de Android: se maneja entero con el control (pausa, adelantar, etc.). */
@OptIn(UnstableApi::class)
@Composable
fun NativePlayer(m: Media.Native) {
    val ctx = LocalContext.current
    val player = remember(m.url) {
        ExoPlayer.Builder(ctx).setSeekBackIncrementMs(10_000).setSeekForwardIncrementMs(10_000).build().apply {
            val item = MediaItem.Builder().setUri(m.url).apply { if (m.hls) setMimeType(MimeTypes.APPLICATION_M3U8) }.build()
            setMediaItem(item); prepare(); playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { c ->
            PlayerView(c).apply {
                this.player = player
                useController = true
                setShowNextButton(false); setShowPreviousButton(false)
                controllerShowTimeoutMs = 3500
                keepScreenOn = true
                isFocusable = true; isFocusableInTouchMode = true
                setBackgroundColor(AColor.BLACK)
                post { requestFocus() }
            }
        }
    )
}

/** Reproductor del servicio donde está el video (Drive, OK.ru, YouTube…). */
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
