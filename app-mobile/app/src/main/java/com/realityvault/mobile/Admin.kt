package com.realityvault.mobile

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/* ======================================================================
   Panel de control dentro de la app (solo cuenta de administrador)
   Mismo contenido que el panel de la web, pensado para el celular: vistas previas reales de cómo
   se ve cada imagen en la app, recortes con los dedos y los cambios se ven al instante.
   ====================================================================== */

@Composable
fun AdminScreen(s: Screen.Admin, nav: Nav) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        if (!AdminData.isAdmin) AdminData.checkAdmin()
        if (AdminData.isAdmin && !AdminData.loaded) runCatching { AdminData.load() }.onFailure { AdminToast.show("No se pudieron cargar los datos", true) }
    }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF140C12), Color.Black)))) {
        when {
            !AdminData.isAdmin -> Column(Modifier.fillMaxSize()) {
                AdminTop("Panel de control", null, nav)
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("Esta cuenta no tiene permiso para usar el panel.", style = ts(16, color = C.fg2).copy(textAlign = TextAlign.Center))
                }
            }
            !AdminData.loaded -> Column(Modifier.fillMaxSize()) {
                AdminTop("Panel de control", null, nav)
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = C.accent, strokeWidth = 2.5.dp) }
            }
            else -> when (s.page) {
                "catalogo" -> AdminCatalog(nav)
                "titulo" -> AdminTitle(s.id, nav)
                "banners" -> AdminBanners(nav)
                "banner" -> AdminBanner(s.id, nav)
                "colecciones" -> AdminColls(nav)
                "coleccion" -> AdminColl(s.id, nav)
                "filas" -> AdminRows(nav)
                "fila" -> AdminRow(s.id, nav)
                "generos" -> AdminGenres(nav)
                "sitio" -> AdminSite(nav)
                else -> AdminHome(nav) { scope.launch { runCatching { AdminData.load() } } }
            }
        }
        AdminToastHost(Modifier.align(Alignment.BottomCenter))
    }
}

private val bottomPad @Composable get() = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

/* ---------------- inicio del panel ---------------- */

@Composable
private fun AdminHome(nav: Nav, reload: () -> Unit) {
    val v = AdminData.ver
    val ts0 = AdminData.titles
    val series = ts0.count { it.optString("type") == "serie" }
    val drafts = ts0.count { it.optString("status") == "borrador" }
    val soonT = ts0.count { it.optString("availability") == "proximamente" }
    // capítulos marcados "Próximamente": lo que más se toca, a un toque de publicarlo
    val soonEps = remember(v) {
        ts0.filter { it.optString("type") == "serie" }.flatMap { t ->
            val ss = t.optJSONArray("seasons") ?: JSONArray()
            (0 until ss.length()).flatMap { i ->
                val s = ss.getJSONObject(i); val es = s.optJSONArray("episodes") ?: JSONArray()
                (0 until es.length()).mapNotNull { j -> es.getJSONObject(j).takeIf { it.optString("availability") == "proximamente" }?.let { Triple(t, s, it) } }
            }
        }
    }
    val scope = rememberCoroutineScope()
    LazyColumn(contentPadding = PaddingValues(bottom = bottomPad + 30.dp)) {
        item {
            AdminTop("Panel de control", "Los cambios se ven al instante en la web, la TV y el celular", nav) {
                if (AdminData.loading) CircularProgressIndicator(color = C.accent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                else Box(Modifier.size(40.dp).tap(reload, .9f), contentAlignment = Alignment.Center) { Icon(Ic.replay, "Actualizar", tint = Color.White, modifier = Modifier.size(22.dp)) }
            }
        }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Stat("${ts0.size}", "títulos") }
                item { Stat("$series", "series") }
                item { Stat("${ts0.size - series}", "películas") }
                item { Stat("$drafts", "borradores") }
                item { Stat("$soonT", "próximamente") }
            }
        }
        item {
            Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigAction("Nueva serie", Ic.tv, Modifier.weight(1f)) { nav.go(Screen.Admin("titulo", "new-serie")) }
                BigAction("Nueva película", Ic.film, Modifier.weight(1f)) { nav.go(Screen.Admin("titulo", "new-pelicula")) }
            }
        }
        item {
            val tiles = listOf(
                Triple("catalogo", "Catálogo", "${ts0.size} títulos") to Ic.clapper,
                Triple("banners", "Banners", "${AdminData.banners.size} en la portada") to Ic.home,
                Triple("colecciones", "Colecciones", "${AdminData.colls.length()} colecciones") to Ic.heart,
                Triple("filas", "Filas destacadas", "${AdminData.rows.length()} filas") to Ic.film,
                Triple("generos", "Géneros", "Renombrar y ordenar") to Ic.info,
                Triple("sitio", "Sitio", "Logo, pie y redes") to Ic.settings
            )
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                tiles.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { (t, ic) -> SectionTile(t.second, t.third, ic, Modifier.weight(1f)) { nav.go(Screen.Admin(t.first)) } }
                    }
                }
            }
        }
        if (soonEps.isNotEmpty()) item {
            ACard("Capítulos en \"Próximamente\"", "Tocá Publicar cuando el capítulo ya esté listo: aparece al instante y avisa con la campanita a quien tenga la serie en su lista.", Modifier.padding(top = 14.dp)) {
                soonEps.take(12).forEach { (t, s, e) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(76.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(7.dp)).background(C.surface)) {
                            val img = e.optString("thumb").ifBlank { t.optString("thumbWide").ifBlank { t.optString("backdrop") } }
                            if (img.isNotBlank()) Img(img, Modifier.fillMaxSize())
                        }
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(t.optString("title"), style = ts(14, FontWeight.Bold, Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("T${s.optInt("number")}:E${e.optInt("number")}" + e.optString("title").let { if (it.isNotBlank()) " · $it" else "" }, style = ts(12, color = C.muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        AButton("Publicar", {
                            e.put("availability", "disponible")
                            scope.launch {
                                runCatching { AdminData.saveTitle(JSONObject(t.toString())) }
                                    .onSuccess { AdminToast.show("Capítulo publicado") }
                                    .onFailure { e.put("availability", "proximamente"); AdminToast.show(it.message ?: "No se pudo publicar", true) }
                            }
                        }, primary = true)
                    }
                }
            }
        }
        val recent = ts0.sortedByDescending { it.optLong("updatedAt", it.optLong("createdAt")) }.take(14)
        if (recent.isNotEmpty()) item {
            Column(Modifier.padding(top = 18.dp)) {
                Text("Editados hace poco", style = ts(16, FontWeight.Bold, Color.White), modifier = Modifier.padding(start = 18.dp, bottom = 10.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(recent, key = { it.optString("id") }) { t -> APoster(t, Modifier.width(104.dp)) { nav.go(Screen.Admin("titulo", t.optString("id"))) } }
                }
            }
        }
    }
}

@Composable
private fun Stat(n: String, label: String) {
    Column(Modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = .06f)).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(n, style = ts(24, FontWeight.Bold, Color.White))
        Text(label, style = ts(12, color = C.muted))
    }
}

@Composable
private fun BigAction(text: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(C.accent, Color(0xFFE07BA8)))).tap(onClick, .97f).padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Ic.plus, null, tint = Color.Black, modifier = Modifier.size(18.dp))
        Text(text, style = ts(14.5f, FontWeight.Bold, Color.Black), modifier = Modifier.padding(start = 8.dp), maxLines = 1)
        Spacer(Modifier.weight(1f))
        Icon(icon, null, tint = Color.Black.copy(alpha = .6f), modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SectionTile(title: String, sub: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.height(112.dp).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = .06f))
            .border(1.dp, Color.White.copy(alpha = .07f), RoundedCornerShape(18.dp)).tap(onClick, .97f).padding(14.dp)
    ) {
        Box(Modifier.size(36.dp).background(C.accent.copy(alpha = .16f), CircleShape), contentAlignment = Alignment.Center) { Icon(icon, null, tint = C.accent, modifier = Modifier.size(19.dp)) }
        Spacer(Modifier.weight(1f))
        Text(title, style = ts(15, FontWeight.Bold, Color.White), maxLines = 1)
        Text(sub, style = ts(11.5f, color = C.muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun APoster(t: JSONObject, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.tap(onClick, .97f)) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp)).background(C.surface)) {
            if (t.optString("poster").isNotBlank()) Img(t.optString("poster"), Modifier.fillMaxSize())
            else Box(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) { Text(t.optString("title").ifBlank { "Sin título" }, style = ts(12, FontWeight.Bold, Color.White).copy(textAlign = TextAlign.Center)) }
            Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (t.optString("status") == "borrador") ABadge("Borrador", Color(0xFFFFD36B))
                if (t.optString("availability") == "proximamente") ABadge("Próximamente", C.accent)
                if (t.optBoolean("featured")) ABadge("Destacado", Color.White)
            }
        }
        Text(t.optString("title").ifBlank { "Sin título" }, style = ts(12.5f, FontWeight.Bold, Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
    }
}

/* ---------------- catálogo ---------------- */

@Composable
private fun AdminCatalog(nav: Nav) {
    AdminData.ver
    var q by remember { mutableStateOf(nav.memory.text["adm-q"] ?: "") }
    var f by remember { mutableStateOf(nav.memory.text["adm-f"] ?: "todo") }
    val norm = { s: String -> java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase() }
    val list = AdminData.titles.filter { t ->
        (q.isBlank() || norm(t.optString("title")).contains(norm(q.trim()))) && when (f) {
            "serie" -> t.optString("type") == "serie"; "pelicula" -> t.optString("type") != "serie"
            "borrador" -> t.optString("status") == "borrador"; "pronto" -> t.optString("availability") == "proximamente"
            else -> true
        }
    }
    LazyVerticalGrid(GridCells.Adaptive(104.dp), state = nav.memory.grid("adm-cat"), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottomPad + 30.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                AdminTop("Catálogo", "${list.size} de ${AdminData.titles.size} títulos", nav, onBack = { nav.back() }) {
                    AButton("Nuevo", { nav.go(Screen.Admin("titulo", "new-serie")) }, primary = true, icon = Ic.plus)
                }
                AField("Buscar", q, { q = it; nav.memory.text["adm-q"] = it }, "Nombre del título…")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                    items(listOf("todo" to "Todo", "serie" to "Series", "pelicula" to "Películas", "borrador" to "Borradores", "pronto" to "Próximamente")) { (k, l) ->
                        Chip(l, f == k) { f = k; nav.memory.text["adm-f"] = k }
                    }
                }
            }
        }
        items(list, key = { it.optString("id") }) { t -> APoster(t, Modifier) { nav.go(Screen.Admin("titulo", t.optString("id"))) } }
        if (list.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            Text(if (AdminData.titles.isEmpty()) "El catálogo está vacío. Tocá “Nuevo”." else "Ningún título coincide.", style = ts(14, color = C.muted), modifier = Modifier.padding(24.dp))
        }
    }
}

/* ---------------- editor de título ---------------- */

@Composable
private fun AdminTitle(id: String, nav: Nav) {
    val doc = remember(id) {
        Doc(when (id) {
            "new-serie" -> AdminData.blankTitle("serie")
            "new-pelicula" -> AdminData.blankTitle("pelicula")
            else -> AdminData.titleById(id)?.let { JSONObject(it.toString()) } ?: AdminData.blankTitle("serie")
        })
    }
    val o = doc.o
    var tab by remember { mutableStateOf("info") }
    var saving by remember { mutableStateOf(false) }
    var askLeave by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val isNew = doc.bool("_new")
    val serie = doc.str("type") == "serie"
    BackHandler(enabled = doc.dirty) { askLeave = true }

    fun save() {
        if (doc.str("title").isBlank()) { AdminToast.show("Poné un título antes de guardar", true); tab = "info"; return }
        saving = true
        scope.launch {
            try {
                // limpia el reparto vacío y numera bien temporadas y capítulos (igual que la web)
                val cast = o.optJSONArray("cast") ?: JSONArray()
                o.put("cast", JSONArray((0 until cast.length()).map { cast.getJSONObject(it) }.filter { it.optString("name").isNotBlank() }))
                val wasNew = isNew
                AdminData.saveTitle(o)
                doc.dirty = false; doc.v++
                AdminToast.show(if (wasNew) "Título creado" else "Cambios guardados")
                if (wasNew) nav.replace(Screen.Admin("titulo", o.getString("id")))
            } catch (e: Exception) { AdminToast.show(e.message ?: "No se pudo guardar", true) }
            saving = false
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(bottom = bottomPad + 110.dp)) {
            item {
                AdminTop(doc.str("title").ifBlank { if (isNew) (if (serie) "Nueva serie" else "Nueva película") else "Sin título" },
                    listOf(if (serie) "Serie" else "Película", if (doc.str("status") == "borrador") "Borrador" else "Publicado").joinToString(" · "), nav,
                    onBack = { if (doc.dirty) askLeave = true else nav.back() })
            }
            item {
                val tabs = listOfNotNull("info" to "Datos", "img" to "Imágenes", "video" to "Video", if (serie) "temps" to "Capítulos" else null, "cast" to "Reparto")
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                    items(tabs) { (k, l) -> Chip(l, tab == k) { tab = k } }
                }
            }
            when (tab) {
                "info" -> item { TitleInfo(doc, isNew) }
                "img" -> item { TitleImages(doc) }
                "video" -> item { TitleVideo(doc, serie) }
                "temps" -> item { TitleSeasons(doc) }
                "cast" -> item { TitleCast(doc) }
            }
            if (!isNew) item {
                Box(Modifier.fillMaxWidth().padding(top = 18.dp), contentAlignment = Alignment.Center) { AButton("Eliminar título", { askDelete = true }, danger = true) }
            }
        }
        // barra de guardar, siempre a mano
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .92f), Color.Black)))
                .navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(if (doc.dirty) "Cambios sin guardar" else if (isNew) "Completá los datos" else "Todo guardado", style = ts(13, FontWeight.Bold, if (doc.dirty) C.accent else C.muted), modifier = Modifier.weight(1f))
            PillButton(if (saving) "Guardando…" else "Guardar", { if (!saving) save() }, Modifier.width(150.dp), icon = null, dark = !doc.dirty && !isNew)
        }
        if (askLeave) AConfirm("¿Salir sin guardar?", "Los cambios que hiciste se pierden.", "Salir", { askLeave = false; doc.dirty = false; nav.back() }, { askLeave = false })
        if (askDelete) AConfirm("¿Eliminar “${doc.str("title")}”?", "Se borra de la web, la TV y el celular. No se puede deshacer.", "Eliminar", {
            askDelete = false
            scope.launch { runCatching { AdminData.deleteTitle(o.getString("id")) }.onSuccess { AdminToast.show("Título eliminado"); nav.back() }.onFailure { AdminToast.show(it.message ?: "No se pudo eliminar", true) } }
        }, { askDelete = false })
    }
}

@Composable
private fun TitleInfo(doc: Doc, isNew: Boolean) {
    val o = doc.o
    ACard {
        AToggle("Publicado", if (doc.str("status") == "borrador") "Ahora es un borrador: solo lo ves vos" else "Visible para todos", doc.str("status") != "borrador") { doc.set("status", if (it) "publicado" else "borrador") }
        AToggle("Destacado", "Aparece en la portada si no hay banners", doc.bool("featured")) { doc.set("featured", it) }
        AToggle("Próximamente", "Se ve en el catálogo pero todavía no se puede reproducir", doc.str("availability") == "proximamente") { doc.set("availability", if (it) "proximamente" else "disponible") }
        if (doc.str("availability") == "proximamente") AField("Fecha de estreno", doc.str("releaseDate"), { doc.set("releaseDate", it) }, "Ej: 15 de diciembre")
    }
    ACard("Datos") {
        if (isNew) {
            Text("TIPO", style = ts(10.5f, FontWeight.Bold, C.muted), modifier = Modifier.padding(bottom = 6.dp))
            ASegment(listOf("serie" to "Serie", "pelicula" to "Película"), doc.str("type")) { t ->
                doc.set("type", t)
                if (t == "serie" && (o.optJSONArray("seasons")?.length() ?: 0) == 0) o.put("seasons", JSONArray().put(JSONObject().put("id", AdminData.uid("s")).put("number", 1).put("title", "").put("episodes", JSONArray())))
            }
            Spacer(Modifier.height(12.dp))
        }
        AField("Título", doc.str("title"), { doc.set("title", it) }, "Nombre del título")
        AField("Frase corta", doc.str("tagline"), { doc.set("tagline", it) }, "Una línea que lo resuma")
        AField("Sinopsis", doc.str("description"), { doc.set("description", it) }, "De qué se trata…", lines = 5)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AField("Año", doc.str("year"), { doc.set("year", it) }, "2024", number = true, modifier = Modifier.weight(1f))
            if (doc.str("type") == "serie") AField("Hasta", doc.str("yearEnd"), { doc.set("yearEnd", it) }, "En emisión", number = true, modifier = Modifier.weight(1f))
            else AField("Duración", doc.str("duration"), { doc.set("duration", it) }, "1 h 35 min", modifier = Modifier.weight(1f))
        }
        AField("Dirección", doc.str("director"), { doc.set("director", it) }, "Opcional")
        Text("CLASIFICACIÓN", style = ts(10.5f, FontWeight.Bold, C.muted), modifier = Modifier.padding(bottom = 6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(AdminData.RATINGS) { r -> Chip(r, doc.str("rating") == r) { doc.set("rating", r) } } }
    }
    ACard("Géneros", "Cada género con títulos publicados es una pestaña del inicio y una fila de la portada.") {
        val g = o.optJSONArray("genres") ?: JSONArray().also { o.put("genres", it) }
        doc.v
        val cur = (0 until g.length()).map { g.getString(it) }
        val known = (AdminData.GENRES + AdminData.titles.flatMap { t -> t.optJSONArray("genres")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList() }).distinct()
        FlowChips(known.sortedBy { it !in cur }) { name ->
            Chip(name, name in cur) {
                val l = cur.toMutableList(); if (name in l) l.remove(name) else l.add(name)
                o.put("genres", JSONArray(l)); doc.touch()
            }
        }
        var nw by remember { mutableStateOf("") }
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.Bottom) {
            AField("Agregar otro", nw, { nw = it }, "Nuevo género", modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Box(Modifier.padding(bottom = 10.dp)) { AButton("Agregar", { val n = nw.trim(); if (n.isNotBlank() && n !in cur) { o.put("genres", JSONArray(cur + n)); doc.touch() }; nw = "" }) }
        }
    }
    ACard("Formatos", "Insignias que se muestran en la ficha.") {
        val fm = o.optJSONObject("formats") ?: JSONObject().also { o.put("formats", it) }
        listOf("uhd4k" to "4K", "hdr" to "HDR / Dolby Vision", "atmos" to "Dolby Atmos", "surround51" to "5.1", "hd" to "HD").forEach { (k, l) ->
            AToggle(l, null, doc.bool(k, fm)) { doc.set(k, it, fm) }
        }
    }
}

/** Chips que saltan de renglón. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun FlowChips(items: List<String>, chip: @Composable (String) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { chip(it) }
    }
}

@Composable
private fun TitleImages(doc: Doc) {
    val o = doc.o
    ACard("Así se ve en la app", "Vista previa con las imágenes cargadas: la portada del inicio y la ficha del título.") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                PhoneHero(doc.str("bannerImageMobile").ifBlank { doc.str("bannerImage").ifBlank { doc.str("backdrop").ifBlank { doc.str("poster") } } }, doc.str("logo"), doc.str("title"),
                    listOf(doc.str("rating"), if (doc.str("type") == "serie") "${o.optJSONArray("seasons")?.length() ?: 0} temporadas" else doc.str("duration")).filter { it.isNotBlank() }.joinToString("   "),
                    doc.str("tagline").ifBlank { doc.str("description") })
                Text("Inicio", style = ts(11.5f, FontWeight.Bold, C.muted), modifier = Modifier.padding(top = 6.dp))
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                PhoneDetail(doc.str("backdropMobile").ifBlank { doc.str("bannerImageMobile").ifBlank { doc.str("backdrop").ifBlank { doc.str("bannerImage").ifBlank { doc.str("poster") } } } }, doc.str("logo"), doc.str("title"))
                Text("Ficha", style = ts(11.5f, FontWeight.Bold, C.muted), modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
    ACard("Imágenes principales") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AImage("Póster", "poster", doc.str("poster"), "2:3 · 1200 × 1800", modifier = Modifier.weight(1f)) { doc.set("poster", it) }
            AImage("Logo", "logo", doc.str("logo"), "PNG transparente", ratio = 2f / 3f, contain = true, modifier = Modifier.weight(1f)) { doc.set("logo", it) }
        }
        AImage("Fondo de la ficha", "backdrop", doc.str("backdrop"), "16:9 · computadora y TV si no hay otra") { doc.set("backdrop", it) }
        AImage("Miniatura ancha", "thumb", doc.str("thumbWide"), "16:9 · tarjetas de \"Seguir viendo\" y búsquedas") { doc.set("thumbWide", it) }
        AImage("Fondo del reproductor (opcional)", "backdrop", doc.str("playerBg"), "16:9 · solo en el reproductor de la web (carga y pausa). Vacío = fondo de la ficha", fallback = doc.str("backdrop")) { doc.set("playerBg", it) }
    }
    ACard("Celular", "Las que usa la app del celular y la web en pantallas chicas.") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AImage("Portada del inicio", "mobilebanner", doc.str("bannerImageMobile"), "4:5 · deja libre la parte de abajo", modifier = Modifier.weight(1f),
                fallback = doc.str("bannerImage").ifBlank { doc.str("backdrop") }) { doc.set("bannerImageMobile", it) }
            AImage("Fondo de la ficha", "mobile", doc.str("backdropMobile"), "16:10 · 1600 × 1000", modifier = Modifier.weight(1f), fallback = doc.str("backdrop")) { doc.set("backdropMobile", it) }
        }
    }
    ACard("Computadora y TV") {
        AImage("Banner de la portada (web)", "banner", doc.str("bannerImage"), "3:1 · 3000 × 1000") { doc.set("bannerImage", it) }
        AImage("Banner de la TV", "tv", doc.str("bannerImageTv"), "16:9 · 1920 × 1080") { doc.set("bannerImageTv", it) }
        AImage("Fondo de la ficha en la TV", "tv", doc.str("backdropTv"), "16:9 · 1920 × 1080") { doc.set("backdropTv", it) }
    }
}

/** Mini celular con la portada del inicio tal como se ve en la app. */
@Composable
fun PhoneHero(img: String, logo: String, title: String, meta: String, text: String, headline: String = "") {
    PhoneFrame {
        Box(Modifier.fillMaxWidth().fillMaxHeight(.84f)) {
            if (img.isNotBlank()) Img(img, Modifier.fillMaxSize(), alignment = Alignment.TopCenter) else Box(Modifier.fillMaxSize().background(C.surface))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = .7f), .18f to Color.Transparent, .5f to Color.Transparent, .78f to Color.Black.copy(alpha = .6f), 1f to Color.Black)))
            Column(Modifier.align(Alignment.BottomCenter).padding(horizontal = 8.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                when {
                    headline.isNotBlank() -> Text(headline, style = ts(13, FontWeight.Bold, Color.White).copy(textAlign = TextAlign.Center), maxLines = 2)
                    logo.isNotBlank() -> Img(logo, Modifier.fillMaxWidth(.78f).height(34.dp), scale = ContentScale.Fit)
                    else -> Text(title.ifBlank { "Título" }, style = ts(13, FontWeight.Bold, Color.White).copy(textAlign = TextAlign.Center), maxLines = 2)
                }
                if (meta.isNotBlank()) Text(meta, style = ts(6.5f, color = Color.White.copy(alpha = .8f)), modifier = Modifier.padding(top = 5.dp))
                if (text.isNotBlank()) Text(text, style = ts(7, color = Color.White.copy(alpha = .9f)).copy(textAlign = TextAlign.Center, lineHeight = 9.sp), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
            }
            Box(Modifier.fillMaxWidth().padding(top = 10.dp, start = 8.dp, end = 8.dp)) {
                LocalLogo(Modifier.height(9.dp).width(50.dp))
            }
        }
        Row(Modifier.padding(start = 8.dp, top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) { repeat(3) { Box(Modifier.width(26.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = .12f))) } }
    }
}

/** Mini celular con la ficha (fondo difuminado detrás del logo). */
@Composable
fun PhoneDetail(img: String, logo: String, title: String) {
    PhoneFrame {
        Box(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().aspectRatio(1f / 1.12f)) {
                if (img.isNotBlank()) Img(img, Modifier.fillMaxSize(), alignment = Alignment.TopCenter) else Box(Modifier.fillMaxSize().background(C.surface))
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = .38f), .22f to Color.Transparent, .5f to Color.Transparent, .8f to Color(0xFF241A20).copy(alpha = .8f), 1f to Color(0xFF241A20))))
            }
            Column(Modifier.fillMaxWidth().padding(top = 104.dp, start = 8.dp, end = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (logo.isNotBlank()) Img(logo, Modifier.fillMaxWidth(.8f).height(30.dp), scale = ContentScale.Fit)
                else Text(title.ifBlank { "Título" }, style = ts(12, FontWeight.Bold, Color.White).copy(textAlign = TextAlign.Center), maxLines = 2)
                Box(Modifier.padding(top = 10.dp).fillMaxWidth().height(14.dp).background(Color.White, RoundedCornerShape(99.dp)))
                repeat(3) { Box(Modifier.padding(top = 5.dp).fillMaxWidth(if (it == 2) .6f else 1f).height(3.dp).background(Color.White.copy(alpha = .3f), RoundedCornerShape(9.dp))) }
            }
        }
    }
}

@Composable
private fun PhoneFrame(content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxWidth().widthIn(max = 220.dp).aspectRatio(9f / 18.5f).clip(RoundedCornerShape(20.dp)).background(Color(0xFF241A20))
            .border(3.dp, Color(0xFF3A3238), RoundedCornerShape(20.dp)).padding(3.dp).clip(RoundedCornerShape(17.dp)).background(Color.Black)
    ) { Column { content() } }
}


@Composable
private fun TitleVideo(doc: Doc, serie: Boolean) {
    if (!serie) ACard("Película", "Se reproduce dentro de la app. Funciona con .mp4, .mkv, .ts, .m3u8, YouTube, Vimeo, Drive y más.") {
        AField("Enlace del video", doc.str("url"), { doc.set("url", cleanLink(it)) }, "https://…")
        AField("Enlace para la TV (4K, opcional)", doc.str("urlTv"), { doc.set("urlTv", cleanLink(it)) }, "Solo la app de la TV")
        AField("Subtítulos en español (.vtt)", doc.str("subs"), { doc.set("subs", it) }, "https://…/pelicula.es.vtt")
        SkipsEditor(doc, doc.o)
    }
    ACard("Tráiler") { AField("Enlace del tráiler", doc.str("trailer"), { doc.set("trailer", cleanLink(it)) }, "YouTube o archivo de video") }
    ACard("Video del banner", "Video corto sin sonido que arranca en el banner de la web (no YouTube).") {
        AField("Computadora (16:9)", doc.str("previewVideo"), { doc.set("previewVideo", it) }, "https://…/video.mp4")
        AField("Celular (4:5, opcional)", doc.str("previewVideoMobile"), { doc.set("previewVideoMobile", it) }, "https://…/video-celular.mp4")
    }
}

/** Si se pega el código de inserción completo (<iframe …>), se queda solo con el enlace (igual que la web). */
private fun cleanLink(v: String): String {
    val m = Regex("<iframe[^>]*\\ssrc\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(v)
    var u = m?.groupValues?.get(1) ?: return v
    if (u.startsWith("//")) u = "https:$u"
    return u.replace("&amp;", "&")
}

/** "Saltear intro / resumen" de una película, capítulo o extra. */
@Composable
private fun SkipsEditor(doc: Doc, target: JSONObject) {
    doc.v
    val ks = target.optJSONArray("skips") ?: JSONArray()
    Text("SALTEAR INTRO / RESUMEN", style = ts(10.5f, FontWeight.Bold, C.muted), modifier = Modifier.padding(top = 6.dp, bottom = 6.dp))
    for (i in 0 until ks.length()) {
        val k = ks.getJSONObject(i)
        Column(Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = .25f)).padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf("Saltear intro", "Saltear resumen", "Saltear créditos")) { l -> Chip(l, doc.str("label", k) == l) { doc.set("label", l, k) } }
                }
                Box(Modifier.size(34.dp).tap({ val n = JSONArray(); for (j in 0 until ks.length()) if (j != i) n.put(ks.get(j)); target.put("skips", n); doc.touch() }), contentAlignment = Alignment.Center) {
                    Icon(Ic.close, "Quitar", tint = Color(0xFFFF9AA8), modifier = Modifier.size(18.dp))
                }
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AField("Aparece en", doc.str("from", k), { doc.set("from", it.trim(), k) }, "0:00", modifier = Modifier.weight(1f))
                AField("Salta a", doc.str("to", k), { doc.set("to", it.trim(), k) }, "1:30", modifier = Modifier.weight(1f))
            }
            val f = com.realityvault.tv.parseSkipTime(doc.str("from", k)); val t = com.realityvault.tv.parseSkipTime(doc.str("to", k))
            val err = when {
                doc.str("from", k).isNotBlank() && f == null -> "El minuto de inicio no es válido (usá 1:30)."
                doc.str("to", k).isBlank() -> "Falta el minuto al que salta."
                t == null -> "El minuto al que salta no es válido (usá 1:30)."
                t <= (f ?: 0) -> "El minuto al que salta tiene que ser después del inicio."
                else -> null
            }
            if (err != null) Text(err, style = ts(12, color = Color(0xFFFF8A8A)))
        }
    }
    AButton("+ Agregar salto", {
        val n = target.optJSONArray("skips") ?: JSONArray().also { target.put("skips", it) }
        n.put(JSONObject().put("label", if (n.length() == 0) "Saltear intro" else "Saltear créditos").put("from", "").put("to", "")); doc.touch()
    })
}

@Composable
private fun TitleSeasons(doc: Doc) {
    val o = doc.o
    doc.v
    val ss = o.optJSONArray("seasons") ?: JSONArray().also { o.put("seasons", it) }
    var si by remember { mutableIntStateOf(0) }
    if (si >= ss.length()) si = (ss.length() - 1).coerceAtLeast(0)
    var askDel by remember { mutableStateOf(false) }
    Column {
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
            items((0 until ss.length()).toList()) { i -> val s = ss.getJSONObject(i); Chip("T${s.optString("number")}" + s.optString("title").let { if (it.isNotBlank()) " · $it" else "" }, i == si) { si = i } }
            item {
                Chip("+ Temporada", false) {
                    val n = ((0 until ss.length()).maxOfOrNull { ss.getJSONObject(it).optInt("number") } ?: 0) + 1
                    ss.put(JSONObject().put("id", AdminData.uid("s")).put("number", n).put("title", "").put("episodes", JSONArray())); si = ss.length() - 1; doc.touch()
                }
            }
        }
        if (ss.length() == 0) { Text("Todavía no hay temporadas.", style = ts(14, color = C.muted), modifier = Modifier.padding(20.dp)); return@Column }
        val s = ss.getJSONObject(si)
        ACard("Temporada ${doc.str("number", s)}") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AField("Número", doc.str("number", s), { v -> doc.set("number", v.filter { it.isDigit() }.toIntOrNull() ?: 1, s) }, number = true, modifier = Modifier.width(90.dp))
                AField("Nombre (opcional)", doc.str("title", s), { doc.set("title", it, s) }, "Ej: Road Trip", modifier = Modifier.weight(1f))
            }
            AToggle("Temporada próximamente", null, doc.str("availability", s) == "proximamente") { doc.set("availability", if (it) "proximamente" else "disponible", s) }
            Box(Modifier.fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.CenterEnd) { AButton("Quitar temporada", { askDel = true }, danger = true) }
        }
        val es = s.optJSONArray("episodes") ?: JSONArray().also { s.put("episodes", it) }
        Text("Capítulos · ${es.length()}", style = ts(16, FontWeight.Bold, Color.White), modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp))
        for (j in 0 until es.length()) EpisodeEditor(doc, es, j, extra = false)
        Box(Modifier.fillMaxWidth().padding(16.dp)) {
            AButton("+ Capítulo", {
                val n = ((0 until es.length()).maxOfOrNull { es.getJSONObject(it).optInt("number") } ?: 0) + 1
                es.put(JSONObject().put("id", AdminData.uid("e")).put("number", n).put("title", "").put("description", "").put("duration", "").put("thumb", "").put("url", "")); doc.touch()
            }, modifier = Modifier.fillMaxWidth())
        }
        val xs = s.optJSONArray("extras") ?: JSONArray()
        Text("Extras · ${xs.length()}", style = ts(16, FontWeight.Bold, Color.White), modifier = Modifier.padding(start = 20.dp, top = 6.dp))
        Text("Detrás de cámaras, escenas eliminadas, entrevistas… No cuentan como capítulos. El primero es el destacado.", style = ts(12, color = C.muted), modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp))
        for (j in 0 until xs.length()) EpisodeEditor(doc, xs, j, extra = true)
        Box(Modifier.fillMaxWidth().padding(16.dp)) {
            AButton("+ Extra", {
                val a = s.optJSONArray("extras") ?: JSONArray().also { s.put("extras", it) }
                a.put(JSONObject().put("id", AdminData.uid("x")).put("title", "").put("description", "").put("duration", "").put("thumb", "").put("url", "")); doc.touch()
            }, modifier = Modifier.fillMaxWidth())
        }
        if (askDel) AConfirm("¿Quitar la temporada ${doc.str("number", s)}?", "Se quitan también sus capítulos. Se borra recién al guardar.", "Quitar", {
            val n = JSONArray(); for (k in 0 until ss.length()) if (k != si) n.put(ss.get(k)); o.put("seasons", n); askDel = false; si = 0; doc.touch()
        }, { askDel = false })
    }
}

@Composable
private fun EpisodeEditor(doc: Doc, arr: JSONArray, j: Int, extra: Boolean) {
    val e = arr.getJSONObject(j)
    var open by remember(e) { mutableStateOf(false) }
    val soon = doc.str("availability", e) == "proximamente"
    val hasUrl = com.realityvault.tv.isHttp(doc.str("url", e))
    Column(Modifier.padding(horizontal = 16.dp, vertical = 5.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = .055f)).animateContentSize()) {
        Row(Modifier.fillMaxWidth().tap({ open = !open }, .99f).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(92.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(C.surface)) {
                if (doc.str("thumb", e).isNotBlank()) Img(doc.str("thumb", e), Modifier.fillMaxSize())
            }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text((if (extra) "" else "${doc.str("number", e)}. ") + doc.str("title", e).ifBlank { if (extra) "Extra sin título" else "Capítulo sin título" },
                    style = ts(14, FontWeight.Bold, Color.White), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    when {
                        soon -> ABadge("Próximamente", C.accent)
                        hasUrl -> ABadge("Disponible", Color(0xFF7BE0A4))
                        else -> ABadge("Sin video", Color(0xFFFFD36B))
                    }
                    if (doc.str("duration", e).isNotBlank()) Text(doc.str("duration", e), style = ts(11.5f, color = C.muted))
                }
            }
            Icon(Ic.chevron, null, tint = Color.White.copy(alpha = .6f), modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = if (open) 90f else 0f })
        }
        if (open) Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
            if (!extra) AToggle("Próximamente", "Se ve en la lista pero no se puede reproducir", soon) { doc.set("availability", if (it) "proximamente" else "disponible", e) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!extra) AField("Nº", doc.str("number", e), { v -> doc.set("number", v.filter { it.isDigit() }.toIntOrNull() ?: 1, e) }, number = true, modifier = Modifier.width(72.dp))
                AField("Título", doc.str("title", e), { doc.set("title", it, e) }, modifier = Modifier.weight(1f))
            }
            AField("Duración", doc.str("duration", e), { doc.set("duration", it, e) }, "45 min")
            AField(if (extra) "Descripción" else "Sinopsis", doc.str("description", e), { doc.set("description", it, e) }, lines = 3)
            AImage("Miniatura", "thumb", doc.str("thumb", e), "16:9 · 1280 × 720") { doc.set("thumb", it, e) }
            AField("Enlace del video", doc.str("url", e), { doc.set("url", cleanLink(it), e) }, "https://…")
            AField("Enlace para la TV (4K, opcional)", doc.str("urlTv", e), { doc.set("urlTv", cleanLink(it), e) }, "Vacío = usa el de arriba")
            AField("Subtítulos en español (.vtt)", doc.str("subs", e), { doc.set("subs", it, e) }, "https://…/capitulo.es.vtt")
            SkipsEditor(doc, e)
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (j > 0) AButton("Subir", { val a = arr.get(j - 1); arr.put(j - 1, arr.get(j)); arr.put(j, a); doc.touch() })
                if (j < arr.length() - 1) AButton("Bajar", { val a = arr.get(j + 1); arr.put(j + 1, arr.get(j)); arr.put(j, a); doc.touch() })
                Spacer(Modifier.weight(1f))
                AButton("Quitar", { arr.remove(j); doc.touch() }, danger = true)
            }
        }
    }
}


@Composable
private fun TitleCast(doc: Doc) {
    val o = doc.o
    doc.v
    val cast = o.optJSONArray("cast") ?: JSONArray().also { o.put("cast", it) }
    ACard("Reparto", "Tocá una persona en la ficha para ver todos sus títulos.") {
        for (i in 0 until cast.length()) {
            val c = cast.getJSONObject(i)
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.width(78.dp)) { AImage("Foto", "face", doc.str("photo", c), null) { doc.set("photo", it, c) } }
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    AField("Nombre", doc.str("name", c), { doc.set("name", it, c) })
                    AField("Rol", doc.str("role", c), { doc.set("role", it, c) }, "Participante, conductora…")
                }
                Box(Modifier.padding(top = 20.dp).size(34.dp).tap({ cast.remove(i); doc.touch() }), contentAlignment = Alignment.Center) { Icon(Ic.close, "Quitar", tint = Color(0xFFFF9AA8), modifier = Modifier.size(18.dp)) }
            }
        }
        AButton("+ Agregar persona", { cast.put(JSONObject().put("name", "").put("role", "").put("photo", "")); doc.touch() }, modifier = Modifier.fillMaxWidth())
    }
}

/* ---------------- banners ---------------- */

private fun bannerImg(b: JSONObject): String {
    val t = AdminData.titleById(b.optString("titleId"))
    return b.optString("imageMobile").ifBlank { t?.optString("bannerImageMobile")?.ifBlank { null } ?: b.optString("image").ifBlank { t?.optString("bannerImage")?.ifBlank { null } ?: t?.optString("backdrop") ?: "" } }
}

@Composable
private fun AdminBanners(nav: Nav) {
    AdminData.ver
    val scope = rememberCoroutineScope()
    val bs = AdminData.banners
    LazyColumn(contentPadding = PaddingValues(bottom = bottomPad + 30.dp)) {
        item {
            AdminTop("Banners", "${bs.count { it.optBoolean("active", true) }} activos de ${bs.size}", nav) {
                AButton("Nuevo", {
                    val id = AdminData.uid("b")
                    val b = JSONObject().put("id", id).put("titleId", AdminData.titles.firstOrNull { it.optString("status") != "borrador" }?.optString("id") ?: "")
                        .put("image", "").put("eyebrow", "").put("headline", "").put("text", "").put("cta", "").put("link", "").put("active", true)
                        .put("order", (bs.maxOfOrNull { it.optInt("order") } ?: 0) + 1).put("createdAt", System.currentTimeMillis())
                    scope.launch { runCatching { AdminData.saveBanner(b) }.onSuccess { nav.go(Screen.Admin("banner", id)) }.onFailure { AdminToast.show("No se pudo crear el banner", true) } }
                }, primary = true, icon = Ic.plus)
            }
            Text("Los activos rotan en la portada en orden aleatorio; los fijados salen primero. Así se ven en el celular:", style = ts(13, color = C.muted), modifier = Modifier.padding(horizontal = 18.dp).padding(bottom = 8.dp))
        }
        itemsIndexed(bs, key = { _, b -> b.optString("id") }) { i, b ->
            val t = AdminData.titleById(b.optString("titleId"))
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = .055f))
                    .tap({ nav.go(Screen.Admin("banner", b.optString("id"))) }, .99f).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.width(112.dp).graphicsLayer { alpha = if (b.optBoolean("active", true)) 1f else .4f }) {
                    PhoneHero(bannerImg(b), t?.optString("logo") ?: "", t?.optString("title") ?: "", "", b.optString("text").ifBlank { t?.optString("tagline") ?: "" }, b.optString("headline"))
                }
                Column(Modifier.padding(start = 14.dp).weight(1f)) {
                    Text("#${i + 1}", style = ts(12, FontWeight.Bold, C.accent))
                    Text(b.optString("headline").ifBlank { t?.optString("title") ?: "Sin título vinculado" }, style = ts(16, FontWeight.Bold, Color.White), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ABadge(if (b.optBoolean("active", true)) "Activo" else "Pausado", if (b.optBoolean("active", true)) Color(0xFF7BE0A4) else Color(0xFF9A9A9A))
                        if (b.optBoolean("pinned")) ABadge("Fijado", Color.White)
                    }
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (i > 0) AButton("↑", { val l = bs.toMutableList(); l.add(i - 1, l.removeAt(i)); scope.launch { runCatching { AdminData.reorderBanners(l) } } })
                        if (i < bs.size - 1) AButton("↓", { val l = bs.toMutableList(); l.add(i + 1, l.removeAt(i)); scope.launch { runCatching { AdminData.reorderBanners(l) } } })
                    }
                }
            }
        }
        if (bs.isEmpty()) item { Text("Todavía no hay banners. Sin banners, la portada muestra los títulos destacados.", style = ts(14, color = C.muted), modifier = Modifier.padding(20.dp)) }
    }
}


@Composable
private fun AdminBanner(id: String, nav: Nav) {
    val src = AdminData.banners.firstOrNull { it.optString("id") == id }
    if (src == null) { Column { AdminTop("Banner", null, nav); Text("Este banner ya no existe.", style = ts(14, color = C.muted), modifier = Modifier.padding(20.dp)) }; return }
    val doc = remember(id) { Doc(JSONObject(src.toString())) }
    val b = doc.o
    var picking by remember { mutableStateOf(false) }
    var askDel by remember { mutableStateOf(false) }
    var askLeave by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    BackHandler(enabled = doc.dirty) { askLeave = true }
    val t = AdminData.titleById(doc.str("titleId"))
    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(bottom = bottomPad + 110.dp)) {
            item { AdminTop("Banner", t?.optString("title") ?: "Sin título vinculado", nav, onBack = { if (doc.dirty) askLeave = true else nav.back() }) }
            item {
                ACard("Así se ve en el celular", "Se actualiza mientras editás.") {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Box(Modifier.width(210.dp)) {
                            PhoneHero(bannerImg(b.also { doc.v }), t?.optString("logo") ?: "", t?.optString("title") ?: "",
                                listOfNotNull(t?.optString("rating")?.ifBlank { null }, t?.let { if (it.optString("type") == "serie") "${it.optJSONArray("seasons")?.length() ?: 0} temporadas" else it.optString("duration").ifBlank { null } }).joinToString("   "),
                                doc.str("text").ifBlank { t?.optString("tagline")?.ifBlank { t.optString("description") } ?: "" }, doc.str("headline"))
                        }
                    }
                }
            }
            item {
                ACard {
                    AToggle("Activo", "Se muestra en la portada", b.optBoolean("active", true).also { doc.v }) { doc.set("active", it) }
                    AToggle("Fijar primero", "Sale siempre al principio, antes de los que rotan", doc.bool("pinned")) { doc.set("pinned", it) }
                }
            }
            item {
                ACard("Título vinculado", "Si un campo queda vacío se usa la información de este título.") {
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = .3f)).tap({ picking = true }, .99f).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(40.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(5.dp)).background(C.surface)) { t?.optString("poster")?.takeIf { it.isNotBlank() }?.let { Img(it, Modifier.fillMaxSize()) } }
                        Text(t?.optString("title") ?: "Elegir un título", style = ts(15, FontWeight.Bold, Color.White), modifier = Modifier.padding(start = 12.dp).weight(1f))
                        Text("Cambiar", style = ts(12.5f, FontWeight.Bold, C.accent))
                    }
                    if (doc.str("titleId").isNotBlank()) Text("Quitar vínculo", style = ts(12.5f, FontWeight.Bold, Color(0xFFFF9AA8)), modifier = Modifier.padding(top = 8.dp).tap({ doc.set("titleId", "") }).padding(4.dp))
                }
            }
            item {
                ACard("Imágenes", "La del celular es vertical: dejá libre la parte de abajo, ahí van el logo y el texto.") {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AImage("Celular", "mobilebanner", doc.str("imageMobile"), "4:5 · tocá Encuadrar para moverla", modifier = Modifier.weight(1f),
                            fallback = t?.optString("bannerImageMobile")?.ifBlank { null } ?: doc.str("image").ifBlank { t?.optString("bannerImage")?.ifBlank { null } ?: t?.optString("backdrop") ?: "" }) { doc.set("imageMobile", it) }
                        Column(Modifier.weight(1f)) {
                            AImage("Computadora", "banner", doc.str("image"), "3:1 · 2400 × 800",
                                fallback = t?.optString("bannerImage")?.ifBlank { null } ?: t?.optString("backdrop") ?: "") { doc.set("image", it) }
                        }
                    }
                }
            }
            item {
                ACard("Textos") {
                    AField("Título grande (opcional)", doc.str("headline"), { doc.set("headline", it) }, t?.optString("title") ?: "En lugar del logo")
                    AField("Texto", doc.str("text"), { doc.set("text", it) }, t?.optString("tagline") ?: "", lines = 3)
                    AField("Antetítulo (web)", doc.str("eyebrow"), { doc.set("eyebrow", it) }, "Estreno · Nueva temporada")
                    AField("Texto del botón (web)", doc.str("cta"), { doc.set("cta", it) }, "Ver ahora")
                    if (doc.str("titleId").isBlank()) AField("Enlace del botón", doc.str("link"), { doc.set("link", it) }, "https://…")
                }
            }
            item { Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) { AButton("Eliminar banner", { askDel = true }, danger = true) } }
        }
        SaveBar(doc.dirty, saving) {
            saving = true
            scope.launch { runCatching { AdminData.saveBanner(b) }.onSuccess { doc.dirty = false; AdminToast.show("Banner guardado") }.onFailure { AdminToast.show(it.message ?: "No se pudo guardar", true) }; saving = false }
        }
        if (picking) TitlePickerSheet({ doc.set("titleId", it); picking = false }, { picking = false })
        if (askLeave) AConfirm("¿Salir sin guardar?", null, "Salir", { askLeave = false; doc.dirty = false; nav.back() }, { askLeave = false })
        if (askDel) AConfirm("¿Eliminar este banner?", null, "Eliminar", {
            askDel = false; scope.launch { runCatching { AdminData.deleteBanner(id) }.onSuccess { AdminToast.show("Banner eliminado"); nav.back() }.onFailure { AdminToast.show("No se pudo eliminar", true) } }
        }, { askDel = false })
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.SaveBar(dirty: Boolean, saving: Boolean, onSave: () -> Unit) {
    Row(
        Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .92f), Color.Black)))
            .navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(if (dirty) "Cambios sin guardar" else "Todo guardado", style = ts(13, FontWeight.Bold, if (dirty) C.accent else C.muted), modifier = Modifier.weight(1f))
        PillButton(if (saving) "Guardando…" else "Guardar", { if (!saving && dirty) onSave() }, Modifier.width(150.dp), icon = null, dark = !dirty)
    }
}

/* ---------------- colecciones ---------------- */

private fun JSONArray.objs(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
private fun JSONArray.strs(): List<String> = (0 until length()).map { getString(it) }

@Composable
private fun AdminColls(nav: Nav) {
    AdminData.ver
    val scope = rememberCoroutineScope()
    val list = AdminData.colls.objs()
    fun saveOrder(l: List<JSONObject>) { scope.launch { runCatching { AdminData.saveColls(JSONArray(l)) }.onFailure { AdminToast.show("No se pudo guardar", true) } } }
    LazyColumn(contentPadding = PaddingValues(bottom = bottomPad + 30.dp)) {
        item {
            AdminTop("Colecciones", "${list.size} colecciones", nav) {
                AButton("Nueva", {
                    val id = AdminData.uid("c")
                    val a = JSONArray(AdminData.colls.toString()).put(JSONObject().put("id", id).put("name", "Nueva colección").put("tagline", "").put("image", "").put("items", JSONArray()))
                    scope.launch { runCatching { AdminData.saveColls(a) }.onSuccess { nav.go(Screen.Admin("coleccion", id)) }.onFailure { AdminToast.show("No se pudo crear", true) } }
                }, primary = true, icon = Ic.plus)
            }
            Text("Grupos de títulos que aparecen en el inicio como un banner, repartidos entre las filas, y tienen su propia página.", style = ts(13, color = C.muted), modifier = Modifier.padding(horizontal = 18.dp).padding(bottom = 8.dp))
        }
        itemsIndexed(list, key = { _, c -> c.optString("id") }) { i, c ->
            val items = c.optJSONArray("items")?.strs()?.mapNotNull { AdminData.titleById(it) } ?: emptyList()
            Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = .055f)).tap({ nav.go(Screen.Admin("coleccion", c.optString("id"))) }, .99f)) {
                CollPreview(c.optString("name"), c.optString("tagline"), c.optString("image"), items)
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${items.size} título${if (items.size == 1) "" else "s"}", style = ts(12.5f, color = C.muted), modifier = Modifier.weight(1f))
                    if (i > 0) AButton("↑", { val l = list.toMutableList(); l.add(i - 1, l.removeAt(i)); saveOrder(l) })
                    Spacer(Modifier.width(6.dp))
                    if (i < list.size - 1) AButton("↓", { val l = list.toMutableList(); l.add(i + 1, l.removeAt(i)); saveOrder(l) })
                }
            }
        }
        if (list.isEmpty()) item { Text("Todavía no hay colecciones.", style = ts(14, color = C.muted), modifier = Modifier.padding(20.dp)) }
    }
}

/** Vista previa del banner de colección tal como aparece en el inicio. */
@Composable
private fun CollPreview(name: String, tagline: String, image: String, items: List<JSONObject>) {
    val bg = Color(0xFF15100F)
    Box(Modifier.fillMaxWidth().height(130.dp).clip(RoundedCornerShape(14.dp)).background(bg)) {
        if (image.isNotBlank()) Img(image, Modifier.fillMaxSize(), alignment = Alignment.CenterEnd)
        else Row(Modifier.align(Alignment.CenterEnd).padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items.filter { it.optString("poster").isNotBlank() }.take(4).forEach { Img(it.optString("poster"), Modifier.width(56.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(6.dp))) }
        }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to bg, .35f to bg.copy(alpha = .95f), .6f to bg.copy(alpha = .4f), 1f to Color.Transparent)))
        Column(Modifier.align(Alignment.CenterStart).padding(start = 16.dp).fillMaxWidth(.6f)) {
            Text("COLECCIÓN", style = ts(9, FontWeight.Bold, C.accent).copy(letterSpacing = 2.sp))
            Text(name.ifBlank { "Sin nombre" }, style = ts(17, FontWeight.Bold, Color.White), maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (tagline.isNotBlank()) Text(tagline, style = ts(10.5f, color = Color.White.copy(alpha = .75f)), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Colores sugeridos + automático (negro suave). */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ColorPick(cur: String, onPick: (String) -> Unit) {
    val opts = listOf("", "#0d0b0c", "#3a1230", "#5a1638", "#1d1240", "#0f2a4a", "#0f3d1f", "#3d2a0f", "#4a0f14", "#2b2b2e", "#123f3f", "#40123d")
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        opts.forEach { h ->
            val on = cur.equals(h, true)
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(if (h.isBlank()) Brush.sweepGradient(listOf(Color(0xFF333333), Color(0xFF111111), Color(0xFF333333))) else Brush.linearGradient(listOf(Color(android.graphics.Color.parseColor(h)), Color(android.graphics.Color.parseColor(h)))))
                    .border(if (on) 3.dp else 1.dp, if (on) C.accent else Color.White.copy(alpha = .2f), CircleShape).tap({ onPick(h) }, .9f),
                contentAlignment = Alignment.Center
            ) { if (h.isBlank()) Text("Auto", style = ts(9, FontWeight.Bold, Color.White)) }
        }
    }
    var hex by remember(cur) { mutableStateOf(cur) }
    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.Bottom) {
        AField("Otro color (#rrggbb)", hex, { hex = it.trim() }, "#3a1230", modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.padding(bottom = 10.dp)) { AButton("Usar", { if (Regex("^#[0-9a-fA-F]{6}$").matches(hex)) onPick(hex.lowercase()) else AdminToast.show("Escribilo así: #3a1230", true) }) }
    }
}

/** Vista previa del estilo "de borde a borde" (franja tipo HBO Max). */
@Composable
private fun CollFullPreview(doc: Doc, items: List<JSONObject>) {
    val o = doc.o
    val col = Regex("^#[0-9a-fA-F]{6}$").takeIf { it.matches(doc.str("color")) }?.let { Color(android.graphics.Color.parseColor(doc.str("color"))) } ?: Color(0xFF3A1230)
    val ids = o.optJSONArray("row")?.strs() ?: emptyList()
    val rowTs = items.filter { ids.isEmpty() || it.optString("id") in ids }
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(col)) {
        if (doc.str("image").isNotBlank()) Img(doc.str("image"), Modifier.matchParentSize().graphicsLayer { alpha = .3f })
        Box(Modifier.matchParentSize().background(Brush.radialGradient(listOf(Color.White.copy(alpha = .26f), Color.Transparent), center = androidx.compose.ui.geometry.Offset(120f, 100f), radius = 600f)))
        Column(Modifier.padding(14.dp)) {
            Text("COLECCIÓN", style = ts(9, FontWeight.Bold, Color.White.copy(alpha = .8f)).copy(letterSpacing = 2.sp))
            if (doc.str("logo").isNotBlank()) Img(doc.str("logo"), Modifier.padding(top = 4.dp).width(160.dp).height(46.dp), scale = ContentScale.Fit, alignment = Alignment.CenterStart)
            else Text(doc.str("name").ifBlank { "Sin nombre" }, style = ts(18, FontWeight.Bold, Color.White), maxLines = 2)
            if (doc.str("tagline").isNotBlank()) Text(doc.str("tagline"), style = ts(11, color = Color.White.copy(alpha = .85f)), maxLines = 2)
            Text("Ver colección", style = ts(10, FontWeight.Bold, Color.Black), modifier = Modifier.padding(top = 8.dp).background(Color.White, RoundedCornerShape(99.dp)).padding(horizontal = 10.dp, vertical = 5.dp))
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowTs.take(5).forEach { t ->
                    Box(Modifier.width(52.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(5.dp)).background(Color.White.copy(alpha = .12f))) { if (t.optString("poster").isNotBlank()) Img(t.optString("poster"), Modifier.fillMaxSize()) }
                }
            }
        }
    }
}

@Composable
private fun AdminColl(id: String, nav: Nav) {
    val src = AdminData.colls.objs().firstOrNull { it.optString("id") == id }
    if (src == null) { Column { AdminTop("Colección", null, nav); Text("Esta colección ya no existe.", style = ts(14, color = C.muted), modifier = Modifier.padding(20.dp)) }; return }
    val doc = remember(id) { Doc(JSONObject(src.toString())) }
    val c = doc.o
    var picking by remember { mutableStateOf(false) }
    var askDel by remember { mutableStateOf(false) }
    var askLeave by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    BackHandler(enabled = doc.dirty) { askLeave = true }
    doc.v
    val ids = c.optJSONArray("items")?.strs() ?: emptyList()
    val items = ids.mapNotNull { AdminData.titleById(it) }
    fun write(all: (JSONArray) -> JSONArray, then: () -> Unit) {
        saving = true
        scope.launch {
            runCatching { AdminData.saveColls(all(JSONArray(AdminData.colls.toString()))) }.onSuccess { then() }.onFailure { AdminToast.show(it.message ?: "No se pudo guardar", true) }
            saving = false
        }
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(bottom = bottomPad + 110.dp)) {
            item { AdminTop(doc.str("name").ifBlank { "Colección" }, "${items.size} títulos", nav, onBack = { if (doc.dirty) askLeave = true else nav.back() }) }
            item {
                ACard("Así se ve en el inicio") {
                    if (doc.str("style") == "full") CollFullPreview(doc, items) else CollPreview(doc.str("name"), doc.str("tagline"), doc.str("image"), items)
                }
            }
            item {
                ACard("Estilo", "Igual en la web, la TV y el celular.") {
                    ASegment(listOf("" to "Banner", "full" to "Borde a borde + fila"), doc.str("style")) { v -> if (v.isBlank()) { c.remove("style"); doc.touch() } else doc.set("style", v) }
                    if (doc.str("style") == "full") {
                        Spacer(Modifier.height(14.dp))
                        AImage("Logo (opcional)", "logo", doc.str("logo"), "PNG transparente · si no hay, se muestra el nombre", ratio = 3f, contain = true) { doc.set("logo", it) }
                        Text("COLOR DE FONDO Y DIFUMINADO", style = ts(10.5f, FontWeight.Bold, C.muted), modifier = Modifier.padding(bottom = 8.dp))
                        ColorPick(doc.str("color")) { v -> if (v.isBlank()) { c.remove("color"); doc.touch() } else doc.set("color", v) }
                    }
                }
            }
            item {
                ACard("Datos") {
                    AField("Nombre", doc.str("name"), { doc.set("name", it) }, "Ej: Las chicas de los 2000")
                    AField("Frase corta (opcional)", doc.str("tagline"), { doc.set("tagline", it) }, "Una línea que la describa", lines = 2)
                    AImage("Imagen de fondo (opcional)", "coll", doc.str("image"), "3:1 · 1800 × 600 · sin imagen se usan los pósters") { doc.set("image", it) }
                }
            }
            item {
                ACard("Títulos", if (doc.str("style") == "full") "En este orden aparecen. Marcá con ★ los que van en la fila del inicio (si no marcás ninguno, van todos)." else "En este orden aparecen en la colección.") {
                    items.forEachIndexed { i, t ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(42.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(5.dp)).background(C.surface)) { if (t.optString("poster").isNotBlank()) Img(t.optString("poster"), Modifier.fillMaxSize()) }
                            Text(t.optString("title"), style = ts(14, FontWeight.Bold, Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp).weight(1f))
                            if (doc.str("style") == "full") {
                                val row = c.optJSONArray("row")?.strs() ?: emptyList()
                                val on = t.optString("id") in row
                                Box(Modifier.padding(start = 6.dp).size(32.dp).clip(CircleShape).background(if (on) Color(0xFFFFD36B) else Color.White.copy(alpha = .1f))
                                    .tap({ val n = if (on) row - t.optString("id") else row + t.optString("id"); c.put("row", JSONArray(ids.filter { it in n })); doc.touch() }, .9f), contentAlignment = Alignment.Center) {
                                    Text("★", style = ts(14, FontWeight.Bold, if (on) Color.Black else Color.White.copy(alpha = .5f)))
                                }
                            }
                            if (i > 0) MiniBtn("↑") { val l = ids.toMutableList(); l.add(i - 1, l.removeAt(ids.indexOf(t.optString("id")))); c.put("items", JSONArray(l)); doc.touch() }
                            if (i < items.size - 1) MiniBtn("↓") { val l = ids.toMutableList(); val k = ids.indexOf(t.optString("id")); l.add(k + 1, l.removeAt(k)); c.put("items", JSONArray(l)); doc.touch() }
                            MiniBtn("✕", danger = true) { c.put("items", JSONArray(ids - t.optString("id"))); doc.touch() }
                        }
                    }
                    AButton("+ Agregar título", { picking = true }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }
            }
            item { Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) { AButton("Eliminar colección", { askDel = true }, danger = true) } }
        }
        SaveBar(doc.dirty, saving) {
            write({ a -> JSONArray(a.objs().map { if (it.optString("id") == id) c else it }) }) { doc.dirty = false; AdminToast.show("Colección guardada") }
        }
        if (picking) TitlePickerSheet({ c.put("items", JSONArray(ids + it)); doc.touch(); picking = false }, { picking = false }, ids.toSet())
        if (askLeave) AConfirm("¿Salir sin guardar?", null, "Salir", { askLeave = false; doc.dirty = false; nav.back() }, { askLeave = false })
        if (askDel) AConfirm("¿Eliminar la colección?", null, "Eliminar", {
            askDel = false; write({ a -> JSONArray(a.objs().filter { it.optString("id") != id }) }) { AdminToast.show("Colección eliminada"); nav.back() }
        }, { askDel = false })
    }
}

@Composable
private fun MiniBtn(t: String, danger: Boolean = false, onClick: () -> Unit) {
    Box(Modifier.padding(start = 6.dp).size(32.dp).clip(CircleShape).background(if (danger) Color(0xFF5A1A26) else Color.White.copy(alpha = .1f)).tap(onClick, .9f), contentAlignment = Alignment.Center) {
        Text(t, style = ts(13, FontWeight.Bold, if (danger) Color(0xFFFF9AA8) else Color.White))
    }
}

/* ---------------- filas destacadas ---------------- */

@Composable
private fun AdminRows(nav: Nav) {
    AdminData.ver
    val scope = rememberCoroutineScope()
    val list = AdminData.rows.objs()
    fun save(l: List<JSONObject>) { scope.launch { runCatching { AdminData.saveRows(JSONArray(l)) }.onFailure { AdminToast.show("No se pudo guardar", true) } } }
    LazyColumn(contentPadding = PaddingValues(bottom = bottomPad + 30.dp)) {
        item {
            AdminTop("Filas destacadas", "${list.size} filas", nav) {
                AButton("Nueva", {
                    val id = AdminData.uid("r")
                    scope.launch { runCatching { AdminData.saveRows(JSONArray(list + JSONObject().put("id", id).put("title", "También te podría gustar").put("items", JSONArray()))) }.onSuccess { nav.go(Screen.Admin("fila", id)) } }
                }, primary = true, icon = Ic.plus)
            }
            Text("Filas con tarjetas grandes (foto, logo y etiqueta) que aparecen en el inicio en este orden.", style = ts(13, color = C.muted), modifier = Modifier.padding(horizontal = 18.dp).padding(bottom = 8.dp))
        }
        itemsIndexed(list, key = { _, r -> r.optString("id") }) { i, r ->
            val its = r.optJSONArray("items")?.objs() ?: emptyList()
            Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = .055f)).tap({ nav.go(Screen.Admin("fila", r.optString("id"))) }, .99f).padding(12.dp)) {
                Text(r.optString("title").ifBlank { "Fila sin nombre" }, style = ts(16, FontWeight.Bold, Color.White))
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    its.take(5).forEach { it2 ->
                        val t = AdminData.titleById(it2.optString("titleId"))
                        val img = it2.optString("image").ifBlank { t?.optString("backdrop")?.ifBlank { t.optString("poster") } ?: "" }
                        Box(Modifier.width(52.dp).aspectRatio(3f / 4.45f).clip(RoundedCornerShape(6.dp)).background(C.surface)) { if (img.isNotBlank()) Img(img, Modifier.fillMaxSize()) }
                    }
                    if (its.isEmpty()) Text("Sin títulos todavía", style = ts(12.5f, color = C.muted))
                }
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${its.size} tarjetas", style = ts(12.5f, color = C.muted), modifier = Modifier.weight(1f))
                    if (i > 0) AButton("↑", { val l = list.toMutableList(); l.add(i - 1, l.removeAt(i)); save(l) })
                    Spacer(Modifier.width(6.dp))
                    if (i < list.size - 1) AButton("↓", { val l = list.toMutableList(); l.add(i + 1, l.removeAt(i)); save(l) })
                }
            }
        }
        if (list.isEmpty()) item { Text("Todavía no hay filas destacadas.", style = ts(14, color = C.muted), modifier = Modifier.padding(20.dp)) }
    }
}

@Composable
private fun AdminRow(id: String, nav: Nav) {
    val src = AdminData.rows.objs().firstOrNull { it.optString("id") == id }
    if (src == null) { Column { AdminTop("Fila", null, nav); Text("Esta fila ya no existe.", style = ts(14, color = C.muted), modifier = Modifier.padding(20.dp)) }; return }
    val doc = remember(id) { Doc(JSONObject(src.toString())) }
    val r = doc.o
    var pickFor by remember { mutableIntStateOf(-2) }
    var askDel by remember { mutableStateOf(false) }
    var askLeave by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    BackHandler(enabled = doc.dirty) { askLeave = true }
    doc.v
    val its = r.optJSONArray("items") ?: JSONArray().also { r.put("items", it) }
    fun write(all: (JSONArray) -> JSONArray, then: () -> Unit) {
        saving = true
        scope.launch { runCatching { AdminData.saveRows(all(JSONArray(AdminData.rows.toString()))) }.onSuccess { then() }.onFailure { AdminToast.show(it.message ?: "No se pudo guardar", true) }; saving = false }
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(bottom = bottomPad + 110.dp)) {
            item { AdminTop(doc.str("title").ifBlank { "Fila" }, "${its.length()} tarjetas", nav, onBack = { if (doc.dirty) askLeave = true else nav.back() }) }
            item { ACard { AField("Nombre de la fila", doc.str("title"), { doc.set("title", it) }, "Ej: También te podría gustar") } }
            item {
                ACard("Tarjetas", "Si no ponés una imagen propia, se usa el fondo de la ficha.") {
                    for (i in 0 until its.length()) {
                        val it2 = its.getJSONObject(i)
                        val t = AdminData.titleById(doc.str("titleId", it2))
                        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.Top) {
                            Box(Modifier.width(96.dp)) { AImage("Imagen", "frow", doc.str("image", it2).ifBlank { "" }, null) { v -> doc.set("image", v, it2) } }
                            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = .3f)).tap({ pickFor = i }, .99f).padding(10.dp)) {
                                    Text(t?.optString("title") ?: "Elegir título", style = ts(14, FontWeight.Bold, if (t == null) C.accent else Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Spacer(Modifier.height(8.dp))
                                AField("Etiqueta (opcional)", doc.str("label", it2), { v -> doc.set("label", v, it2) }, "Nueva serie")
                                Row {
                                    if (i > 0) MiniBtn("←") { val a = its.get(i - 1); its.put(i - 1, its.get(i)); its.put(i, a); doc.touch() }
                                    if (i < its.length() - 1) MiniBtn("→") { val a = its.get(i + 1); its.put(i + 1, its.get(i)); its.put(i, a); doc.touch() }
                                    MiniBtn("✕", danger = true) { its.remove(i); doc.touch() }
                                }
                            }
                        }
                    }
                    AButton("+ Agregar título", { pickFor = -1 }, modifier = Modifier.fillMaxWidth())
                }
            }
            item { Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) { AButton("Eliminar fila", { askDel = true }, danger = true) } }
        }
        SaveBar(doc.dirty, saving) { write({ a -> JSONArray(a.objs().map { if (it.optString("id") == id) r else it }) }) { doc.dirty = false; AdminToast.show("Fila guardada") } }
        if (pickFor != -2) TitlePickerSheet({ tid ->
            if (pickFor == -1) its.put(JSONObject().put("titleId", tid).put("label", "").put("image", "")) else its.getJSONObject(pickFor).put("titleId", tid)
            doc.touch(); pickFor = -2
        }, { pickFor = -2 })
        if (askLeave) AConfirm("¿Salir sin guardar?", null, "Salir", { askLeave = false; doc.dirty = false; nav.back() }, { askLeave = false })
        if (askDel) AConfirm("¿Eliminar la fila?", null, "Eliminar", {
            askDel = false; write({ a -> JSONArray(a.objs().filter { it.optString("id") != id }) }) { AdminToast.show("Fila eliminada"); nav.back() }
        }, { askDel = false })
    }
}

/* ---------------- géneros ---------------- */

@Composable
private fun AdminGenres(nav: Nav) {
    AdminData.ver
    val scope = rememberCoroutineScope()
    val counts = AdminData.titles.flatMap { t -> t.optJSONArray("genres")?.strs() ?: emptyList() }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }
    var renaming by remember { mutableStateOf<String?>(null) }
    var removing by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    fun apply(change: (List<String>) -> List<String>, done: String) {
        busy = true
        scope.launch {
            try {
                AdminData.titles.map { JSONObject(it.toString()) }.forEach { t ->
                    val g = t.optJSONArray("genres")?.strs() ?: emptyList(); val n = change(g).distinct()
                    if (n != g) { t.put("genres", JSONArray(n)); AdminData.saveTitle(t) }
                }
                AdminToast.show(done)
            } catch (e: Exception) { AdminToast.show(e.message ?: "No se pudo completar", true) }
            busy = false
        }
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(bottom = bottomPad + 30.dp)) {
            item {
                AdminTop("Géneros", "${counts.size} géneros", nav) { if (busy) CircularProgressIndicator(color = C.accent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp)) }
                Text("Cada género con títulos publicados es una pestaña del inicio en el celular y una fila de la portada.", style = ts(13, color = C.muted), modifier = Modifier.padding(horizontal = 18.dp).padding(bottom = 8.dp))
            }
            items(counts, key = { it.key }) { (g, n) ->
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = .055f)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(g, style = ts(15, FontWeight.Bold, Color.White))
                        Text("$n título${if (n == 1) "" else "s"}", style = ts(12, color = C.muted))
                    }
                    AButton("Renombrar", { if (!busy) renaming = g })
                    Spacer(Modifier.width(6.dp))
                    AButton("Quitar", { if (!busy) removing = g }, danger = true)
                }
            }
        }
        renaming?.let { old ->
            var v by remember(old) { mutableStateOf(old) }
            BackHandler { renaming = null }
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .7f)).tap({ renaming = null }, 1f), contentAlignment = Alignment.Center) {
                Column(Modifier.padding(28.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xFF1C181B)).tap({}, 1f).padding(20.dp)) {
                    Text("Renombrar “$old”", style = ts(17, FontWeight.Bold, Color.White), modifier = Modifier.padding(bottom = 12.dp))
                    AField("Nuevo nombre", v, { v = it })
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                        AButton("Cancelar", { renaming = null })
                        AButton("Renombrar", { val n = v.trim(); renaming = null; if (n.isNotBlank() && n != old) apply({ l -> l.map { if (it == old) n else it } }, "Género renombrado") }, primary = true)
                    }
                }
            }
        }
        removing?.let { g ->
            AConfirm("¿Quitar “$g” de todos los títulos?", "Los títulos no se borran, solo pierden este género.", "Quitar", { removing = null; apply({ l -> l - g }, "Género quitado") }, { removing = null })
        }
    }
}

/* ---------------- sitio ---------------- */

@Composable
private fun AdminSite(nav: Nav) {
    val doc = remember(AdminData.loaded) { Doc(JSONObject(AdminData.site.toString())) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(bottom = bottomPad + 110.dp)) {
            item { AdminTop("Sitio", "Logo, pie de página y redes", nav) }
            item {
                ACard("Logo", "Aparece arriba en la web y en la app. PNG transparente.") {
                    AImage("Logo principal", "sitelogo", doc.str("logo"), "600 × 160 px aprox.", ratio = 3f, contain = true) { doc.set("logo", it) }
                    AImage("Logo del pie (opcional)", "sitelogo", doc.str("footerLogo"), "300 × 100 px aprox.", ratio = 3f, contain = true) { doc.set("footerLogo", it) }
                }
            }
            item {
                ACard("Pie de página") {
                    AField("Texto principal", doc.str("footerText"), { doc.set("footerText", it) }, "REALITY VAULT")
                    AField("Texto secundario", doc.str("footerSub"), { doc.set("footerSub", it) }, "© 2026 · Todos los derechos reservados")
                }
            }
            item {
                ACard("Redes", "Los íconos aparecen solo si cargás el enlace.") {
                    AField("Instagram", doc.str("instagram"), { doc.set("instagram", it) }, "https://instagram.com/…")
                    AField("TikTok", doc.str("tiktok"), { doc.set("tiktok", it) }, "https://tiktok.com/@…")
                }
            }
            item {
                ACard("DMCA", "La primera línea es el título; cada línea nueva es un párrafo.") {
                    AField("Email de contacto", doc.str("dmcaEmail"), { doc.set("dmcaEmail", it) }, "contacto@…")
                    AField("Texto", doc.str("dmcaText"), { doc.set("dmcaText", it) }, "", lines = 8)
                }
            }
            item { Text("El respaldo del catálogo y la importación desde TMDB siguen en el panel de la web.", style = ts(12, color = C.faint), modifier = Modifier.padding(20.dp)) }
        }
        SaveBar(doc.dirty, saving) {
            saving = true
            scope.launch { runCatching { AdminData.saveSite(doc.o) }.onSuccess { doc.dirty = false; AdminToast.show("Guardado") }.onFailure { AdminToast.show(it.message ?: "No se pudo guardar", true) }; saving = false }
        }
    }
}
