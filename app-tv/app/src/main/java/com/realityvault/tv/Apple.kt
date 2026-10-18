package com.realityvault.tv

import android.graphics.drawable.BitmapDrawable
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/* ======================================================================
   Diseño estilo Apple TV
   ====================================================================== */

@Composable
fun App(nav: Nav, onExit: () -> Unit) {
    BackHandler {
        val top = nav.top
        if (top is Screen.Login && top.required) {
            // es la puerta de entrada: no hay a dónde volver, salir de la app
            onExit()
        } else if (!nav.back()) {
            if (nav.top != Screen.Home) nav.section(Screen.Home) else onExit()
        }
    }
    Box(Modifier.fillMaxSize().background(C.bg)) {
        when (val s = nav.top) {
            Screen.Splash -> SplashScreen(nav)
            is Screen.Player -> PlayerScreen(s, nav)
            is Screen.Detail -> DetailScreen(s.id, nav)
            is Screen.Login -> LoginScreen(s, nav)
            is Screen.Person -> PersonScreen(s.name, nav)
            else -> Box(Modifier.fillMaxSize()) {
                when (s) {
                    Screen.Home -> HomeScreen(nav)
                    Screen.Series -> GridScreen(nav, "series")
                    Screen.Movies -> GridScreen(nav, "peliculas")
                    Screen.MyList -> GridScreen(nav, "milista")
                    Screen.Search -> SearchScreen(nav)
                    Screen.Account -> AccountScreen(nav)
                    else -> {}
                }
                TopTabs(nav, Modifier.align(Alignment.TopStart))
            }
        }
    }
}

/* ---------------- menú de arriba (pastilla "‹ Inicio" que se abre) ---------------- */

@Composable
fun TopTabs(nav: Nav, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    var entered by remember { mutableStateOf(false) }
    val items = listOf(
        Triple(Screen.Search, Ic.search, "Buscar"), Triple(Screen.Home, Ic.home, "Inicio"),
        Triple(Screen.Series, Ic.tv, "Series"), Triple(Screen.Movies, Ic.film, "Películas"),
        Triple(Screen.MyList, Ic.plus, "Mi lista"),
        Triple(Screen.Account, Ic.user, if (Store.session != null) "Mi cuenta" else "Entrar")
    )
    val cur = items.firstOrNull { it.first == nav.top } ?: items[1]
    val curReq = remember { FocusRequester() }
    Box(modifier.padding(start = 44.dp, top = 24.dp)) {
        if (!open) {
            Focusable({ open = true }, Modifier.height(30.dp), shape = RoundedCornerShape(99.dp), scale = 1.05f, ring = false, onFocus = { open = true }) { f ->
                Row(
                    Modifier.background(if (f) Color.White else Color(0x8C26262C)).padding(start = 8.dp, end = 13.dp).height(30.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Ic.back, null, tint = (if (f) Color.Black else Color.White).copy(alpha = .6f), modifier = Modifier.size(11.dp))
                    Spacer(Modifier.width(5.dp))
                    Box(Modifier.size(21.dp).background((if (f) Color.Black else Color.White).copy(alpha = .16f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(cur.second, null, tint = if (f) Color.Black else Color.White, modifier = Modifier.size(12.dp))
                    }
                    Spacer(Modifier.width(7.dp))
                    Text(cur.third, style = ts(12, FontWeight.Bold, if (f) Color.Black else Color.White))
                }
            }
        } else {
            Row(
                Modifier.background(Color(0xCC1E1E24), RoundedCornerShape(99.dp)).padding(3.dp)
                    .onFocusChanged { if (it.hasFocus) entered = true else if (entered) { open = false; entered = false } },
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items.forEach { (scr, ic, label) ->
                    val sel = scr == cur.first
                    Focusable({ if (nav.top != scr) nav.section(scr) }, Modifier.height(28.dp), shape = RoundedCornerShape(99.dp), scale = 1.04f, ring = false, requester = if (sel) curReq else null) { f ->
                        Row(
                            Modifier.background(if (f) Color.White else if (sel) Color.White.copy(alpha = .16f) else Color.Transparent).padding(horizontal = 11.dp).height(28.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(ic, null, tint = if (f) Color.Black else Color.White, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(label, style = ts(12, FontWeight.Bold, if (f) Color.Black else Color.White))
                        }
                    }
                }
            }
            AutoFocus(curReq, "tabs")
        }
    }
}

/* ---------------- desplazamiento: fila enfocada arriba ---------------- */

@OptIn(ExperimentalFoundationApi::class)
class PivotSpec(private val pivotPx: Float, private val atTop: () -> Boolean) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
        if (atTop()) 0f else offset - pivotPx
}

/* ---------------- color de fondo sacado de la imagen ---------------- */

private val tintCache = HashMap<String, Color>()

@Composable
fun rememberTint(url: String): Color {
    val ctx = LocalContext.current
    var c by remember(url) { mutableStateOf(tintCache[url] ?: Color(0xFF1C1A24)) }
    LaunchedEffect(url) {
        if (url.isBlank() || tintCache.containsKey(url)) return@LaunchedEffect
        val r = runCatching { ctx.imageLoader.execute(ImageRequest.Builder(ctx).data(url).size(48).allowHardware(false).build()) }.getOrNull()
        val bmp = (r?.drawable as? BitmapDrawable)?.bitmap ?: return@LaunchedEffect
        val w = bmp.width; val h = bmp.height
        val px = IntArray(w * h); bmp.getPixels(px, 0, w, 0, 0, w, h)
        val hist = FloatArray(36); val hsv = FloatArray(3)
        px.forEach { p -> android.graphics.Color.colorToHSV(p, hsv); if (hsv[2] > .2f) hist[(hsv[0] / 10).toInt().coerceIn(0, 35)] += hsv[1] * hsv[1] }
        val best = hist.indices.maxByOrNull { hist[it] } ?: 0
        val sat = if (hist[best] < 0.5f) .12f else .55f
        val col = Color.hsl(best * 10f + 5f, sat, .30f)
        tintCache[url] = col; c = col
    }
    return c
}

/* ---------------- inicio ---------------- */

data class Slide(val t: Title, val image: String, val headline: String, val text: String)

fun slides(): List<Slide> {
    val fromBanners = Store.banners.mapNotNull { b ->
        val t = Store.byId(b.titleId)?.takeIf { it.status != "borrador" } ?: return@mapNotNull null
        Slide(t, t.bannerTv.ifBlank { t.backdropTv.ifBlank { t.backdrop.ifBlank { b.image.ifBlank { t.tvHero } } } }, b.headline, b.text.ifBlank { t.tagline.ifBlank { t.description } })
    }
    if (fromBanners.isNotEmpty()) return fromBanners
    return Store.published().filter { it.featured }.take(6).map { Slide(it, it.tvHero, "", it.tagline.ifBlank { it.description }) }
}

class RowSpec(val key: String, val title: String, val kind: String, val titles: List<Title>, val feat: List<Pair<Title, FeatItem>> = emptyList())

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
    val feat = pub.filter { it.featured }
    if (feat.isNotEmpty()) rows += RowSpec("feat", "Destacados", "poster", feat)
    val newest = pub.sortedByDescending { it.createdAt }.take(20)
    if (newest.isNotEmpty()) rows += RowSpec("new", "Novedades", "poster", newest)
    val series = pub.filter { it.isSerie }; if (series.isNotEmpty()) rows += RowSpec("series", "Series", "poster", series)
    val movies = pub.filter { !it.isSerie }; if (movies.isNotEmpty()) rows += RowSpec("movies", "Películas", "poster", movies)
    pub.flatMap { t -> t.genres.map { it to t } }.groupBy({ it.first }, { it.second })
        .entries.sortedByDescending { it.value.size }.take(8)
        .forEach { (g, l) -> rows += RowSpec("g-$g", g, "poster", l) }
    return rows
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(nav: Nav) {
    val all = slides()
    val rows = homeRows()
    val mem = nav.memory
    val listState = mem.list("home")
    var heroIdx by remember { mutableIntStateOf(0) }
    var heroFocused by remember { mutableStateOf(mem.focus["home"] == null) }
    val heroReq = remember { FocusRequester() }
    val density = LocalDensity.current
    val defaultSpec = LocalBringIntoViewSpec.current
    val spec = remember { PivotSpec(with(density) { 66.dp.toPx() }) { heroFocused } }
    val screenH = LocalConfiguration.current.screenHeightDp.dp

    LaunchedEffect(heroIdx, heroFocused, all.size) {
        if (all.size > 1 && !heroFocused) { delay(9000); heroIdx = (heroIdx + 1) % all.size }
    }
    LaunchedEffect(heroFocused) { if (heroFocused && (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0)) listState.animateScrollToItem(0) }
    if (mem.focus["home"] == null) AutoFocus(heroReq, "hero")

    if (!Store.loaded) { Loading(); return }
    if (all.isEmpty() && rows.isEmpty()) { Empty("Todavía no hay títulos publicados."); return }

    CompositionLocalProvider(LocalBringIntoViewSpec provides spec) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 220.dp)) {
            item(key = "hero") {
                if (all.isNotEmpty()) HeroA(all, heroIdx % all.size, screenH - 74.dp, nav, heroReq, { heroIdx = (heroIdx + 1) % all.size }) { heroFocused = it }
                else Spacer(Modifier.height(80.dp))
            }
            items(rows, key = { it.key }) { r ->
                CompositionLocalProvider(LocalBringIntoViewSpec provides defaultSpec) {
                    RowA(r, nav, "home", mem.list("row-" + r.key))
                }
            }
        }
    }
}

@Composable
fun HeroA(all: List<Slide>, idx: Int, height: Dp, nav: Nav, req: FocusRequester, next: () -> Unit, onFocus: (Boolean) -> Unit) {
    val slide = all[idx]
    val t = slide.t
    val target = Store.playTarget(t)
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxWidth().height(height).onFocusChanged { onFocus(it.hasFocus) }) {
        Crossfade(slide.image, animationSpec = tween(450), label = "hero") { u ->
            Box(Modifier.fillMaxSize()) { if (u.isNotBlank()) Img(u, Modifier.fillMaxSize(), alignment = Alignment.TopCenter, crossfade = false) }
        }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color.Black.copy(alpha = .75f), .38f to Color.Black.copy(alpha = .35f), .6f to Color.Transparent)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, .5f to Color.Transparent, .8f to Color.Black.copy(alpha = .45f), 1f to Color.Black)))
        Column(Modifier.align(Alignment.BottomStart).padding(start = 48.dp, bottom = 46.dp).width(380.dp)) {
            if (slide.headline.isNotBlank()) Text(slide.headline, style = ts(28, FontWeight.Bold, Color.White), maxLines = 2)
            else TitleLogo(t, 230.dp, 76.dp, 28)
            Spacer(Modifier.height(10.dp))
            KindLine(t)
            if (slide.text.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(slide.text, style = ts(12, FontWeight.Light, Color.White.copy(alpha = .8f)), maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 16.sp)
            }
            Spacer(Modifier.height(15.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                when {
                    t.isSoon -> PlayPill(if (t.releaseDate.isNotBlank()) "Próximamente · ${t.releaseDate}" else "Próximamente", null, req) { nav.go(Screen.Detail(t.id)) }
                    target != null -> PlayPill(if (target.cont) target.label else "Reproducir", Ic.play, req) { nav.play(t, target.url, target.season, target.episode) }
                    else -> PlayPill("Ver detalles", null, req) { nav.go(Screen.Detail(t.id)) }
                }
                GlassButton(if (t.id in Store.myList) Ic.check else Ic.plus) {
                    if (Store.session == null) nav.go(Screen.Login()) else scope.launch { Store.toggleList(t.id) }
                }
                GlassButton(Ic.info) { nav.go(Screen.Detail(t.id)) }
                if (all.size > 1) Focusable(next, Modifier.size(26.dp, 31.dp), shape = RoundedCornerShape(8.dp), scale = 1.15f, ring = false) { f ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Ic.chevron, "Siguiente", tint = if (f) Color.White else Color.White.copy(alpha = .8f), modifier = Modifier.size(17.dp)) }
                }
            }
        }
        if (all.size > 1) Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp).background(Color(0x7326262C), RoundedCornerShape(99.dp)).padding(horizontal = 7.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            all.indices.forEach { i ->
                Box(Modifier.height(4.dp).width(if (i == idx) 11.dp else 4.dp).background(if (i == idx) Color.White else Color.White.copy(alpha = .45f), RoundedCornerShape(9.dp)))
            }
        }
    }
}

@Composable
fun KindLine(t: Title) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text((listOf(t.kind()) + t.genres.take(2)).joinToString(" · "), style = ts(12, color = Color.White.copy(alpha = .88f)), maxLines = 1)
        if (t.rating.isNotBlank()) { Spacer(Modifier.width(7.dp)); RatingBox(t.rating) }
    }
}

@Composable
fun RatingBox(r: String) {
    Box(Modifier.border(1.dp, Color.White.copy(alpha = .75f), RoundedCornerShape(2.dp)).padding(horizontal = 3.dp)) {
        Text(r, style = ts(8, FontWeight.Bold, Color.White))
    }
}

@Composable
fun PlayPill(text: String, icon: ImageVector?, req: FocusRequester? = null, onClick: () -> Unit) {
    Focusable(onClick, Modifier.height(33.dp), shape = RoundedCornerShape(99.dp), scale = 1.07f, ring = false, lift = true, requester = req) {
        Row(Modifier.background(Color.White).padding(horizontal = 22.dp).height(33.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(icon, null, tint = Color.Black, modifier = Modifier.size(11.dp)); Spacer(Modifier.width(6.dp)) }
            Text(text, style = ts(13, FontWeight.Bold, Color.Black), maxLines = 1)
        }
    }
}

@Composable
fun GlassButton(icon: ImageVector, onClick: () -> Unit) {
    Focusable(onClick, Modifier.size(31.dp), shape = CircleShape, scale = 1.12f, ring = false, lift = true) { f ->
        Box(Modifier.fillMaxSize().background(if (f) Color.White else Color(0x9C3C3C42)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = if (f) Color.Black else Color.White, modifier = Modifier.size(14.dp))
        }
    }
}

/* ---------------- filas ---------------- */

@Composable
fun RowA(r: RowSpec, nav: Nav, screenKey: String, state: LazyListState) {
    val restore = nav.memory.focus[screenKey]
    Column(Modifier.padding(top = 4.dp)) {
        Text(r.title, style = ts(13, FontWeight.Bold, Color.White.copy(alpha = .92f)), modifier = Modifier.padding(start = 48.dp))
        LazyRow(
            state = state,
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(if (r.kind == "top") 4.dp else 15.dp)
        ) {
            if (r.kind == "feat") {
                itemsIndexed(r.feat, key = { i, _ -> "f$i" }) { i, (t, item) ->
                    CardA("${r.key}/$i", restore, nav, screenKey, { nav.go(Screen.Detail(t.id)) }, Modifier.width(112.dp).aspectRatio(3f / 4.45f)) { FeatCard(t, item) }
                }
            } else itemsIndexed(r.titles, key = { _, t -> t.id }) { i, t ->
                val k = "${r.key}/${t.id}"
                when (r.kind) {
                    "wide" -> {
                        val p = if (r.key == "cont") Store.progress.firstOrNull { it.titleId == t.id } else null
                        CardA(k, restore, nav, screenKey, {
                            val tg = Store.playTarget(t)
                            if (r.key == "cont" && tg != null) nav.play(t, tg.url, tg.season, tg.episode) else nav.go(Screen.Detail(t.id))
                        }, Modifier.width(189.dp).aspectRatio(16f / 9f)) { WideCard(t, if (p?.season != null) "T${p.season}:E${p.episode}" else null) }
                    }
                    "top" -> Row(verticalAlignment = Alignment.Bottom) {
                        Text("${i + 1}", modifier = Modifier.offset(x = 8.dp, y = 8.dp), style = ts(86, FontWeight.Bold, C.accent).copy(drawStyle = Stroke(width = 4f)))
                        CardA(k, restore, nav, screenKey, { nav.go(Screen.Detail(t.id)) }, Modifier.width(96.dp).aspectRatio(2f / 3f)) { PosterCard(t) }
                    }
                    else -> CardA(k, restore, nav, screenKey, { nav.go(Screen.Detail(t.id)) }, Modifier.width(96.dp).aspectRatio(2f / 3f)) { PosterCard(t) }
                }
            }
        }
    }
}

@Composable
fun CardA(key: String, restore: String?, nav: Nav, screenKey: String, onClick: () -> Unit, modifier: Modifier, content: @Composable () -> Unit) {
    val req = remember { FocusRequester() }
    if (key == restore) AutoFocus(req, key)
    Focusable(onClick, modifier, shape = RoundedCornerShape(9.dp), scale = 1.1f, ring = false, lift = true, requester = req, onFocus = { nav.memory.focus[screenKey] = key }) { content() }
}

@Composable
fun PosterCard(t: Title) {
    Box(Modifier.fillMaxSize().background(C.surface)) {
        if (t.poster.isNotBlank()) Img(t.poster, Modifier.fillMaxSize()) else Placeholder(t, Modifier.fillMaxSize())
        if (t.isSoon) Text(
            "Próximamente", style = ts(8, FontWeight.Bold, Color.Black),
            modifier = Modifier.align(Alignment.TopStart).padding(6.dp).background(C.accent, RoundedCornerShape(99.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun WideCard(t: Title, label: String?) {
    Box(Modifier.fillMaxSize().background(C.surface)) {
        if (t.wide.isNotBlank()) Img(t.wide, Modifier.fillMaxSize()) else Placeholder(t, Modifier.fillMaxSize())
        if (label != null) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, .55f to Color.Transparent, 1f to Color.Black.copy(alpha = .7f))))
            Text(label, style = ts(10, FontWeight.Bold, Color.White), modifier = Modifier.align(Alignment.BottomStart).padding(start = 9.dp, bottom = 14.dp))
            Box(Modifier.align(Alignment.BottomStart).padding(start = 9.dp, end = 9.dp, bottom = 7.dp).fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = .35f), RoundedCornerShape(9.dp))) {
                Box(Modifier.fillMaxWidth(.45f).height(3.dp).background(Color.White, RoundedCornerShape(9.dp)))
            }
        }
    }
}

@Composable
fun FeatCard(t: Title, item: FeatItem) {
    val img = item.image.ifBlank { t.wide }
    Box(Modifier.fillMaxSize().background(C.surface)) {
        if (img.isNotBlank()) Img(img, Modifier.fillMaxSize()) else Placeholder(t, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, .45f to Color.Transparent, 1f to Color.Black)))
        Column(Modifier.align(Alignment.BottomCenter).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (t.logo.isNotBlank()) Img(t.logo, Modifier.fillMaxWidth(.8f).height(32.dp), scale = ContentScale.Fit, alignment = Alignment.BottomCenter)
            else Text(t.title, style = ts(12, FontWeight.Bold, Color.White), maxLines = 2)
            if (item.label.isNotBlank()) { Spacer(Modifier.height(4.dp)); Text(item.label, style = ts(9, FontWeight.Bold, C.fg2), maxLines = 1) }
        }
    }
}

/* ---------------- ficha de la serie / película ---------------- */

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DetailScreen(id: String, nav: Nav) {
    val t = Store.byId(id)
    if (t == null) { Empty("Este título ya no está disponible."); return }
    val scope = rememberCoroutineScope()
    LaunchedEffect(id) { Store.trackView(id) }
    val target = Store.playTarget(t)
    val playReq = remember { FocusRequester() }
    var season by remember(id) {
        val p = Store.progress.firstOrNull { it.titleId == id }
        mutableIntStateOf(t.seasons.indexOfFirst { it.number == p?.season }.coerceAtLeast(0))
    }
    AutoFocus(playReq, id)
    val prog = Store.progress.firstOrNull { it.titleId == id }
    val tint = rememberTint(t.tvBg)
    val listState = rememberLazyListState()
    var heroFocused by remember { mutableStateOf(true) }
    val density = LocalDensity.current
    val spec = remember { PivotSpec(with(density) { 112.dp.toPx() }) { heroFocused } }
    val defaultSpec = LocalBringIntoViewSpec.current
    LaunchedEffect(heroFocused) { if (heroFocused && (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0)) listState.animateScrollToItem(0) }
    val screenH = LocalConfiguration.current.screenHeightDp.dp
    val related = remember(id, Store.titles) {
        Store.published().filter { o -> o.id != t.id && o.genres.any { it in t.genres } }
            .sortedByDescending { o -> o.genres.count { it in t.genres } }.take(14)
    }
    val bgCol = Brush.radialGradient(listOf(tint.copy(alpha = 1f).lighten(.10f), tint, tint.darken(.45f)), radius = 1600f)

    Box(Modifier.fillMaxSize().background(bgCol)) {
        CompositionLocalProvider(LocalBringIntoViewSpec provides spec) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 240.dp)) {
                // portada a pantalla completa
                item(key = "hero") {
                    Box(Modifier.fillMaxWidth().height(screenH - 30.dp).onFocusChanged { heroFocused = it.hasFocus }) {
                        if (t.tvBg.isNotBlank()) Img(t.tvBg, Modifier.fillMaxSize(), alignment = Alignment.TopCenter)
                        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color.Black.copy(alpha = .8f), .36f to Color.Black.copy(alpha = .45f), .58f to Color.Transparent)))
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, .55f to Color.Transparent, 1f to tint)))
                        Column(Modifier.align(Alignment.BottomStart).padding(start = 48.dp, bottom = 70.dp).width(380.dp)) {
                            TitleLogo(t, 230.dp, 76.dp, 28)
                            Spacer(Modifier.height(10.dp))
                            KindLine(t)
                            if (t.description.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text(t.description, style = ts(12, FontWeight.Light, Color.White.copy(alpha = .8f)), maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 16.sp)
                            }
                            val facts = listOfNotNull(
                                t.year.takeIf { it.isNotBlank() }?.let { if (t.yearEnd.isNotBlank()) "$it – ${t.yearEnd}" else it },
                                if (t.isSerie && t.seasons.isNotEmpty()) "${t.seasons.size} temporada" + (if (t.seasons.size == 1) "" else "s") else t.duration.ifBlank { null }
                            ).joinToString(" · ")
                            if (facts.isNotBlank()) {
                                Spacer(Modifier.height(10.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(facts, style = ts(11, color = Color.White.copy(alpha = .85f)))
                                    if (t.rating.isNotBlank()) { Spacer(Modifier.width(7.dp)); RatingBox(t.rating) }
                                }
                            }
                            Spacer(Modifier.height(15.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                when {
                                    t.isSoon -> PlayPill(if (t.releaseDate.isNotBlank()) "Próximamente · ${t.releaseDate}" else "Próximamente", null, playReq) {}
                                    target != null -> PlayPill(target.label, Ic.play, playReq) { nav.play(t, target.url, target.season, target.episode) }
                                    else -> PlayPill("No disponible por el momento", null, playReq) {}
                                }
                                GlassButton(if (t.id in Store.myList) Ic.check else Ic.plus) {
                                    if (Store.session == null) nav.go(Screen.Login()) else scope.launch { Store.toggleList(t.id) }
                                }
                                if (mediaFor(t.trailer) != null) GlassButton(Ic.clapper) { nav.go(Screen.Player(t.id, t.trailer, null, null, trailer = true)) }
                            }
                        }
                        if (t.cast.isNotEmpty()) Text(
                            "Reparto  " + t.cast.take(4).joinToString(", ") { it.name },
                            style = ts(10.5f, color = Color.White.copy(alpha = .8f)), maxLines = 2, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 48.dp, bottom = 78.dp).width(230.dp)
                        )
                    }
                }
                // capítulos
                if (t.isSerie && t.seasons.isNotEmpty()) {
                    item(key = "logo") {
                        Box(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 6.dp), contentAlignment = Alignment.Center) {
                            if (t.logo.isNotBlank()) Img(t.logo, Modifier.height(58.dp).width(260.dp), scale = ContentScale.Fit)
                            else Text(t.title, style = ts(26, FontWeight.Bold, Color.White))
                        }
                    }
                    item(key = "eps") {
                        CompositionLocalProvider(LocalBringIntoViewSpec provides defaultSpec) {
                            val s = t.seasons.getOrNull(season) ?: t.seasons.first()
                            Column {
                                if (t.seasons.size > 1) {
                                    LazyRow(contentPadding = PaddingValues(horizontal = 48.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        itemsIndexed(t.seasons) { i, ss -> Chip(ss.name.ifBlank { "Temporada ${ss.number}" }, i == season) { season = i } }
                                    }
                                } else SectionTitle(s.name.ifBlank { "Temporada ${s.number}" })
                                LazyRow(contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 10.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(17.dp)) {
                                    items(s.episodes, key = { "${s.number}-${it.number}" }) { e ->
                                        EpisodeA(t, s, e, prog) { if (!t.isSoon && isHttp(e.url)) nav.play(t, e.url, s.number, e.number) }
                                    }
                                }
                            }
                        }
                    }
                }
                // también te podría gustar
                if (related.isNotEmpty()) item(key = "rel") {
                    CompositionLocalProvider(LocalBringIntoViewSpec provides defaultSpec) {
                        Column(Modifier.padding(top = 14.dp)) {
                            SectionTitle("También te podría gustar")
                            LazyRow(contentPadding = PaddingValues(horizontal = 48.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                items(related, key = { it.id }) { o ->
                                    Focusable({ nav.go(Screen.Detail(o.id)) }, Modifier.width(110.dp).aspectRatio(2f / 3f), shape = RoundedCornerShape(9.dp), scale = 1.08f, ring = false, lift = true) { PosterCard(o) }
                                }
                            }
                        }
                    }
                }
                // reparto
                if (t.cast.isNotEmpty()) item(key = "cast") {
                    CompositionLocalProvider(LocalBringIntoViewSpec provides defaultSpec) {
                        Column(Modifier.padding(top = 14.dp)) {
                            SectionTitle("Reparto")
                            LazyRow(contentPadding = PaddingValues(horizontal = 48.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                items(t.cast) { c -> CastA(c) { nav.go(Screen.Person(c.name)) } }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, style = ts(13, FontWeight.Bold, Color.White.copy(alpha = .92f)), modifier = Modifier.padding(start = 48.dp, top = 4.dp))
}

@Composable
fun EpisodeA(t: Title, s: Season, e: Episode, prog: Progress?, onClick: () -> Unit) {
    val seen = prog?.watched?.contains("${s.number}:${e.number}") == true
    val current = prog?.season == s.number && prog?.episode == e.number
    val hasVideo = isHttp(e.url)
    Focusable(onClick, Modifier.width(210.dp), shape = RoundedCornerShape(10.dp), scale = 1f, ring = false, clipContent = false) { f ->
        val sc by animateFloatAsState(if (f) 1.05f else 1f, tween(160), label = "ep")
        Column {
            Box(
                Modifier.fillMaxWidth().aspectRatio(16f / 9f).graphicsLayer { scaleX = sc; scaleY = sc }
                    .then(if (f) Modifier.shadow(12.dp, RoundedCornerShape(10.dp)) else Modifier)
                    .clip(RoundedCornerShape(10.dp)).background(C.surface)
            ) {
                val img = e.thumb.ifBlank { t.wide }
                if (img.isNotBlank()) Img(img, Modifier.fillMaxSize()) else Placeholder(t, Modifier.fillMaxSize())
                if (!hasVideo) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .3f)))
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, .6f to Color.Transparent, 1f to Color.Black.copy(alpha = .55f))))
                Row(Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (hasVideo) { Icon(Ic.play, null, tint = Color.White, modifier = Modifier.size(9.dp)); Spacer(Modifier.width(4.dp)) }
                    Text(if (!hasVideo) "Sin video" else e.duration.ifBlank { "Ver" }, style = ts(10.5f, FontWeight.Bold, Color.White))
                }
                if (seen || current) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(C.accent))
            }
            Column(
                Modifier.padding(top = 6.dp).fillMaxWidth()
                    .background(if (f) Color.White.copy(alpha = .14f) else Color.Transparent, RoundedCornerShape(8.dp))
                    .padding(horizontal = 9.dp, vertical = 8.dp)
            ) {
                Text("EPISODIO ${e.number}", style = ts(8.5f, color = Color.White.copy(alpha = .6f)).copy(letterSpacing = .6.sp))
                Spacer(Modifier.height(2.dp))
                Text(e.title.ifBlank { "Episodio ${e.number}" }, style = ts(12, FontWeight.Bold, Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (e.description.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(e.description, style = ts(10, FontWeight.Light, Color.White.copy(alpha = .75f)), maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 13.sp)
                }
            }
        }
    }
}

@Composable
fun CastA(c: CastMember, onClick: () -> Unit) {
    Focusable(onClick, Modifier.width(86.dp), shape = RoundedCornerShape(10.dp), scale = 1f, ring = false, clipContent = false) { f ->
        val sc by animateFloatAsState(if (f) 1.08f else 1f, tween(160), label = "c")
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Box(
                Modifier.size(80.dp).graphicsLayer { scaleX = sc; scaleY = sc }
                    .then(if (f) Modifier.shadow(12.dp, CircleShape) else Modifier)
                    .clip(CircleShape).background(Color.White.copy(alpha = .14f)),
                contentAlignment = Alignment.Center
            ) {
                if (c.photo.isNotBlank()) Img(c.photo, Modifier.fillMaxSize())
                else Text(c.name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }, style = ts(24, FontWeight.Bold, Color.White.copy(alpha = .75f)))
            }
            Spacer(Modifier.height(7.dp))
            Text(c.name, style = ts(10, color = if (f) Color.White else Color.White.copy(alpha = .85f)), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            if (c.role.isNotBlank()) Text(c.role, style = ts(8.5f, color = Color.White.copy(alpha = .55f)), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
    }
}

/* ---------------- títulos de una persona del reparto ---------------- */

@Composable
fun PersonScreen(name: String, nav: Nav) {
    val list = Store.published().filter { t -> t.cast.any { it.name.equals(name, true) } }
    val photo = Store.published().flatMap { it.cast }.firstOrNull { it.name.equals(name, true) && it.photo.isNotBlank() }?.photo ?: ""
    val firstReq = remember { FocusRequester() }
    AutoFocus(firstReq, name)
    Column(Modifier.fillMaxSize().padding(start = 48.dp, top = 40.dp, end = 48.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(Color.White.copy(alpha = .14f))) { if (photo.isNotBlank()) Img(photo, Modifier.fillMaxSize()) }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(name, style = ts(24, FontWeight.Bold, Color.White))
                Text("${list.size} título${if (list.size == 1) "" else "s"} en Reality Vault", style = ts(12, color = C.muted))
            }
        }
        Spacer(Modifier.height(20.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
            itemsIndexed(list, key = { _, t -> t.id }) { i, t ->
                Focusable({ nav.go(Screen.Detail(t.id)) }, Modifier.width(110.dp).aspectRatio(2f / 3f), shape = RoundedCornerShape(9.dp), scale = 1.08f, ring = false, lift = true, requester = if (i == 0) firstReq else null) { PosterCard(t) }
            }
        }
    }
}

/* ---------------- utilidades de color ---------------- */

fun Color.darken(f: Float) = Color(red * (1 - f), green * (1 - f), blue * (1 - f), alpha)
fun Color.lighten(f: Float) = Color(red + (1 - red) * f, green + (1 - green) * f, blue + (1 - blue) * f, alpha)
