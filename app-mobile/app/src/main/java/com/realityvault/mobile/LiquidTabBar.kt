package com.realityvault.mobile

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color as AColor
import android.util.Base64
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.realityvault.tv.Store
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import java.io.ByteArrayOutputStream

/**
 * Barra de abajo con el TabBar real de @onlymoein/liquidglass (React). Va en un WebView transparente
 * que carga una mini página empaquetada en assets/tabbar (React + la librería, sin internet).
 * El vidrio de la librería dobla lo que tiene DETRÁS dentro de la misma página; como lo de atrás es
 * la app nativa, acá se ve el vidrio, la lente que sigue al dedo y el movimiento, pero no el desenfoque
 * en vivo del contenido.
 */
@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
fun LiquidTabBar(nav: Nav) {
    val ctx = LocalContext.current
    val tab = when {
        nav.top == Screen.Home -> "home"
        nav.top == Screen.Search -> "search"
        nav.top == Screen.Mine || nav.top is Screen.Account -> "mine"
        else -> null
    }
    val web = remember {
        WebView(ctx).apply {
            setBackgroundColor(AColor.TRANSPARENT)
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false; isHorizontalScrollBarEnabled = false
            settings.javaScriptEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = false
        }
    }
    var ready by remember { androidx.compose.runtime.mutableStateOf(false) }
    // lo que pasa en la barra (tocar una pestaña) vuelve a la app nativa
    remember(web) {
        web.addJavascriptInterface(object {
            @JavascriptInterface fun onTab(v: String) {
                web.post { nav.section(when (v) { "search" -> Screen.Search; "mine" -> Screen.Mine; else -> Screen.Home }) }
            }
            @JavascriptInterface fun onReady() { web.post { ready = true } }
        }, "Android")
        web.loadUrl("file:///android_asset/tabbar/index.html")
        true
    }
    androidx.compose.runtime.DisposableEffect(web) { onDispose { web.removeJavascriptInterface("Android"); web.destroy() } }
    LaunchedEffect(ready, tab) { if (ready) web.evaluateJavascript("window.RVTab&&window.RVTab.setTab(${if (tab == null) "null" else "'$tab'"})", null) }
    val avatar = Store.session?.avatar
    LaunchedEffect(ready, avatar) {
        if (!ready) return@LaunchedEffect
        val res = avatarRes(avatar)
        val url = if (res == null) "null" else runCatching {
            val bmp = ResourcesCompat.getDrawable(ctx.resources, res, null)!!.toBitmap(96, 96)
            val out = ByteArrayOutputStream(); bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            "'data:image/png;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP) + "'"
        }.getOrDefault("null")
        web.evaluateJavascript("window.RVTab&&window.RVTab.setAvatar($url)", null)
    }
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 6.dp), contentAlignment = Alignment.Center) {
        AndroidView({ web }, Modifier.width(290.dp).height(84.dp))
    }
}
