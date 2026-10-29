package com.realityvault.tv

import android.graphics.drawable.BitmapDrawable
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
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
                TopNavBar(nav, Modifier.align(Alignment.TopStart))
            }
        }
        // Aviso de actualización: se dibuja último para quedar siempre arriba de todo, pero
        // UpdateOverlay se oculta solo mientras se está reproduciendo un video.
        UpdateOverlay(nav)
    }
}

/* ---------------- menú de arriba, igual que el sitio web ---------------- */

@Composable
fun TopNavBar(nav: Nav, modifier: Modifier = Modifier) {
    val items = listOf(Screen.Home to "Inicio", Screen.Series to "Series", Screen.Movies to "Películas", Screen.MyList to "Mi lista")
    Row(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .85f), Color.Black.copy(alpha = .35f), Color.Transparent)))
            .padding(start = 48.dp, end = 48.dp, top = 24.dp, bottom = 46.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (Store.siteLogo.isNotBlank()) Img(Store.siteLogo, Modifier.height(24.dp).width(140.dp), scale = ContentScale.Fit, alignment = Alignment.CenterStart)
        else LocalLogo(Modifier.height(24.dp).width(140.dp))
        Spacer(Modifier.width(40.dp))
        items.forEach { (scr, label) ->
            NavLink(label, nav.top == scr) { if (nav.top != scr) nav.section(scr) }
            Spacer(Modifier.width(28.dp))
        }
        Spacer(Modifier.weight(1f))
        RoundButton(Ic.search, "Buscar", { nav.go(Screen.Search) })
        Spacer(Modifier.width(16.dp))
        Focusable({ nav.go(Screen.Account) }, Modifier.size(36.dp), shape = CircleShape, scale = 1.1f, ring = false) { f ->
            Box(
                Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFFFFD1E6), C.accent)), CircleShape)
                    .border(if (f) 2.5.dp else 0.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) { Text(Store.session?.email?.take(1)?.uppercase() ?: "?", style = ts(14, FontWeight.Bold, Color.White)) }
        }
    }
}

@Composable
private fun NavLink(label: String, selected: Boolean, onClick: () -> Unit) {
    Focusable(onClick, Modifier, shape = RoundedCornerShape(6.dp), scale = 1f, ring = false) { f ->
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = ts(15, FontWeight.Bold, if (f || selected) Color.White else C.muted))
            Spacer(Modifier.height(5.dp))
            Box(
                Modifier.width(if (selected || f) 22.dp else 0.dp).height(3.dp)
                    .background(if (selected || f) C.accent else Color.Transparent, RoundedCornerShape(2.dp))
            )
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
    // Mismo orden "al azar pero fijo durante la sesión" que usa el sitio web (ver Store.bannerSeed).
    val seededRandom = kotlin.random.Random(Store.bannerSeed)
    val fromBanners = Store.banners.shuffled(seededRandom).mapNotNull { b ->
        val t = Store.byId(b.titleId)?.takeIf { it.status != "borrador" } ?: return@mapNotNull null
        Slide(t, t.bannerTv.ifBlank { t.backdropTv.ifBlank { t.backdrop.ifBlank { b.image.ifBlank { t.tvHero } } } }, b.headline, b.text.ifBlank { t.tagline.ifBlank { t.description } })
    }
    if (fromBanners.isNotEmpty()) return fromBanners
    return Store.published().filter { it.featured }.shuffled(seededRandom).take(6).map { Slide(it, it.tvHero, "", it.tagline.ifBlank { it.description }) }
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
    // Atrás mientras estás abajo en las filas: primero vuelve arriba (con el foco puesto),
    // igual que cualquier app de streaming de verdad. Solo sale de la app si ya estás arriba del todo.
    val scope = rememberCoroutineScope()
    BackHandler(enabled = !heroFocused) {
        scope.launch { listState.animateScrollToItem(0); AutoFocusOnce(heroReq) }
    }

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
        Column(Modifier.align(Alignment.BottomStart).padding(start = 48.dp, bottom = 58.dp).width(540.dp)) {
            if (slide.headline.isNotBlank()) Text(slide.headline, style = ts(44, FontWeight.Bold, Color.White), maxLines = 2)
            else TitleLogo(t, 320.dp, 100.dp, 40)
            Spacer(Modifier.height(12.dp))
            KindLine(t)
            if (slide.text.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(slide.text, style = ts(15, FontWeight.Light, Color.White.copy(alpha = .85f)), maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 20.sp)
            }
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
            if (all.size > 1) {
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    all.indices.forEach { i ->
                        Box(Modifier.height(4.dp).width(if (i == idx) 26.dp else 14.dp).background(if (i == idx) C.accent else Color.White.copy(alpha = .3f), RoundedCornerShape(4.dp)))
                    }
                }
            }
        }
    }
}

@Composable
fun KindLine(t: Title) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text((listOf(t.kind()) + t.genres.take(2)).joinToString(" · "), style = ts(14, color = Color.White.copy(alpha = .88f)), maxLines = 1)
        if (t.rating.isNotBlank()) { Spacer(Modifier.width(9.dp)); RatingBox(t.rating) }
        if (t.formats.any) { Spacer(Modifier.width(9.dp)); FmtBadges(t.formats) }
    }
}

@Composable
fun RatingBox(r: String) {
    Box(Modifier.border(1.dp, Color.White.copy(alpha = .75f), RoundedCornerShape(3.dp)).padding(horizontal = 4.dp)) {
        Text(r, style = ts(10, FontWeight.Bold, Color.White))
    }
}

/* ---------------- insignias de formato (4K / HDR / Dolby Atmos / 5.1) ---------------- */

private val FmtInk = Color(0xFFEBEBF5)

private val FourKLogo: ImageVector by lazy {
    ImageVector.Builder(name = "4K", defaultWidth = 26.dp, defaultHeight = 15.dp, viewportWidth = 26f, viewportHeight = 15f)
        .addPath(
            pathData = addPathNodes("M2.737 0h20.528A2.73 2.73 0 0 1 26 2.727v9.545A2.73 2.73 0 0 1 23.265 15H2.737A2.73 2.73 0 0 1 0 12.272V2.727A2.73 2.73 0 0 1 2.737 0m7.078 11.6H11.5v-1.5h1.1V8.681h-1.1v-5.53H9a56 56 0 0 0-3.39 5.442V10.1h4.205zM7.162 8.686A40 40 0 0 1 9.815 4.4h.035v4.333H7.162zm8.273 2.914V9.02l.791-.949 2.507 3.529h2.114L17.5 6.9l3.13-3.749h-1.968l-3.174 3.854h-.053V3.151h-1.769V11.6z"),
            fill = SolidColor(FmtInk), pathFillType = PathFillType.EvenOdd
        ).build()
}

private val DolbyAtmosLogo: ImageVector by lazy {
    ImageVector.Builder(name = "DolbyAtmos", defaultWidth = 52.dp, defaultHeight = 22.dp, viewportWidth = 52f, viewportHeight = 22f)
        .addPath(
            pathData = addPathNodes("m12.114 14.87 3.014 7.085h-1.423l-.653-1.62H10.02l-.632 1.62H7.993l3.044-7.084zm8.386 0v1.101h-2.144v5.984H17.11v-5.984h-2.144v-1.1zm3.5 0 1.818 5.034h.03l1.798-5.033h1.956v7.084h-1.235v-5.693h-.02l-2.124 5.693h-.87l-2.094-5.693h-.02v5.693h-1.186v-7.084zm10.652 0q.78 0 1.438.248.658.247 1.138.705t.748 1.11q.267.651.268 1.46 0 .792-.268 1.444a3.3 3.3 0 0 1-.748 1.123q-.48.471-1.138.733a3.9 3.9 0 0 1-1.438.262q-.772 0-1.425-.262a3.35 3.35 0 0 1-1.128-.733 3.4 3.4 0 0 1-.743-1.123 3.8 3.8 0 0 1-.268-1.443q0-.81.268-1.462a3.2 3.2 0 0 1 .743-1.11 3.3 3.3 0 0 1 1.128-.704 4 4 0 0 1 1.425-.247zm7.359.012q.506 0 1.026.18t.905.541l-.769.854a1.44 1.44 0 0 0-.53-.412 1.57 1.57 0 0 0-1.101-.114 1.3 1.3 0 0 0-.375.161.9.9 0 0 0-.277.285.8.8 0 0 0-.107.422.76.76 0 0 0 .093.39.9.9 0 0 0 .258.275q.165.113.398.2.235.084.516.17.319.105.66.237.343.133.63.352.285.218.468.55t.183.826q0 .541-.197.944a1.94 1.94 0 0 1-.525.67 2.3 2.3 0 0 1-.769.399 3.2 3.2 0 0 1-.928.132 3.4 3.4 0 0 1-1.265-.242q-.619-.241-1.013-.717l.844-.797a1.7 1.7 0 0 0 .651.541q.408.2.802.2.206 0 .422-.053.215-.051.389-.175a1 1 0 0 0 .28-.309.9.9 0 0 0 .109-.46.76.76 0 0 0-.122-.442 1.1 1.1 0 0 0-.328-.303q-.206-.129-.488-.223l-.59-.2q-.3-.094-.6-.228-.3-.132-.54-.35a1.7 1.7 0 0 1-.388-.537q-.15-.318-.15-.793 0-.513.21-.883.212-.369.554-.612.342-.242.778-.36.435-.12.886-.12zm-7.35 1.14a2.08 2.08 0 0 0-1.592.687 2.3 2.3 0 0 0-.447.751 2.7 2.7 0 0 0-.159.934q0 .522.159.957.158.435.452.756.294.32.696.5c.402.18.566.178.891.178q.489 0 .896-.179a2.1 2.1 0 0 0 .7-.499q.294-.321.453-.756.158-.435.158-.957a2.7 2.7 0 0 0-.158-.934 2.3 2.3 0 0 0-.448-.75 2.1 2.1 0 0 0-.696-.505 2.2 2.2 0 0 0-.905-.183m-23.12.35-1.097 2.882h2.174zM46.325 3.15l1.993 4.537 1.993-4.537h1.671l-3.962 8.966a1.973 1.973 0 0 1-2.612 1.017l-.53-.24h-.003l.19-.432.427-.972.22.1a.813.813 0 0 0 1.077-.42l.004-.01.613-1.402.02-.044.056-.125-2.828-6.438zM36.392.002v10.23h-1.53V.002zm2.818 0v3.784a3.45 3.45 0 0 1 2-.638c1.928 0 3.496 1.588 3.496 3.54s-1.568 3.542-3.496 3.542a3.45 3.45 0 0 1-2-.639v.64h-1.532V.002zm-8.834 3.146c1.928 0 3.496 1.588 3.496 3.54s-1.568 3.541-3.496 3.541c-1.929 0-3.497-1.588-3.497-3.54s1.568-3.541 3.497-3.541M1.489 0c2.78 0 5.045 2.295 5.045 5.11s-2.266 5.11-5.045 5.11H0V0zm12.867 0v10.22h-1.488c-2.78 0-5.046-2.295-5.046-5.11S10.088 0 12.868 0zm6.7 0c2.783 0 5.047 2.292 5.047 5.11s-2.264 5.11-5.046 5.11h-3.645V0zm9.32 4.54c-1.157 0-2.109.95-2.109 2.135 0 1.172.939 2.135 2.109 2.135 1.157 0 2.108-.95 2.108-2.135 0-1.171-.951-2.135-2.108-2.135m10.834 0c-.927 0-1.72.61-2 1.462a2.15 2.15 0 0 0 0 1.346 2.11 2.11 0 0 0 2 1.463c1.157 0 2.108-.95 2.108-2.135 0-1.172-.951-2.135-2.108-2.135zM21.057 1.55h-2.115v7.12h2.115c1.937 0 3.516-1.599 3.516-3.56s-1.58-3.56-3.516-3.56"),
            fill = SolidColor(FmtInk)
        ).build()
}

private val DolbyVisionLogo: ImageVector by lazy {
    ImageVector.Builder(name = "DolbyVision", defaultWidth = 52.dp, defaultHeight = 22.dp, viewportWidth = 52f, viewportHeight = 22f)
        .addPath(
            pathData = addPathNodes("m9.72 14.87 1.927 5.454h.04l1.946-5.453h1.344l-2.806 7.084h-1.077l-2.767-7.084zm7.839 0v7.085h-1.245v-7.084zm9.369 0v7.085h-1.245v-7.084zm5.227 0q.78 0 1.438.248.658.247 1.138.705t.748 1.11.268 1.46q0 .792-.268 1.444a3.3 3.3 0 0 1-.748 1.123q-.48.471-1.138.733a3.9 3.9 0 0 1-1.438.262q-.773 0-1.425-.262a3.35 3.35 0 0 1-1.128-.733 3.4 3.4 0 0 1-.743-1.123 3.8 3.8 0 0 1-.268-1.443q0-.81.268-1.462.267-.651.743-1.109.474-.458 1.128-.705.652-.247 1.425-.247zm6.985 0 3.25 5.304h.02v-5.303h1.245v7.084h-1.58l-3.32-5.473h-.02v5.473h-1.246v-7.084zm-17.28.002q.506 0 1.026.18.416.145.746.405l.159.137-.769.854a1.44 1.44 0 0 0-.53-.413 1.57 1.57 0 0 0-1.1-.114 1.3 1.3 0 0 0-.376.162.9.9 0 0 0-.276.284.8.8 0 0 0-.108.423.76.76 0 0 0 .351.664q.164.114.399.2.234.085.515.17.32.105.661.238.343.133.628.351c.285.218.347.33.469.55q.183.333.183.826 0 .541-.197.945a1.94 1.94 0 0 1-.525.67 2.2 2.2 0 0 1-.769.398 3.2 3.2 0 0 1-.928.133 3.4 3.4 0 0 1-1.265-.242 2.5 2.5 0 0 1-.875-.565l-.138-.152.844-.797q.243.342.652.54.407.2.801.2.206 0 .422-.052a1.1 1.1 0 0 0 .389-.176 1 1 0 0 0 .281-.308.9.9 0 0 0 .108-.46.76.76 0 0 0-.122-.442 1.1 1.1 0 0 0-.328-.304q-.207-.128-.488-.223l-.59-.2q-.3-.093-.6-.227-.3-.133-.54-.351a1.7 1.7 0 0 1-.388-.537q-.15-.318-.15-.792 0-.513.21-.883.212-.37.554-.612.342-.243.778-.361.435-.12.886-.119m10.304 1.15q-.498 0-.9.183-.402.184-.692.504a2.3 2.3 0 0 0-.447.751 2.7 2.7 0 0 0-.159.934q0 .523.159.957.158.435.452.756.294.32.696.5c.402.18.566.178.891.178q.489 0 .895-.179c.406-.179.505-.285.701-.499q.294-.321.453-.756.158-.435.158-.957a2.7 2.7 0 0 0-.158-.934 2.3 2.3 0 0 0-.448-.75 2.1 2.1 0 0 0-.697-.505 2.2 2.2 0 0 0-.904-.183M46.325 3.15l1.993 4.536 1.993-4.536h1.671l-3.962 8.966a1.975 1.975 0 0 1-2.47 1.075l-.142-.058-.53-.24h-.003l.19-.431.428-.973.219.1a.81.81 0 0 0 1.025-.32l.052-.1.004-.01.613-1.402.02-.044.057-.125-2.829-6.438zM39.21.002v3.784a3.45 3.45 0 0 1 2-.638c1.928 0 3.496 1.588 3.496 3.541s-1.568 3.54-3.496 3.54a3.45 3.45 0 0 1-2-.638v.64h-1.532V.002zm-2.818 0v10.23h-1.53V.002zm-6.016 3.146c1.928 0 3.496 1.588 3.496 3.54 0 1.953-1.568 3.541-3.496 3.541-1.929 0-3.497-1.588-3.497-3.54 0-1.953 1.568-3.541 3.497-3.541M21.057 0c2.782 0 5.046 2.292 5.046 5.11s-2.264 5.11-5.046 5.11h-3.645V0zm-6.7 0v10.22h-1.49c-2.78 0-5.045-2.295-5.045-5.11 0-2.743 2.151-4.992 4.833-5.105L12.868 0zM1.488 0c2.78 0 5.046 2.295 5.046 5.11 0 2.743-2.152 4.991-4.833 5.105l-.213.004h-1.49V0zm39.72 4.541c-.926 0-1.719.61-1.999 1.461a2.15 2.15 0 0 0 0 1.346 2.11 2.11 0 0 0 2 1.463c1.157 0 2.108-.95 2.108-2.135 0-1.172-.951-2.135-2.108-2.135zm-10.833 0c-1.157 0-2.109.95-2.109 2.134 0 1.172.939 2.135 2.109 2.135 1.157 0 2.108-.95 2.108-2.135 0-1.171-.951-2.135-2.108-2.135zm-9.32-2.992h-2.114V8.67h2.115c1.937 0 3.516-1.599 3.516-3.56s-1.58-3.56-3.516-3.56z"),
            fill = SolidColor(FmtInk)
        ).build()
}

@Composable
private fun FmtLogo(icon: ImageVector, heightDp: Dp, vwVh: Float, desc: String) {
    Image(icon, desc, modifier = Modifier.height(heightDp).width(heightDp * vwVh))
}

@Composable
fun FmtBadges(f: Formats) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (f.uhd4k) FmtLogo(FourKLogo, 15.dp, 26f / 15f, "4K Ultra HD")
        if (f.hdr) FmtLogo(DolbyVisionLogo, 13.dp, 52f / 22f, "Dolby Vision")
        if (f.atmos) FmtLogo(DolbyAtmosLogo, 13.dp, 52f / 22f, "Dolby Atmos")
        if (f.surround51) Box(Modifier.border(1.dp, Color.White.copy(alpha = .75f), RoundedCornerShape(3.dp)).padding(horizontal = 4.dp)) {
            Text("5.1", style = ts(10, FontWeight.Bold, Color.White))
        }
    }
}

@Composable
fun PlayPill(text: String, icon: ImageVector?, req: FocusRequester? = null, onClick: () -> Unit) {
    Focusable(onClick, Modifier.height(46.dp), shape = RoundedCornerShape(99.dp), scale = 1.06f, ring = false, lift = true, requester = req) {
        Row(Modifier.background(Color.White).padding(horizontal = 28.dp).height(46.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(icon, null, tint = Color.Black, modifier = Modifier.size(15.dp)); Spacer(Modifier.width(8.dp)) }
            Text(text, style = ts(15, FontWeight.Bold, Color.Black), maxLines = 1)
        }
    }
}

@Composable
fun GlassButton(icon: ImageVector, onClick: () -> Unit) {
    Focusable(onClick, Modifier.size(42.dp), shape = CircleShape, scale = 1.1f, ring = false, lift = true) { f ->
        Box(Modifier.fillMaxSize().background(if (f) Color.White else Color(0x9C3C3C42)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = if (f) Color.Black else Color.White, modifier = Modifier.size(18.dp))
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
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, .42f to Color.Transparent, .68f to tint.copy(alpha = .75f), .88f to tint, 1f to tint)))
                        Column(Modifier.align(Alignment.BottomStart).padding(start = 48.dp, bottom = 80.dp).width(540.dp)) {
                            TitleLogo(t, 320.dp, 100.dp, 40)
                            Spacer(Modifier.height(12.dp))
                            KindLine(t)
                            if (t.description.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text(t.description, style = ts(15, FontWeight.Light, Color.White.copy(alpha = .85f)), maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 20.sp)
                            }
                            val facts = listOfNotNull(
                                t.year.takeIf { it.isNotBlank() }?.let { if (t.yearEnd.isNotBlank()) "$it – ${t.yearEnd}" else it },
                                if (t.isSerie && t.seasons.isNotEmpty()) "${t.seasons.size} temporada" + (if (t.seasons.size == 1) "" else "s") else t.duration.ifBlank { null }
                            ).joinToString(" · ")
                            if (facts.isNotBlank()) {
                                Spacer(Modifier.height(10.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(facts, style = ts(13, color = Color.White.copy(alpha = .85f)))
                                    if (t.rating.isNotBlank()) { Spacer(Modifier.width(9.dp)); RatingBox(t.rating) }
                                }
                            }
                            Spacer(Modifier.height(20.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
