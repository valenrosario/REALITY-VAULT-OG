package com.realityvault.mobile

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.runtime.derivedStateOf
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.realityvault.tv.Banner
import com.realityvault.tv.Coll
import com.realityvault.tv.FeatItem
import com.realityvault.tv.Store
import com.realityvault.tv.Title
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/* ---------------- datos del inicio (mismo criterio que la TV y la web) ---------------- */

data class Slide(val t: Title, val image: String, val headline: String, val text: String)

fun slides(match: (Title) -> Boolean = { true }): List<Slide> {
    // Mismo orden "al azar pero fijo durante la sesión" que usa el sitio web (ver Store.bannerSeed).
    val seededRandom = kotlin.random.Random(Store.bannerSeed)
    val fromBanners = Store.banners.shuffled(seededRandom).mapNotNull { b: Banner ->
        val t = Store.byId(b.titleId)?.takeIf { it.status != "borrador" && match(it) } ?: return@mapNotNull null
        // en el celular: primero la imagen vertical pensada para celular (como la web)
        val img = b.imageMobile.ifBlank { t.bannerMobile.ifBlank { b.image.ifBlank { t.bannerImage.ifBlank { t.backdrop.ifBlank { t.poster } } } } }
        Slide(t, img, b.headline, b.text.ifBlank { t.tagline.ifBlank { t.description } })
    }
    if (fromBanners.isNotEmpty()) return fromBanners
    val pool = Store.published().filter(match)
    return pool.filter { it.featured }.ifEmpty { pool.sortedByDescending { it.createdAt } }.shuffled(seededRandom).take(6).map {
        Slide(it, it.bannerMobile.ifBlank { it.bannerImage.ifBlank { it.backdrop.ifBlank { it.poster } } }, "", it.tagline.ifBlank { it.description })
    }
}

class RowSpec(val key: String, val title: String, val kind: String, val titles: List<Title>, val feat: List<Pair<Title, FeatItem>> = emptyList(), val coll: Coll? = null)

/** Recomendados según tus gustos: géneros de Mi lista (los favoritos que elegiste) y de lo que viste. */
fun recommended(): List<Title> {
    val pub = Store.published()
    val liked = (Store.myList + Store.progress.map { it.titleId }).distinct().mapNotNull { Store.byId(it) }
    val seen = liked.map { it.id }.toSet()
    val gw = HashMap<String, Int>()
    liked.forEach { t -> t.genres.forEach { g -> gw[g] = (gw[g] ?: 0) + 1 } }
    val series = liked.count { it.isSerie }; val movies = liked.size - series
    val scored = pub.filter { it.id !in seen }.map { t ->
        t to (t.genres.sumOf { gw[it] ?: 0 } * 3 + (if (t.isSerie == (series >= movies)) 1 else 0) + liked.count { l -> l.cast.any { c -> t.cast.any { it.name.equals(c.name, true) } } } * 2)
    }.filter { it.second > 1 }.sortedWith(compareByDescending<Pair<Title, Int>> { it.second }.thenByDescending { it.first.createdAt }).map { it.first }
    val fill = pub.filter { it.id !in seen && it !in scored }.sortedWith(compareByDescending<Title> { it.featured }.thenByDescending { it.createdAt })
    return (scored + fill).take(20)
}

private fun tabMatch(tab: String): (Title) -> Boolean = when (tab) {
    "Series" -> { t -> t.isSerie }
    "Películas" -> { t -> !t.isSerie }
    "Para vos" -> { _ -> true }
    else -> { t -> tab in t.genres }
}

fun homeRows(tab: String = "Para vos"): List<RowSpec> {
    if (tab != "Para vos") return tabRows(tab)
    val pub = Store.published()
    val rows = mutableListOf<RowSpec>()
    val cont = Store.progress.mapNotNull { p -> Store.byId(p.titleId)?.takeIf { it.status != "borrador" } }
    if (cont.isNotEmpty()) rows += RowSpec("cont", "Seguir viendo", "wide", cont)
    val rec = recommended()
    if (rec.isNotEmpty()) rows += RowSpec("rec", "Recomendado para vos", "poster", rec)
    val top = Store.top10.mapNotNull { id -> pub.firstOrNull { it.id == id } }.take(10)
    if (top.isNotEmpty()) rows += RowSpec("top", "Top 10 de la semana", "top", top)
    val list = Store.myList.mapNotNull { id -> pub.firstOrNull { it.id == id } }
    if (list.isNotEmpty()) rows += RowSpec("list", "Mi lista", "wide", list)
    Store.featRows.forEach { r ->
        val items = r.items.mapNotNull { i -> pub.firstOrNull { it.id == i.titleId }?.let { it to i } }
        if (items.isNotEmpty()) rows += RowSpec("f-" + r.id, r.title, "feat", emptyList(), items)
    }
    val rest = mutableListOf<RowSpec>()
    val feat = pub.filter { it.featured }
    if (feat.isNotEmpty()) rest += RowSpec("feat", "Destacados", "poster", feat)
    val newest = pub.sortedByDescending { it.createdAt }.take(20)
    if (newest.isNotEmpty()) rest += RowSpec("new", "Novedades", "poster", newest)
    val series = pub.filter { it.isSerie }; if (series.isNotEmpty()) rest += RowSpec("series", "Series", "poster", series)
    val movies = pub.filter { !it.isSerie }; if (movies.isNotEmpty()) rest += RowSpec("movies", "Películas", "poster", movies)
    pub.flatMap { t -> t.genres.map { it to t } }.groupBy({ it.first }, { it.second })
        .entries.sortedByDescending { it.value.size }.take(8)
        .forEach { (g, l) -> rest += RowSpec("g-$g", g, "poster", l) }
    // las colecciones se reparten entre las filas, igual que en la web (una cada tanto, no todas juntas)
    val colls = Store.colls.mapNotNull { c ->
        val ts = c.items.mapNotNull { i -> pub.firstOrNull { it.id == i } }
        if (ts.isEmpty()) null else RowSpec("c-" + c.id, c.name, "coll", ts, coll = c)
    }
    return rows + spread(rest, colls)
}

/**
 * Reparte las colecciones entre las filas comunes: nunca dos colecciones seguidas.
 * Si hay más colecciones que lugares, las que sobran no se muestran en esa pestaña.
 */
private fun spread(rows: List<RowSpec>, colls: List<RowSpec>): List<RowSpec> {
    if (colls.isEmpty()) return rows
    if (rows.isEmpty()) return colls.take(1)
    val gap = maxOf(2, (rows.size + 1) / (colls.size + 1))
    val out = mutableListOf<RowSpec>(); var ci = 0
    rows.forEachIndexed { i, r -> out += r; if (ci < colls.size && (i + 1) % gap == 0 && i < rows.lastIndex) out += colls[ci++] }
    if (ci < colls.size && out.lastOrNull()?.kind != "coll") out += colls[ci]
    return out
}

/** Filas de una pestaña (Series, Películas o un género): todo se arma ahí mismo, sin salir del inicio. */
private fun tabRows(tab: String): List<RowSpec> {
    val match = tabMatch(tab)
    val pub = Store.published().filter(match)
    val rows = mutableListOf<RowSpec>()
    val cont = Store.progress.mapNotNull { p -> Store.byId(p.titleId)?.takeIf { it.status != "borrador" && match(it) } }
    if (cont.isNotEmpty()) rows += RowSpec("cont", "Seguir viendo", "wide", cont)
    val rec = recommended().filter(match)
    if (rec.size >= 3) rows += RowSpec("rec", "Recomendado para vos", "poster", rec)
    val top = Store.top10.mapNotNull { id -> pub.firstOrNull { it.id == id } }.take(10)
    if (top.size >= 3) rows += RowSpec("top", "Top 10 de la semana", "top", top)
    val newest = pub.sortedByDescending { it.createdAt }.take(20)
    if (newest.isNotEmpty()) rows += RowSpec("new", "Novedades", "poster", newest)
    val feat = pub.filter { it.featured }
    if (feat.isNotEmpty()) rows += RowSpec("feat", "Destacados", "poster", feat)
    pub.flatMap { t -> t.genres.filter { it != tab }.map { it to t } }.groupBy({ it.first }, { it.second })
        .entries.filter { it.value.size >= 2 }.sortedByDescending { it.value.size }.take(8)
        .forEach { (g, l) -> rows += RowSpec("g-$g", g, "poster", l) }
    val colls = Store.colls.mapNotNull { c ->
        val ts = c.items.mapNotNull { i -> pub.firstOrNull { it.id == i } }
        if (ts.size >= 2) RowSpec("c-" + c.id, c.name, "coll", ts, coll = c) else null
    }
    if (rows.none { it.key == "new" } && pub.isNotEmpty()) rows += RowSpec("all", tab, "poster", pub)
    // "Seguir viendo" y "Recomendado" van arriba; las colecciones se reparten en el resto
    val head = rows.takeWhile { it.key == "cont" || it.key == "rec" }
    return head + spread(rows.drop(head.size), colls)
}

/* ---------------- pantalla de inicio (diseño tipo discovery+) ---------------- */

/** Pestañas de arriba: "Para vos" + Series, Películas y los géneros con más títulos. */
private fun homeTabs(): List<String> =
    listOf("Para vos", "Series", "Películas") + Store.published().flatMap { it.genres }.groupingBy { it }.eachCount()
        .entries.sortedByDescending { it.value }.take(8).map { it.key }

/** Logo a la izquierda; transmitir, novedades y perfil a la derecha. */
@Composable
private fun TopIcons(nav: Nav) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        LocalLogo(Modifier.height(28.dp).width(150.dp))
        Spacer(Modifier.weight(1f))
        CastButton(42.dp, 25.dp)
        Spacer(Modifier.width(6.dp))
        BellButton(nav, 42.dp, 25.dp)
        Spacer(Modifier.width(10.dp))
        AvatarButton(nav)
    }
}

@Composable
private fun Tabs(tabs: List<String>, selected: String, onSelect: (String) -> Unit) {
    val state = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(selected) { val i = tabs.indexOf(selected); if (i > 0) state.animateScrollToItem((i - 1).coerceAtLeast(0)) }
    LazyRow(state = state, contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        items(tabs, key = { it }) { tab ->
            val on = tab == selected
            val alpha by animateFloatAsState(if (on) 1f else .68f, tween(200), label = "tabA")
            Column(Modifier.width(IntrinsicSize.Max).tap({ onSelect(tab) }, .95f).padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(tab, style = ts(17, FontWeight.Bold, Color.White.copy(alpha = alpha)), maxLines = 1)
                Spacer(Modifier.height(7.dp))
                // subrayado del ancho de la palabra, como en la captura
                androidx.compose.animation.AnimatedVisibility(on, enter = fadeIn(tween(180)) + androidx.compose.animation.expandHorizontally(tween(220)), exit = fadeOut(tween(120)) + androidx.compose.animation.shrinkHorizontally(tween(160))) {
                    Box(Modifier.fillMaxWidth().height(3.dp).background(Color.White, RoundedCornerShape(99.dp)))
                }
                if (!on) Spacer(Modifier.height(3.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(nav: Nav) {
    if (!Store.loaded) { Loading(); return }
    val tabs = homeTabs()
    var tab by remember { mutableStateOf(nav.memory.text["homeTab"]?.takeIf { it in tabs } ?: "Para vos") }
    val all = remember(tab, Store.titles, Store.banners) { slides(tabMatch(tab)) }
    val rows = remember(tab, Store.titles, Store.myList, Store.progress, Store.colls, Store.top10, Store.featRows) { homeRows(tab) }
    if (Store.published().isEmpty()) { Empty("Todavía no hay títulos publicados."); return }
    val listState = nav.memory.list("home")
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val scope = rememberCoroutineScope()
    val pick: (String) -> Unit = { tb -> if (tb != tab) { tab = tb; nav.memory.text["homeTab"] = tb; scope.launch { listState.scrollToItem(0) } } }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottom + 96.dp)) {
            item(key = "hero-$tab") {
                if (all.isNotEmpty()) Hero(all, tabs, tab, pick, nav)
                else Column(Modifier.statusBarsPadding().padding(top = 10.dp)) { TopIcons(nav); Spacer(Modifier.height(10.dp)); Tabs(tabs, tab, pick) }
            }
            items(rows, key = { "$tab-" + it.key }) { r -> RowSection(r, nav, tab) }
        }
        // al bajar, la barra de arriba queda fija con fondo
        val pinned by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 420 } }
        AnimatedVisibility(pinned, enter = fadeIn(tween(180)), exit = fadeOut(tween(140))) {
            Column(Modifier.fillMaxWidth().background(C.bg.copy(alpha = .96f)).statusBarsPadding().padding(top = 8.dp, bottom = 6.dp)) {
                TopIcons(nav); Spacer(Modifier.height(4.dp)); Tabs(tabs, tab, pick)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Hero(all: List<Slide>, tabs: List<String>, tab: String, pick: (String) -> Unit, nav: Nav) {
    val cfg = LocalConfiguration.current
    val h = minOf(cfg.screenWidthDp * 1.72f, cfg.screenHeightDp * 0.82f).dp
    val pager = rememberPagerState { all.size }
    // pasa solo cada 7 s; si la persona lo está arrastrando, espera
    LaunchedEffect(pager.currentPage, pager.isScrollInProgress, all.size) {
        if (all.size > 1 && !pager.isScrollInProgress) { delay(7000); pager.animateScrollToPage((pager.currentPage + 1) % all.size) }
    }
    Box(Modifier.fillMaxWidth().height(h)) {
        HorizontalPager(pager, Modifier.fillMaxSize(), key = { it }) { i ->
            val slide = all[i]
            val t = slide.t
            Box(Modifier.fillMaxSize().tap({ nav.go(Screen.Detail(t.id)) }, 1f)) {
                if (slide.image.isNotBlank()) Img(slide.image, Modifier.fillMaxSize(), alignment = Alignment.TopCenter, crossfade = false) else Placeholder(t, Modifier.fillMaxSize(), big = true)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = .55f), .2f to Color.Transparent, .5f to Color.Transparent, .74f to C.bg.copy(alpha = .55f), .9f to C.bg.copy(alpha = .94f), 1f to C.bg
                )))
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 26.dp, end = 26.dp, bottom = 50.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (slide.headline.isNotBlank()) Text(slide.headline, style = ts(34, FontWeight.Bold, Color.White).copy(textAlign = TextAlign.Center, lineHeight = 36.sp), maxLines = 2)
                    else TitleLogo(t, (cfg.screenWidthDp * .74f).dp, 118.dp, 40)
                    val meta = listOfNotNull(
                        t.rating.ifBlank { null },
                        if (t.isSerie) t.seasons.size.takeIf { it > 0 }?.let { "$it temporada${if (it == 1) "" else "s"}" } else t.duration.ifBlank { t.year.ifBlank { null } }
                    )
                    if (meta.isNotEmpty()) Text(meta.joinToString("     "), style = ts(14, color = Color.White.copy(alpha = .8f)), modifier = Modifier.padding(top = 18.dp))
                    if (slide.text.isNotBlank()) Text(
                        slide.text, style = ts(16, color = Color.White.copy(alpha = .93f)).copy(textAlign = TextAlign.Center, lineHeight = 22.sp),
                        maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
        // difuminado oscuro desde arriba para que se lean el logo, los íconos y las pestañas
        Box(Modifier.fillMaxWidth().height(230.dp).background(Brush.verticalGradient(
            0f to Color.Black.copy(alpha = .82f), .45f to Color.Black.copy(alpha = .5f), .75f to Color.Black.copy(alpha = .18f), 1f to Color.Transparent
        )))
        // arriba, sobre la portada: logo, íconos y pestañas
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 10.dp)) {
            TopIcons(nav); Spacer(Modifier.height(10.dp)); Tabs(tabs, tab, pick)
        }
        if (all.size > 1) Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(13.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            all.indices.forEach { i ->
                val on = i == pager.currentPage
                Box(Modifier.size(9.dp).background(if (on) Color.White else Color(0xFF5B5F78), CircleShape))
            }
        }
    }
}

/** Botón redondo de agregar / sacar de Mi lista (pide cuenta si no entró). */
@Composable
fun ListToggle(t: Title, nav: Nav, size: androidx.compose.ui.unit.Dp = 46.dp) {
    val scope = rememberCoroutineScope()
    val liked = t.id in Store.myList
    androidx.compose.foundation.layout.Box(
        Modifier.size(size).tap({ if (Store.session == null) nav.go(Screen.Login()) else scope.launch { Store.toggleList(t.id) } }, .9f)
            .clip(androidx.compose.foundation.shape.CircleShape).background(Color(0x9C3C3C42))
            .semantics { this.contentDescription = if (liked) "Quitar de Mi lista" else "Agregar a Mi lista" },
        contentAlignment = Alignment.Center
    ) { com.realityvault.tv.LikeHeart(liked, size * .46f) }
}

/* ---------------- filas ---------------- */

@Composable
private fun RowSection(r: RowSpec, nav: Nav, tab: String) {
    if (r.kind == "coll") { if (r.coll?.full == true) CollFull(r, nav, tab) else CollBanner(r, nav); return }
    val cardW = (LocalConfiguration.current.screenWidthDp * .4f).coerceAtMost(220f).dp
    Column(Modifier.padding(top = 26.dp)) {
        Text(r.title, style = ts(19, FontWeight.Bold, Color.White), modifier = Modifier.padding(start = 20.dp, bottom = 12.dp))
        LazyRow(
            // cada pestaña tiene su propio scroll por fila (compartirlo trababa las filas al cambiar de género)
            state = nav.memory.list("row-$tab-" + r.key),
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(if (r.kind == "top") 6.dp else 12.dp)
        ) {
            if (r.kind == "feat") {
                itemsIndexed(r.feat, key = { i, _ -> "f$i" }) { _, (t, item) ->
                    FeatCard(t, item, Modifier.width(124.dp).aspectRatio(3f / 4.45f).tap({ nav.go(Screen.Detail(t.id)) }))
                }
            } else itemsIndexed(r.titles.distinctBy { it.id }, key = { _, t -> t.id }) { i, t ->
                when (r.kind) {
                    "wide" -> {
                        val p = if (r.key == "cont") Store.progress.firstOrNull { it.titleId == t.id } else null
                        WideCard(t, p, Modifier.width(212.dp).aspectRatio(16f / 9f).tap({
                            val tg = Store.playTarget(t)
                            if (r.key == "cont" && tg != null) nav.play(t, tg.url, tg.season, tg.episode) else nav.go(Screen.Detail(t.id))
                        }))
                    }
                    "top" -> Row(verticalAlignment = Alignment.Bottom) {
                        Text("${i + 1}", modifier = Modifier.offset(x = 10.dp, y = 8.dp), style = ts(92, FontWeight.Bold, C.accent).copy(drawStyle = Stroke(width = 4f)))
                        PosterCard(t, Modifier.width(112.dp).aspectRatio(2f / 3f).tap({ nav.go(Screen.Detail(t.id)) }).clip(RoundedCornerShape(9.dp)))
                    }
                    else -> PosterCard(t, Modifier.width(cardW).aspectRatio(2f / 3f).tap({ nav.go(Screen.Detail(t.id)) }).clip(RoundedCornerShape(5.dp)))
                }
            }
        }
    }
}

@Composable
fun WideCard(t: Title, p: com.realityvault.tv.Progress?, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(10.dp)).background(C.surface)) {
        if (t.wide.isNotBlank()) Img(t.wide, Modifier.fillMaxSize()) else Placeholder(t, Modifier.fillMaxSize())
        if (p != null) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, .55f to Color.Transparent, 1f to Color.Black.copy(alpha = .75f))))
            if (p.season != null) Text("T${p.season}:E${p.episode}", style = ts(10.5f, FontWeight.Bold, Color.White), modifier = Modifier.align(Alignment.BottomStart).padding(start = 10.dp, bottom = 16.dp))
            val frac = p.positions[com.realityvault.tv.posKey(p.season, p.episode)]?.let { (tt, d) -> if (d > 0) (tt.toFloat() / d).coerceIn(0f, 1f) else null } ?: .45f
            Box(Modifier.align(Alignment.BottomStart).padding(start = 10.dp, end = 10.dp, bottom = 8.dp).fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = .35f), RoundedCornerShape(9.dp))) {
                Box(Modifier.fillMaxWidth(frac).height(3.dp).background(Color.White, RoundedCornerShape(9.dp)))
            }
        }
    }
}

@Composable
fun FeatCard(t: Title, item: FeatItem, modifier: Modifier = Modifier) {
    val img = item.image.ifBlank { t.wide }
    Box(modifier.clip(RoundedCornerShape(10.dp)).background(C.surface)) {
        if (img.isNotBlank()) Img(img, Modifier.fillMaxSize()) else Placeholder(t, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, .45f to Color.Transparent, 1f to Color.Black)))
        Column(Modifier.align(Alignment.BottomCenter).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (t.logo.isNotBlank()) Img(t.logo, Modifier.fillMaxWidth(.8f).height(32.dp), scale = ContentScale.Fit, alignment = Alignment.BottomCenter)
            else Text(t.title, style = ts(12, FontWeight.Bold, Color.White), maxLines = 2)
            if (item.label.isNotBlank()) { Spacer(Modifier.height(4.dp)); Text(item.label, style = ts(9, FontWeight.Bold, C.fg2), maxLines = 1) }
        }
    }
}

/* ---------------- colección "de borde a borde" con su fila (opcional, se elige en el panel) ---------------- */

@Composable
fun CollFull(r: RowSpec, nav: Nav, tab: String) {
    val c = r.coll ?: return
    val rowTs = c.rowItems.mapNotNull { id -> r.titles.firstOrNull { it.id == id } }.ifEmpty { r.titles }
    val col = c.colorArgb?.let { Color(it) } ?: Color(0xFF3A1230)
    // franja tipo HBO Max: color de fondo con un resplandor detrás del texto, y los pósters chicos en fila
    Box(Modifier.padding(top = 28.dp).fillMaxWidth().background(col)) {
        if (c.image.isNotBlank()) Img(c.image, Modifier.matchParentSize().graphicsLayer { alpha = .3f })
        Box(Modifier.matchParentSize().background(Brush.radialGradient(listOf(Color.White.copy(alpha = .26f), Color.Transparent), center = androidx.compose.ui.geometry.Offset(160f, 140f), radius = 900f)))
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(0f to Color.Transparent, .6f to Color.Transparent, 1f to Color.Black.copy(alpha = .4f))))
        Column(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().tap({ nav.go(Screen.Collection(c.id)) }, 1f).padding(start = 20.dp, end = 20.dp, top = 24.dp)) {
                Text("COLECCIÓN", style = ts(10.5f, FontWeight.Bold, Color.White.copy(alpha = .8f)).copy(letterSpacing = 2.5.sp))
                if (c.logo.isNotBlank()) Img(c.logo, Modifier.padding(top = 8.dp).fillMaxWidth(.65f).height(64.dp), scale = ContentScale.Fit, alignment = Alignment.CenterStart)
                else Text(c.name, style = ts(26, FontWeight.Bold, Color.White).copy(lineHeight = 29.sp), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                if (c.tagline.isNotBlank()) Text(c.tagline, style = ts(13.5f, color = Color.White.copy(alpha = .88f)).copy(lineHeight = 19.sp), maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                Text("Ver colección", style = ts(13, FontWeight.Bold, Color.Black),
                    modifier = Modifier.padding(top = 14.dp).background(Color.White, RoundedCornerShape(99.dp)).padding(horizontal = 18.dp, vertical = 9.dp))
            }
            LazyRow(
                state = nav.memory.list("row-$tab-" + r.key),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(rowTs, key = { _, t -> t.id }) { _, t ->
                    Column(Modifier.width(112.dp).tap({ nav.go(Screen.Detail(t.id)) })) {
                        PosterCard(t, Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(6.dp)))
                        Text(t.title, style = ts(12, FontWeight.Bold, Color.White.copy(alpha = .9f)), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        }
    }
}

/* ---------------- banner de colección (igual al de la web y la TV) ---------------- */

@Composable
private fun CollBanner(r: RowSpec, nav: Nav) {
    val c = r.coll ?: return
    val ps = r.titles.filter { it.poster.isNotBlank() }
    val bg = Color(0xFF15100F)
    val shape = RoundedCornerShape(14.dp)
    Box(Modifier.padding(top = 24.dp, start = 18.dp, end = 18.dp).fillMaxWidth().height(158.dp).tap({ nav.go(Screen.Collection(c.id)) }, .98f).clip(shape)) {
        // resplandor de color sacado de la imagen (como el desenfoque de color de la web)
        val tint = rememberTint(c.image.ifBlank { ps.firstOrNull()?.poster ?: "" })
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to bg, .45f to bg, 1f to tint.copy(alpha = .75f)))) {
            if (c.image.isNotBlank()) {
                Img(c.image, Modifier.fillMaxSize(), alignment = Alignment.CenterEnd)
            } else if (ps.isNotEmpty()) {
                // muro de pósters inclinado a la derecha; las filas se deslizan solas, una para cada lado
                val k = if (ps.size >= 4) (ps.size + 1) / 2 else ps.size
                val r1 = ps.take(k); val r2 = if (ps.size >= 4) ps.drop(k) else ps
                fun fill(a: List<Title>): List<Title> { val o = mutableListOf<Title>(); while (o.size < 7) o += a; return o.take(7) }
                val setPx = with(LocalDensity.current) { (7 * (70 + 10)).dp.toPx() }
                val inf = rememberInfiniteTransition(label = "coll")
                val f1 by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(70000, easing = LinearEasing)), label = "c1")
                val f2 by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(85000, easing = LinearEasing)), label = "c2")
                Column(
                    Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(.7f).graphicsLayer { rotationZ = -9f; scaleX = 1.3f; scaleY = 1.3f; translationX = 40f }
                        .wrapContentSize(Alignment.CenterStart, unbounded = true),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(Modifier.graphicsLayer { translationX = -f1 * setPx }, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val l = fill(r1); (l + l).forEach { t -> Img(t.poster, Modifier.width(70.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(7.dp)), crossfade = false) }
                    }
                    Row(Modifier.padding(start = 36.dp).graphicsLayer { translationX = -(1f - f2) * setPx }, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val l = fill(r2); (l + l).forEach { t -> Img(t.poster, Modifier.width(70.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(7.dp)), crossfade = false) }
                    }
                }
            }
            // difuminado del lado izquierdo, donde va el texto
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to bg, .30f to bg.copy(alpha = .96f), .46f to bg.copy(alpha = .78f), .62f to bg.copy(alpha = .38f), .80f to bg.copy(alpha = .08f), 1f to Color.Transparent)))
            Column(Modifier.align(Alignment.CenterStart).padding(start = 20.dp).fillMaxWidth(.56f)) {
                Text("COLECCIÓN", style = ts(9.5f, FontWeight.Bold, C.accent).copy(letterSpacing = 2.sp))
                Text(c.name, style = ts(21, FontWeight.Bold, Color.White), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                if (c.tagline.isNotBlank()) Text(c.tagline, style = ts(11, color = Color.White.copy(alpha = .75f)), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
                Text(
                    "Ver colección", style = ts(11.5f, FontWeight.Bold, Color.Black),
                    modifier = Modifier.padding(top = 10.dp).background(Color.White, RoundedCornerShape(99.dp)).padding(horizontal = 14.dp, vertical = 7.dp)
                )
            }
        }
    }
}

/* ---------------- estados vacíos ---------------- */

@Composable
fun Loading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LocalLogo(Modifier.width(240.dp).height(40.dp)) }
}

@Composable
fun Empty(msg: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(if (Store.offline) "Sin conexión a internet. Revisá tu red." else msg, style = ts(17, color = C.fg2).copy(textAlign = TextAlign.Center))
    }
}
