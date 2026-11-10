package com.realityvault.mobile

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.requiredSize
import kotlinx.coroutines.launch
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
    val glass = rememberGlassBackdrop()
    Box(Modifier.fillMaxSize().background(C.bg)) {
        AnimatedContent(
            modifier = Modifier.fillMaxSize().then(if (GLASS_OK) Modifier.glassSource(glass) else Modifier.haze(haze)),
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
        AnimatedVisibility(nav.top.isTab, Modifier.align(Alignment.BottomCenter), enter = fadeIn(tween(200)), exit = fadeOut(tween(120))) { BottomBar(nav, haze, glass) }
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

/* ---------------- barra de abajo: píldora flotante de vidrio líquido ---------------- */

@Composable
internal fun BottomBar(nav: Nav, haze: dev.chrisbanes.haze.HazeState, glass: GlassBackdrop) {
    val shape = RoundedCornerShape(36.dp)
    val thumbShape = RoundedCornerShape(30.dp)
    val items = listOf(Screen.Home to "Inicio", Screen.Search to "Buscar", Screen.Mine to "Mi Vault")
    val itemW = 96.dp
    val barW = itemW * items.size + 12.dp; val barH = 68.dp
    val idx = items.indexOfFirst { (sc, _) -> nav.top == sc || (sc == Screen.Mine && nav.top is Screen.Account) }
    // la lente de la pestaña activa: cada borde es un resorte (el de adelante sale primero), así se estira al
    // deslizarse y se vuelve a cerrar, como una gota
    val tl = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(itemW.value * idx.coerceAtLeast(0)) }
    val tr = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(itemW.value * (idx.coerceAtLeast(0) + 1)) }
    androidx.compose.runtime.LaunchedEffect(idx) {
        if (idx < 0) return@LaunchedEffect
        val l = itemW.value * idx; val right = l > tl.value
        val lead = androidx.compose.animation.core.spring<Float>(dampingRatio = .78f, stiffness = 480f)
        val trail = androidx.compose.animation.core.spring<Float>(dampingRatio = .86f, stiffness = 240f)
        launch { tl.animateTo(l, if (right) trail else lead) }
        launch { tr.animateTo(l + itemW.value, if (right) lead else trail) }
    }
    val thumbA by androidx.compose.animation.core.animateFloatAsState(if (idx >= 0) 1f else 0f, tween(180), label = "thumbA")
    val margin = 26.dp
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp), contentAlignment = Alignment.Center) {
        if (GLASS_OK) {
            LiquidGlass(
                glass, Modifier.requiredSize(barW + margin * 2, barH + margin * 2), margin, 36.dp,
                thumb = { androidx.compose.ui.geometry.Rect(6f + tl.value, 6f, 6f + tr.value, barH.value - 6f) },
                thumbRadius = 30.dp, thumbAlpha = { thumbA }
            )
        }
        Box(
            Modifier.width(barW).height(barH)
                .then(if (GLASS_OK) Modifier else Modifier
                    .shadow(26.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
                    .clip(shape)
                    .hazeChild(haze, dev.chrisbanes.haze.HazeStyle(
                        backgroundColor = C.bg,
                        tints = listOf(dev.chrisbanes.haze.HazeTint(Color(0xFF2A2A2E).copy(alpha = .58f))),
                        blurRadius = 28.dp, noiseFactor = .06f
                    ))
                    .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = .30f), Color.White.copy(alpha = .06f))), shape))
                .padding(6.dp)
        ) {
            if (!GLASS_OK) Box(
                Modifier.offset(x = tl.value.dp).width((tr.value - tl.value).coerceAtLeast(0f).dp).fillMaxHeight().alpha(thumbA).clip(thumbShape)
                    .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = .22f), Color.White.copy(alpha = .10f))))
                    .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = .34f), Color.White.copy(alpha = .08f))), thumbShape)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                items.forEach { (sc, label) ->
                    val on = items.indexOf(sc to label) == idx
                    val tint by androidx.compose.animation.animateColorAsState(if (on) Color.White else Color.White.copy(alpha = .72f), tween(200), label = "tabTint")
                    Column(
                        Modifier.width(itemW).fillMaxHeight().clip(thumbShape).tap({ if (!on) nav.section(sc) }, .92f),
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
                        Text(label, style = ts(11.5f, FontWeight.Bold, tint), modifier = Modifier.padding(top = 4.dp))
                    }
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
