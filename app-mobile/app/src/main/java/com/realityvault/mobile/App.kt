package com.realityvault.mobile

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.realityvault.tv.Store

@Composable
fun App(nav: Nav, onExit: () -> Unit) {
    BackHandler {
        val top = nav.top
        if (top is Screen.Login && top.required) onExit()
        else if (!nav.back()) { if (nav.top != Screen.Home) nav.section(Screen.Home) else onExit() }
    }
    Box(Modifier.fillMaxSize().background(C.bg)) {
        AnimatedContent(
            targetState = nav.top,
            transitionSpec = {
                when (nav.dir) {
                    1 -> (slideInHorizontally(tween(280)) { it / 4 } + fadeIn(tween(280))) togetherWith (fadeOut(tween(200)))
                    -1 -> fadeIn(tween(220)) togetherWith (slideOutHorizontally(tween(260)) { it / 4 } + fadeOut(tween(220)))
                    else -> fadeIn(tween(220)) togetherWith fadeOut(tween(160))
                } as ContentTransform
            },
            label = "screens", contentKey = { it::class.simpleName + (it as? Screen.Detail)?.id + (it as? Screen.Collection)?.id + (it as? Screen.Person)?.name + (it as? Screen.Genre)?.name }
        ) { s ->
            when (s) {
                Screen.Splash -> SplashScreen(nav)
                is Screen.Player -> PlayerScreen(s, nav)
                is Screen.Detail -> DetailScreen(s.id, nav)
                is Screen.Login -> LoginScreen(s, nav)
                is Screen.Person -> PersonScreen(s.name, nav)
                is Screen.Collection -> CollectionScreen(s.id, nav)
                Screen.Account -> AccountScreen(nav)
                Screen.Home -> HomeScreen(nav)
                Screen.Series -> GridScreen(nav, "series")
                Screen.Movies -> GridScreen(nav, "peliculas")
                is Screen.Genre -> GridScreen(nav, "g:" + s.name)
                Screen.MyList -> GridScreen(nav, "milista")
                Screen.Search -> SearchScreen(nav)
            }
        }
        // la barra de abajo queda fija mientras se cambia de sección (no parpadea con la transición)
        AnimatedVisibility(nav.top.isTab, Modifier.align(Alignment.BottomCenter), enter = fadeIn(tween(200)), exit = fadeOut(tween(120))) { BottomBar(nav) }
        UpdateDialog(nav)
    }
}

/* ---------------- barra de abajo ---------------- */

@Composable
private fun BottomBar(nav: Nav, modifier: Modifier = Modifier) {
    val items = listOf<Triple<Screen, String, ImageVector>>(
        Triple(Screen.Home, "Inicio", Ic.home), Triple(Screen.Search, "Buscar", Ic.search),
        Triple(Screen.Series, "Series", Ic.tv), Triple(Screen.Movies, "Películas", Ic.film),
        Triple(Screen.MyList, "Mi lista", Ic.plus)
    )
    Box(modifier.fillMaxWidth().background(Brush.verticalGradient(0f to Color.Transparent, .35f to Color.Black.copy(alpha = .92f), 1f to Color.Black))) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(top = 14.dp).height(60.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            items.forEach { (sc, label, ic) ->
                val on = nav.top == sc
                Column(
                    Modifier.weight(1f).fillMaxSize().tap({ if (!on) nav.section(sc) }, .9f),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
                ) {
                    Icon(ic, label, tint = if (on) C.accent else Color.White.copy(alpha = .62f), modifier = Modifier.size(23.dp))
                    Text(label, style = ts(10.5f, if (on) FontWeight.Bold else FontWeight.Normal, if (on) C.accent else Color.White.copy(alpha = .62f)), modifier = Modifier.padding(top = 3.dp))
                }
            }
        }
    }
}

/** Botón redondo con la foto de perfil (abre "Mi cuenta"). */
@Composable
fun AvatarButton(nav: Nav, modifier: Modifier = Modifier) {
    val s = Store.session
    Box(
        modifier.size(36.dp).tap({ nav.go(Screen.Account) }, .9f).clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color(0xFFFFD1E6), C.accent, Color(0xFFB04C86)))),
        contentAlignment = Alignment.Center
    ) {
        val ph = avatarRes(s?.avatar)
        if (ph != null) androidx.compose.foundation.Image(painterResource(ph), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text(if (s == null) "★" else (s.email.firstOrNull()?.uppercase() ?: "★"), style = ts(16, FontWeight.Bold, Color.White))
    }
}

/** Encabezado simple para las pantallas con lista (Series, Películas, Mi lista). */
@Composable
fun ScreenHeader(title: String, nav: Nav, subtitle: String? = null, back: Boolean = false) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (back) { GlassButton(Ic.back, "Volver", { nav.back() }, size = 40.dp); androidx.compose.foundation.layout.Spacer(Modifier.size(12.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, style = ts(26, FontWeight.Bold, Color.White))
            if (subtitle != null) Text(subtitle, style = ts(12, color = C.muted))
        }
        AvatarButton(nav)
    }
}
