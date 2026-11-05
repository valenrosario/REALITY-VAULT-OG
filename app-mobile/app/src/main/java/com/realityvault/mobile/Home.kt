package com.realityvault.mobile

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

fun slides(): List<Slide> {
    // Mismo orden "al azar pero fijo durante la sesión" que usa el sitio web (ver Store.bannerSeed).
    val seededRandom = kotlin.random.Random(Store.bannerSeed)
    val fromBanners = Store.banners.shuffled(seededRandom).mapNotNull { b: Banner ->
        val t = Store.byId(b.titleId)?.takeIf { it.status != "borrador" } ?: return@mapNotNull null
        // en el celular: primero la imagen vertical pensada para celular (como la web)
        val img = b.imageMobile.ifBlank { t.bannerMobile.ifBlank { b.image.ifBlank { t.bannerImage.ifBlank { t.backdrop.ifBlank { t.poster } } } } }
        Slide(t, img, b.headline, b.text.ifBlank { t.tagline.ifBlank { t.description } })
    }
    if (fromBanners.isNotEmpty()) return fromBanners
    return Store.published().filter { it.featured }.shuffled(seededRandom).take(6).map {
        Slide(it, it.bannerMobile.ifBlank { it.bannerImage.ifBlank { it.backdrop.ifBlank { it.poster } } }, "", it.tagline.ifBlank { it.description })
    }
}

class RowSpec(val key: String, val title: String, val kind: String, val titles: List<Title>, val feat: List<Pair<Title, FeatItem>> = emptyList(), val coll: Coll? = null)

fun homeRows(): List<RowSpec> {
    val pub = Store.published()
    val rows = mutableListOf<RowSpec>()
    val cont = Store.progress.mapNotNull { p -> Store.byId(p.titleId)?.takeIf { it.status != "borrador" } }
    if (cont.isNotEmpty()) rows += RowSpec("cont", "Seguir viendo", "wide", cont)
    val top = Store.top10.mapNotNull { id -> pub.firstOrNull { it.id == id } }.take(10)
    if (top.isNotEmpty()) rows += RowSpec("top", "Top 10 hoy", "top", top)
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
    val gap = maxOf(1, rest.size / (colls.size + 1)); var ci = 0
    rest.forEachIndexed { i, r -> rows += r; if (ci < colls.size && (i + 1) % gap == 0) rows += colls[ci++] }
    while (ci < colls.size) rows += colls[ci++]
    return rows
}

/* ---------------- pantalla de inicio ---------------- */

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(nav: Nav) {
    if (!Store.loaded) { Loading(); return }
    val all = slides()
    val rows = homeRows()
    if (all.isEmpty() && rows.isEmpty()) { Empty("Todavía no hay títulos publicados."); return }
    val listState = nav.memory.list("home")
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottom + 96.dp)) {
            item(key = "hero") { if (all.isNotEmpty()) Hero(all, nav) else Spacer(Modifier.height(90.dp)) }
            items(rows, key = { it.key }) { r -> RowSection(r, nav) }
        }
        // logo y foto de perfil flotando arriba; se oscurece un poco para que se lean sobre la portada
        Row(
            Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .6f), Color.Transparent)))
                .statusBarsPadding().padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LocalLogo(Modifier.height(26.dp).width(160.dp))
            Spacer(Modifier.weight(1f))
            AvatarButton(nav)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Hero(all: List<Slide>, nav: Nav) {
    val cfg = LocalConfiguration.current
    val h = minOf(cfg.screenWidthDp * 1.28f, cfg.screenHeightDp * 0.74f).dp
    val pager = rememberPagerState { all.size }
    val scope = rememberCoroutineScope()
    // pasa solo cada 7 s; si la persona lo está arrastrando, espera
    LaunchedEffect(pager.currentPage, pager.isScrollInProgress, all.size) {
        if (all.size > 1 && !pager.isScrollInProgress) { delay(7000); pager.animateScrollToPage((pager.currentPage + 1) % all.size) }
    }
    Box(Modifier.fillMaxWidth().height(h)) {
        HorizontalPager(pager, Modifier.fillMaxSize(), key = { it }) { i ->
            val slide = all[i]
            val t = slide.t
            val target = Store.playTarget(t)
            Box(Modifier.fillMaxSize().tap({ nav.go(Screen.Detail(t.id)) }, .99f)) {
                if (slide.image.isNotBlank()) Img(slide.image, Modifier.fillMaxSize(), alignment = Alignment.TopCenter, crossfade = false) else Placeholder(t, Modifier.fillMaxSize(), big = true)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, .5f to Color.Transparent, .8f to Color.Black.copy(alpha = .62f), 1f to Color.Black)))
                Column(Modifier.align(Alignment.BottomCenter).padding(start = 22.dp, end = 22.dp, bottom = 34.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (slide.headline.isNotBlank()) Text(slide.headline, style = ts(30, FontWeight.Bold, Color.White).copy(textAlign = TextAlign.Center, lineHeight = 32.sp), maxLines = 2)
                    else TitleLogo(t, 250.dp, 84.dp, 30)
                    Spacer(Modifier.height(10.dp))
                    KindLine(t, center = true)
                    if (slide.text.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(slide.text, style = ts(13, FontWeight.Light, Color.White.copy(alpha = .85f)).copy(textAlign = TextAlign.Center, lineHeight = 18.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        when {
                            t.isSoon -> PillButton(if (t.releaseDate.isNotBlank()) "Próximamente · ${t.releaseDate}" else "Próximamente", { nav.go(Screen.Detail(t.id)) }, icon = null)
                            target != null -> PillButton(if (target.cont) target.label else "Reproducir", { nav.play(t, target.url, target.season, target.episode) })
                            else -> PillButton("Ver detalles", { nav.go(Screen.Detail(t.id)) }, icon = null)
                        }
                        ListToggle(t, nav)
                    }
                }
            }
        }
        if (all.size > 1) Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            all.indices.forEach { i ->
                val on = i == pager.currentPage
                val sz by animateFloatAsState(if (on) 8f else 6f, tween(220), label = "dot")
                Box(Modifier.size(sz.dp).background(if (on) C.accent else Color.White.copy(alpha = .4f), CircleShape))
            }
        }
    }
}

/** Botón redondo de agregar / sacar de Mi lista (pide cuenta si no entró). */
@Composable
fun ListToggle(t: Title, nav: Nav, size: androidx.compose.ui.unit.Dp = 46.dp) {
    val scope = rememberCoroutineScope()
    GlassButton(if (t.id in Store.myList) Ic.check else Ic.plus, if (t.id in Store.myList) "Quitar de Mi lista" else "Agregar a Mi lista", {
        if (Store.session == null) nav.go(Screen.Login()) else scope.launch { Store.toggleList(t.id) }
    }, size = size)
}

/* ---------------- filas ---------------- */

@Composable
private fun RowSection(r: RowSpec, nav: Nav) {
    if (r.kind == "coll") { CollBanner(r, nav); return }
    Column(Modifier.padding(top = 22.dp)) {
        Text(r.title, style = ts(15, FontWeight.Bold, Color.White.copy(alpha = .94f)), modifier = Modifier.padding(start = 18.dp, bottom = 10.dp))
        LazyRow(
            state = nav.memory.list("row-" + r.key),
            contentPadding = PaddingValues(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(if (r.kind == "top") 6.dp else 11.dp)
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
                    else -> PosterCard(t, Modifier.width(116.dp).aspectRatio(2f / 3f).tap({ nav.go(Screen.Detail(t.id)) }).clip(RoundedCornerShape(9.dp)))
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
