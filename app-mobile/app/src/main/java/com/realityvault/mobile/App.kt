package com.realityvault.mobile

import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.runtime.getValue
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
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
    val haze = androidx.compose.runtime.remember { dev.chrisbanes.haze.HazeState() }
    Box(Modifier.fillMaxSize().background(C.bg)) {
        AnimatedContent(
            modifier = Modifier.fillMaxSize().haze(haze),
            targetState = nav.top,
            transitionSpec = {
                when (nav.dir) {
                    1 -> (slideInHorizontally(tween(280)) { it / 4 } + fadeIn(tween(280))) togetherWith (fadeOut(tween(200)))
                    -1 -> fadeIn(tween(220)) togetherWith (slideOutHorizontally(tween(260)) { it / 4 } + fadeOut(tween(220)))
                    else -> fadeIn(tween(220)) togetherWith fadeOut(tween(160))
                } as ContentTransform
            },
            label = "screens", contentKey = { it::class.simpleName + (it as? Screen.Detail)?.id + (it as? Screen.Collection)?.id + (it as? Screen.Person)?.name + (it as? Screen.Genre)?.name + (it as? Screen.Admin)?.let { a -> a.page + a.id } }
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
                Screen.Notifications -> NotificationsScreen(nav)
                Screen.Mine -> MineScreen(nav)
                is Screen.Admin -> AdminScreen(s, nav)
                Screen.MyList -> GridScreen(nav, "milista")
                Screen.Search -> SearchScreen(nav)
            }
        }
        // la barra de abajo queda fija mientras se cambia de sección (no parpadea con la transición)
        AnimatedVisibility(nav.top.isTab, Modifier.align(Alignment.BottomCenter), enter = fadeIn(tween(200)), exit = fadeOut(tween(120))) { BottomBar(nav, haze) }
        UpdateDialog(nav)
        // al agregar/sacar series de Mi lista, la campanita toma nota de sus capítulos actuales
        androidx.compose.runtime.LaunchedEffect(Store.myList, Store.titles) { Notifs.scan() }
        // al entrar o salir de una cuenta: se vuelve a comprobar si es la de administrador (sin cerrar la app)
        androidx.compose.runtime.LaunchedEffect(Store.session?.userId) {
            AdminData.reset()
            if (Store.session != null) runCatching { AdminData.checkAdmin() }
        }
    }
}

/* ---------------- barra de abajo: píldora flotante de vidrio esmerilado ---------------- */

@Composable
private fun BottomBar(nav: Nav, haze: dev.chrisbanes.haze.HazeState) {
    val shape = RoundedCornerShape(34.dp)
    val items = listOf(Screen.Home to "Inicio", Screen.Search to "Buscar", Screen.Mine to "Mi Vault")
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 10.dp), contentAlignment = Alignment.Center) {
        Row(
            Modifier.padding(horizontal = 24.dp).widthIn(max = 420.dp).fillMaxWidth().height(70.dp)
                .shadow(24.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(shape)
                .hazeChild(haze, dev.chrisbanes.haze.HazeStyle(
                    backgroundColor = C.bg,
                    tints = listOf(dev.chrisbanes.haze.HazeTint(Color(0xFF2A2A2E).copy(alpha = .62f))),
                    blurRadius = 26.dp, noiseFactor = .06f
                ))
                .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = .22f), Color.White.copy(alpha = .06f))), shape)
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { (sc, label) ->
                val on = nav.top == sc || (sc == Screen.Mine && nav.top is Screen.Account)
                val bg by androidx.compose.animation.animateColorAsState(if (on) Color.White.copy(alpha = .13f) else Color.Transparent, tween(220), label = "tab")
                val tint = if (on) Color.White else Color.White.copy(alpha = .62f)
                Column(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(28.dp)).background(bg).tap({ if (!on) nav.section(sc) }, .92f),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
                ) {
                    when (sc) {
                        Screen.Mine -> {
                            val ph = avatarRes(Store.session?.avatar)
                            Box(Modifier.size(25.dp).clip(RoundedCornerShape(7.dp)).background(Brush.radialGradient(listOf(Color(0xFFFFD1E6), C.accent, Color(0xFFB04C86)))).border(if (on) 1.5.dp else 0.dp, Color.White, RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) {
                                if (ph != null) androidx.compose.foundation.Image(painterResource(ph), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                else Text("★", style = ts(12, color = Color.White))
                            }
                        }
                        else -> Icon(if (sc == Screen.Home) Ic.home else Ic.search, label, tint = tint, modifier = Modifier.size(25.dp))
                    }
                    Text(label, style = ts(12, FontWeight.Bold, tint), modifier = Modifier.padding(top = 4.dp))
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
        modifier.size(36.dp).tap({ nav.section(Screen.Mine) }, .9f).clip(CircleShape)
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
