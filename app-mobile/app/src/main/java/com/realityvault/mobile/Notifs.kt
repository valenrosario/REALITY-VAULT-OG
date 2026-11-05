package com.realityvault.mobile

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.realityvault.tv.Store
import org.json.JSONArray
import org.json.JSONObject

/* ---------------- campanita: capítulos nuevos de las series de Mi lista ----------------
   Cuando en el panel se saca un capítulo de "Próximamente" (o se agrega uno ya disponible) en una
   serie que está en tu lista, aparece un aviso con punto rojo. Se compara con lo que la app ya
   conocía de cada serie, así que la primera vez que ve una serie no avisa de capítulos viejos. */

object Notifs {
    data class Item(val titleId: String, val season: Int, val episode: Int, val epTitle: String, val at: Long)

    var items by mutableStateOf<List<Item>>(emptyList()); private set
    private var seenAt by mutableLongStateOf(0L)
    val unread get() = items.count { it.at > seenAt }

    private lateinit var app: Context
    private val prefs get() = app.getSharedPreferences("rv_notifs", Context.MODE_PRIVATE)

    fun init(c: Context) {
        app = c.applicationContext
        seenAt = prefs.getLong("seenAt", 0L)
        items = runCatching {
            val a = JSONArray(prefs.getString("items", "[]"))
            (0 until a.length()).map { i -> a.getJSONObject(i).let { Item(it.getString("t"), it.getInt("s"), it.getInt("e"), it.optString("n"), it.getLong("at")) } }
        }.getOrDefault(emptyList())
    }

    /** Revisa las series de Mi lista contra lo último que se vio de cada una. */
    fun scan() {
        if (!::app.isInitialized || !Store.loaded || Store.session == null) return
        val snap = runCatching { JSONObject(prefs.getString("snap", "{}")) }.getOrDefault(JSONObject())
        val now = System.currentTimeMillis()
        val fresh = mutableListOf<Item>()
        Store.myList.mapNotNull { Store.byId(it) }.filter { it.isSerie && it.status != "borrador" && !it.isSoon }.forEach { t ->
            val ready = t.seasons.filter { !it.soon }.flatMap { s -> s.episodes.filter { it.ready }.map { e -> Triple(s.number, e.number, e.title) } }
            val keys = ready.map { "${it.first}-${it.second}" }
            val prev = snap.optJSONArray(t.id)
            if (prev != null) {
                val old = (0 until prev.length()).map { prev.getString(it) }.toSet()
                ready.filter { "${it.first}-${it.second}" !in old }.forEach { (s, e, n) -> fresh += Item(t.id, s, e, n, now) }
            }
            snap.put(t.id, JSONArray(keys))
        }
        prefs.edit().putString("snap", snap.toString()).apply()
        if (fresh.isNotEmpty()) {
            items = (fresh + items).distinctBy { "${it.titleId}-${it.season}-${it.episode}" }.take(40)
            val a = JSONArray(); items.forEach { a.put(JSONObject().put("t", it.titleId).put("s", it.season).put("e", it.episode).put("n", it.epTitle).put("at", it.at)) }
            prefs.edit().putString("items", a.toString()).apply()
        }
    }

    fun markSeen() {
        seenAt = System.currentTimeMillis()
        prefs.edit().putLong("seenAt", seenAt).apply()
    }
}

/** Campanita: punto rojo cuando hay capítulos nuevos sin ver. */
@Composable
fun BellButton(nav: Nav, size: Dp = 40.dp, iconSize: Dp = 23.dp) {
    Box(Modifier.size(size).tap({ nav.go(Screen.Notifications) }, .9f), contentAlignment = Alignment.Center) {
        Icon(Ic.bell, "Notificaciones", tint = Color.White, modifier = Modifier.size(iconSize))
        if (Notifs.unread > 0) Box(
            Modifier.align(Alignment.Center).offset(x = 7.dp, y = (-8).dp).size(11.dp)
                .background(Color(0xFFFF3B47), CircleShape).border(2.dp, Color.Black.copy(alpha = .55f), CircleShape)
        )
    }
}

@Composable
fun NotificationsScreen(nav: Nav) {
    LaunchedEffect(Unit) { Notifs.markSeen() }
    val list = Notifs.items.filter { Store.byId(it.titleId) != null }
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 12.dp, end = 18.dp, top = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassButton(Ic.back, "Volver", { nav.back() }, size = 40.dp)
            Text("Notificaciones", style = ts(24, FontWeight.Bold, Color.White), modifier = Modifier.padding(start = 14.dp))
        }
        if (list.isEmpty()) Box(Modifier.fillMaxSize().padding(36.dp), contentAlignment = Alignment.Center) {
            Text(
                if (Store.session == null) "Entrá con tu cuenta y agregá series a Mi lista: te avisamos acá cuando salga un capítulo nuevo."
                else "Todavía no hay novedades. Cuando salga un capítulo nuevo de una serie de Mi lista, te avisamos acá.",
                style = ts(15, color = C.fg2).copy(textAlign = TextAlign.Center)
            )
        } else LazyColumn(contentPadding = PaddingValues(top = 6.dp, bottom = bottom + 24.dp)) {
            items(list, key = { "${it.titleId}-${it.season}-${it.episode}" }) { n ->
                val t = Store.byId(n.titleId)!!
                val ep = t.seasons.firstOrNull { it.number == n.season }?.episodes?.firstOrNull { it.number == n.episode }
                Row(Modifier.fillMaxWidth().tap({ nav.go(Screen.Detail(t.id)) }, .98f).padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(140.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(9.dp)).background(C.surface)) {
                        val img = ep?.thumb?.ifBlank { null } ?: t.wide
                        if (img.isNotBlank()) Img(img, Modifier.fillMaxSize()) else Placeholder(t, Modifier.fillMaxSize())
                    }
                    Column(Modifier.padding(start = 14.dp).weight(1f)) {
                        Text("NUEVO CAPÍTULO", style = ts(10.5f, FontWeight.Bold, C.accent))
                        Text(t.title, style = ts(15, FontWeight.Bold, Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                        Text("T${n.season}:E${n.episode}" + (ep?.title ?: n.epTitle).let { if (it.isNotBlank()) " · $it" else "" },
                            style = ts(12.5f, color = Color.White.copy(alpha = .7f)), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                    }
                    if (n.at > 0) Spacer(Modifier.width(4.dp))
                }
            }
        }
    }
}
