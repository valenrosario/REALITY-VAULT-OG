package com.realityvault.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.realityvault.tv.Store

/* ---------------- "Mi Vault": el espacio personal (como "Mi Netflix") ---------------- */

@Composable
fun MineScreen(nav: Nav) {
    val s = Store.session
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    if (s == null) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("Tu espacio en Reality Vault", style = ts(22, FontWeight.Bold, Color.White).copy(textAlign = TextAlign.Center))
            Text("Entrá para tener tu lista, seguir donde dejaste y recibir avisos de capítulos nuevos.", style = ts(14.5f, color = C.fg2).copy(textAlign = TextAlign.Center), modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(22.dp))
            PillButton("Entrar o crear cuenta", { nav.go(Screen.Login()) }, icon = null)
        }
        return
    }
    val pub = Store.published()
    val list = Store.myList.mapNotNull { id -> pub.firstOrNull { it.id == id } }
    val cont = Store.progress.mapNotNull { p -> pub.firstOrNull { it.id == p.titleId } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottom + 110.dp)) {
        item(key = "head") {
            Column(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(92.dp).clip(RoundedCornerShape(22.dp)).background(Brush.radialGradient(listOf(Color(0xFFFFD1E6), C.accent, Color(0xFFB04C86)))), contentAlignment = Alignment.Center) {
                    val ph = avatarRes(s.avatar)
                    if (ph != null) androidx.compose.foundation.Image(painterResource(ph), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Text("★", style = ts(36, color = Color.White))
                }
                Text(s.email.substringBefore("@"), style = ts(22, FontWeight.Bold, Color.White), modifier = Modifier.padding(top = 12.dp))
                Text(s.email, style = ts(13, color = C.muted), modifier = Modifier.padding(top = 2.dp))
            }
        }
        if (AdminData.isAdmin) item(key = "admin") {
            Row(
                Modifier.padding(start = 18.dp, end = 18.dp, top = 24.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF3A1230), Color(0xFF1B0F1E))))
                    .border(1.dp, C.accent.copy(alpha = .35f), RoundedCornerShape(18.dp))
                    .tap({ nav.go(Screen.Admin()) }, .98f).padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(46.dp).background(C.accent.copy(alpha = .18f), CircleShape), contentAlignment = Alignment.Center) { Icon(Ic.settings, null, tint = C.accent, modifier = Modifier.size(24.dp)) }
                Column(Modifier.padding(start = 14.dp).weight(1f)) {
                    Text("Panel de control", style = ts(17, FontWeight.Bold, Color.White))
                    Text("Catálogo, banners, filas, colecciones y más", style = ts(12.5f, color = Color.White.copy(alpha = .7f)), modifier = Modifier.padding(top = 2.dp))
                }
                Icon(Ic.chevron, null, tint = Color.White.copy(alpha = .7f), modifier = Modifier.size(20.dp))
            }
        }
        item(key = "menu") {
            Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 18.dp).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = .06f))) {
                MenuRow(Ic.bell, "Notificaciones", if (Notifs.unread > 0) "${Notifs.unread} nueva${if (Notifs.unread > 1) "s" else ""}" else null, Notifs.unread > 0) { nav.go(Screen.Notifications) }
                MenuRow(Ic.plus, "Mi lista", "${list.size}", false) { nav.go(Screen.MyList) }
                MenuRow(Ic.user, "Mi cuenta", null, false) { nav.go(Screen.Account) }
            }
        }
        if (cont.isNotEmpty()) item(key = "cont") { MineRow("Seguir viendo", cont.take(20), nav, wide = true) }
        if (list.isNotEmpty()) item(key = "list") { MineRow("Mi lista", list, nav) }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, value: String?, dot: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().tap(onClick, .99f).padding(horizontal = 16.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
        Text(label, style = ts(15.5f, FontWeight.Bold, Color.White), modifier = Modifier.padding(start = 14.dp).weight(1f))
        if (dot) Box(Modifier.padding(end = 8.dp).size(9.dp).background(Color(0xFFFF3B47), CircleShape))
        if (value != null) Text(value, style = ts(13.5f, color = C.muted), modifier = Modifier.padding(end = 8.dp))
        Icon(Ic.chevron, null, tint = Color.White.copy(alpha = .5f), modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun MineRow(title: String, titles: List<com.realityvault.tv.Title>, nav: Nav, wide: Boolean = false) {
    Column(Modifier.padding(top = 26.dp)) {
        Text(title, style = ts(18, FontWeight.Bold, Color.White), modifier = Modifier.padding(start = 20.dp, bottom = 12.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(titles, key = { it.id }) { t ->
                if (wide) {
                    val p = Store.progress.firstOrNull { it.titleId == t.id }
                    WideCard(t, p, Modifier.width(220.dp).aspectRatio(16f / 9f).tap({
                        val tg = Store.playTarget(t); if (tg != null) nav.play(t, tg.url, tg.season, tg.episode) else nav.go(Screen.Detail(t.id))
                    }))
                } else PosterCard(t, Modifier.width(118.dp).aspectRatio(2f / 3f).tap({ nav.go(Screen.Detail(t.id)) }).clip(RoundedCornerShape(8.dp)))
            }
        }
    }
}
