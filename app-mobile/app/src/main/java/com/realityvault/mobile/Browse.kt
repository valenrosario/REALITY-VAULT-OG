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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.realityvault.tv.Store
import com.realityvault.tv.Title

private val gridPad @Composable get() = PaddingValues(start = 18.dp, end = 18.dp, top = 6.dp, bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 96.dp)

@Composable
private fun PosterGrid(list: List<Title>, nav: Nav, key: String, header: (@Composable () -> Unit)? = null) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(104.dp), state = nav.memory.grid(key), contentPadding = gridPad,
        horizontalArrangement = Arrangement.spacedBy(11.dp), verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (header != null) item(span = { GridItemSpan(maxLineSpan) }) { header() }
        items(list.distinctBy { it.id }, key = { it.id }) { t ->
            PosterCard(t, Modifier.aspectRatio(2f / 3f).tap({ nav.go(Screen.Detail(t.id)) }).clip(RoundedCornerShape(9.dp)))
        }
    }
}

/* ---------------- series, películas, mi lista ---------------- */

@Composable
fun GridScreen(nav: Nav, kind: String) {
    if (!Store.loaded) { Loading(); return }
    val pub = Store.published()
    var genre by remember(kind) { mutableStateOf(nav.memory.text["genre-$kind"] ?: "") }
    val base = when (kind) {
        "series" -> pub.filter { it.isSerie }
        "peliculas" -> pub.filter { !it.isSerie }
        else -> Store.myList.mapNotNull { id -> pub.firstOrNull { it.id == id } }
    }
    val genres = if (kind == "milista") emptyList() else base.flatMap { it.genres }.distinct().sorted()
    val list = if (genre.isBlank()) base else base.filter { genre in it.genres }
    val title = when (kind) { "series" -> "Series"; "peliculas" -> "Películas"; else -> "Mi lista" }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title, nav, "${list.size} título${if (list.size == 1) "" else "s"}")
        if (genres.isNotEmpty()) LazyRow(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Chip("Todos", genre.isBlank()) { genre = ""; nav.memory.text["genre-$kind"] = "" } }
            items(genres) { g -> Chip(g, genre == g) { genre = g; nav.memory.text["genre-$kind"] = g } }
        }
        if (kind == "milista" && Store.session == null) {
            Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(40.dp))
                Text("Entrá con tu cuenta para guardar series y películas en tu lista.", style = ts(15, color = C.fg2).copy(textAlign = TextAlign.Center))
                Spacer(Modifier.height(20.dp))
                PillButton("Entrar o crear cuenta", { nav.go(Screen.Login()) }, icon = null)
            }
        } else if (list.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.TopCenter) {
                Text(if (kind == "milista") "Todavía no guardaste nada. Tocá + en cualquier título." else "No hay títulos para mostrar.", style = ts(15, color = C.fg2).copy(textAlign = TextAlign.Center), modifier = Modifier.padding(top = 40.dp))
            }
        } else PosterGrid(list, nav, "$kind-$genre")
    }
}

/* ---------------- colección ---------------- */

@Composable
fun CollectionScreen(id: String, nav: Nav) {
    if (!Store.loaded) { Loading(); return }
    val pub = Store.published()
    val c = Store.colls.firstOrNull { it.id == id }
    val list = c?.items?.mapNotNull { i -> pub.firstOrNull { it.id == i } } ?: emptyList()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassButton(Ic.back, "Volver", { nav.back() }, size = 40.dp)
        }
        if (c == null || list.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) { Text("Esta colección no está disponible.", style = ts(17, FontWeight.Bold, Color.White)) }
        } else PosterGrid(list, nav, "coll-$id") {
            Column(Modifier.padding(bottom = 14.dp, top = 6.dp)) {
                Text("COLECCIÓN", style = ts(11, FontWeight.Bold, C.accent).copy(letterSpacing = 2.sp))
                Text(c.name, style = ts(28, FontWeight.Bold, Color.White))
                if (c.tagline.isNotBlank()) Text(c.tagline, style = ts(13, color = C.fg2), modifier = Modifier.padding(top = 4.dp), maxLines = 3)
                Text("${list.size} título${if (list.size == 1) "" else "s"}", style = ts(12, color = C.muted), modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

/* ---------------- títulos de una persona del reparto ---------------- */

@Composable
fun PersonScreen(name: String, nav: Nav) {
    val list = Store.published().filter { t -> t.cast.any { it.name.equals(name, true) } }
    val photo = Store.published().flatMap { it.cast }.firstOrNull { it.name.equals(name, true) && it.photo.isNotBlank() }?.photo ?: ""
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassButton(Ic.back, "Volver", { nav.back() }, size = 40.dp)
        }
        PosterGrid(list, nav, "person-$name") {
            Row(Modifier.padding(bottom = 14.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(64.dp).clip(CircleShape).background(Color.White.copy(alpha = .14f))) { if (photo.isNotBlank()) Img(photo, Modifier.fillMaxSize()) }
                Column(Modifier.padding(start = 14.dp)) {
                    Text(name, style = ts(22, FontWeight.Bold, Color.White))
                    Text("${list.size} título${if (list.size == 1) "" else "s"} en Reality Vault", style = ts(12, color = C.muted))
                }
            }
        }
    }
}

/* ---------------- buscar ---------------- */

// Misma idea que el buscador de la web: sin acentos, tolera errores de tipeo y ordena por relevancia.
private fun nrm(s: String): String =
    java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()
        .replace(Regex("[^a-z0-9\\s]"), " ").replace(Regex("\\s+"), " ").trim()

private fun levMax(a: String, b: String, max: Int): Int {
    if (kotlin.math.abs(a.length - b.length) > max) return max + 1
    var prev = IntArray(b.length + 1) { it }
    for (i in 1..a.length) {
        val cur = IntArray(b.length + 1); cur[0] = i; var rmin = i
        for (j in 1..b.length) {
            cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            if (cur[j] < rmin) rmin = cur[j]
        }
        if (rmin > max) return max + 1
        prev = cur
    }
    return prev[b.length]
}

private fun wordScore(qw: String, words: List<String>): Double {
    var best = 0.0
    for (w in words) {
        if (w.isEmpty()) continue
        val s = when {
            w == qw -> 10.0
            w.startsWith(qw) -> 8.0
            qw.length >= 3 && w.contains(qw) -> 5.0
            qw.length >= 4 -> {
                val max = if (qw.length >= 7) 2 else 1
                val d = minOf(levMax(qw, w, max), levMax(qw, w.take(qw.length), max))
                if (d <= max) (6 - d).toDouble() else 0.0
            }
            else -> 0.0
        }
        if (s > best) best = s
    }
    return best
}

fun searchTitles(q: String, pub: List<Title>): List<Title> {
    val qn = nrm(q); if (qn.isBlank()) return emptyList()
    val qws = qn.split(" ")
    return pub.mapNotNull { t ->
        val tt = nrm(t.title)
        val fields = listOf(tt to 6.0, t.genres.joinToString(" ") { nrm(it) } to 2.5, t.cast.joinToString(" ") { nrm(it.name) } to 3.0)
        val long = nrm(t.tagline + " " + t.description)
        var total = 0.0
        for (qw in qws) {
            var best = 0.0
            for ((txt, w) in fields) { if (txt.isEmpty()) continue; val s = wordScore(qw, txt.split(" ")) * w / 6.0; if (s > best) best = s }
            if (best == 0.0 && long.contains(qw)) best = 1.0
            if (best == 0.0) return@mapNotNull null
            total += best
        }
        if (tt.startsWith(qn)) total += 20 else if (tt.contains(qn)) total += 10
        t to total
    }.sortedByDescending { it.second }.map { it.first }
}

@Composable
fun SearchScreen(nav: Nav) {
    var q by remember { mutableStateOf(nav.memory.text["searchQ"] ?: "") }
    val req = remember { FocusRequester() }
    val kb = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { if (q.isBlank()) runCatching { req.requestFocus() } }
    val pub = Store.published()
    val n = q.trim().lowercase()
    val res = if (n.isBlank()) emptyList() else searchTitles(q, pub)
    // sin nada escrito se muestran los más vistos (o los más nuevos), así la pantalla nunca queda vacía
    val shown = if (n.isBlank()) Store.top10.mapNotNull { id -> pub.firstOrNull { it.id == id } }.ifEmpty { pub.sortedByDescending { it.createdAt }.take(12) } else res
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.statusBarsPadding().padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 10.dp)) {
            Text("Buscar", style = ts(26, FontWeight.Bold, Color.White))
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().height(48.dp).background(Color.White.copy(alpha = .09f), RoundedCornerShape(99.dp)).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Ic.search, null, tint = C.muted, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (q.isEmpty()) Text("Títulos, géneros, reparto…", style = ts(15, color = C.faint))
                    BasicTextField(
                        q, { q = it; nav.memory.text["searchQ"] = it }, singleLine = true,
                        textStyle = ts(15, color = C.fg), cursorBrush = SolidColor(C.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { kb?.hide() }),
                        modifier = Modifier.fillMaxWidth().focusRequester(req)
                    )
                }
                if (q.isNotEmpty()) Icon(Ic.close, "Borrar", tint = C.muted, modifier = Modifier.size(20.dp).tap({ q = ""; nav.memory.text["searchQ"] = "" }))
            }
        }
        if (n.isNotBlank() && res.isEmpty()) Text("No encontramos nada con “$q”.", style = ts(14, color = C.fg2), modifier = Modifier.padding(start = 18.dp, top = 8.dp))
        PosterGrid(shown, nav, "search") {
            if (shown.isNotEmpty()) Text(
                if (n.isBlank()) "Popular ahora" else "${shown.size} resultado${if (shown.size == 1) "" else "s"}",
                style = ts(13, FontWeight.Bold, Color.White.copy(alpha = .85f)), modifier = Modifier.padding(bottom = 4.dp)
            )
        }
    }
}
