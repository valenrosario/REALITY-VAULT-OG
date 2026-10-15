package com.realityvault.tv

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/* ---------------- navegación ---------------- */

sealed class Screen {
    data object Home : Screen()
    data object Series : Screen()
    data object Movies : Screen()
    data object MyList : Screen()
    data object Search : Screen()
    data object Account : Screen()
    data class Detail(val id: String) : Screen()
    data class Player(val titleId: String, val url: String, val season: Int?, val episode: Int?, val trailer: Boolean = false) : Screen()
    data class Login(val then: Screen? = null) : Screen()
}

private val mainScreens = listOf(Screen.Search, Screen.Home, Screen.Series, Screen.Movies, Screen.MyList, Screen.Account)

/** Guarda dónde estaba parado el usuario en cada pantalla para volver justo ahí. */
class Memory {
    val lists = HashMap<String, LazyListState>()
    val grids = HashMap<String, LazyGridState>()
    val focus = HashMap<String, String>()
    fun list(k: String) = lists.getOrPut(k) { LazyListState() }
    fun grid(k: String) = grids.getOrPut(k) { LazyGridState() }
}

class Nav {
    val stack = mutableStateListOf<Screen>(Screen.Home)
    val top get() = stack.last()
    val memory = Memory()
    fun go(s: Screen) { stack.add(s) }
    fun section(s: Screen) { stack.clear(); stack.add(s) }
    fun back(): Boolean { if (stack.size > 1) { stack.removeAt(stack.lastIndex); return true }; return false }
    fun replace(s: Screen) { stack[stack.lastIndex] = s }

    /** Ver algo: pide cuenta igual que el sitio. */
    fun play(t: Title, url: String, season: Int?, episode: Int?) {
        val p = Screen.Player(t.id, url, season, episode)
        if (Store.session == null) go(Screen.Login(p)) else go(p)
    }
}

@Composable
fun App(nav: Nav, onExit: () -> Unit) {
    BackHandler {
        if (!nav.back()) {
            if (nav.top != Screen.Home) nav.section(Screen.Home) else onExit()
        }
    }
    Box(Modifier.fillMaxSize().background(C.bg)) {
        when (val s = nav.top) {
            is Screen.Player -> PlayerScreen(s, nav)
            is Screen.Detail -> DetailScreen(s.id, nav)
            is Screen.Login -> LoginScreen(s, nav)
            else -> Row(Modifier.fillMaxSize()) {
                Rail(nav)
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    when (s) {
                        Screen.Home -> HomeScreen(nav)
                        Screen.Series -> GridScreen(nav, "series")
                        Screen.Movies -> GridScreen(nav, "peliculas")
                        Screen.MyList -> GridScreen(nav, "milista")
                        Screen.Search -> SearchScreen(nav)
                        Screen.Account -> AccountScreen(nav)
                        else -> {}
                    }
                }
            }
        }
    }
}

/* ---------------- menú lateral ---------------- */

@Composable
fun Rail(nav: Nav) {
    var open by remember { mutableStateOf(false) }
    val w by animateDpAsState(if (open) 210.dp else 76.dp, tween(180), label = "rail")
    val items = listOf(
        Triple(Screen.Search, Ic.search, "Buscar"), Triple(Screen.Home, Ic.home, "Inicio"),
        Triple(Screen.Series, Ic.tv, "Series"), Triple(Screen.Movies, Ic.film, "Películas"),
        Triple(Screen.MyList, Ic.plus, "Mi lista"),
        Triple(Screen.Account, Ic.user, if (Store.session != null) "Mi cuenta" else "Entrar")
    )
    Box(Modifier.width(76.dp).fillMaxHeight()) {
        Column(
            Modifier.width(w).fillMaxHeight()
                .background(if (open) Brush.horizontalGradient(listOf(Color.Black, Color.Black.copy(alpha = .9f), Color.Transparent)) else Brush.horizontalGradient(listOf(Color.Black.copy(alpha = .6f), Color.Transparent)))
                .onFocusChanged { open = it.hasFocus }
                .padding(start = 14.dp, top = 40.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items.forEachIndexed { i, (screen, icon, label) ->
                if (i == items.lastIndex) Spacer(Modifier.weight(1f))
                val selected = nav.top == screen
                Focusable(
                    onClick = { if (nav.top != screen) nav.section(screen) },
                    modifier = Modifier.height(46.dp).width(if (open) 180.dp else 48.dp),
                    shape = RoundedCornerShape(99.dp), scale = 1.04f, ring = false
                ) { f ->
                    Row(
                        Modifier.fillMaxSize().background(if (f) Color.White else if (selected) Color.White.copy(alpha = .14f) else Color.Transparent)
                            .padding(horizontal = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(icon, label, tint = if (f) Color.Black else if (selected) Color.White else C.muted, modifier = Modifier.size(22.dp))
                        if (open) {
                            Spacer(Modifier.width(14.dp))
                            Text(label, style = ts(16, FontWeight.Bold, if (f) Color.Black else C.fg), maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

/* ---------------- inicio ---------------- */

data class Slide(val t: Title, val image: String, val headline: String, val text: String)

fun slides(): List<Slide> {
    val pub = Store.published()
    val fromBanners = Store.banners.mapNotNull { b ->
        val t = Store.byId(b.titleId)?.takeIf { it.status != "borrador" } ?: return@mapNotNull null
        Slide(t, b.image.ifBlank { t.bannerImage.ifBlank { t.bg } }, b.headline, b.text.ifBlank { t.description })
    }
    if (fromBanners.isNotEmpty()) return fromBanners
    return pub.filter { it.featured }.take(5).map { Slide(it, it.bannerImage.ifBlank { it.bg }, "", it.description) }
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

@Composable
fun HomeScreen(nav: Nav) {
    val all = slides()
    val rows = homeRows()
    val mem = nav.memory
    val listState = mem.list("home")
    var heroIdx by remember { mutableIntStateOf(0) }
    var heroFocused by remember { mutableStateOf(false) }
    var bgTitle by remember { mutableStateOf<Title?>(null) }
    val slide = all.getOrNull(if (all.isEmpty()) 0 else heroIdx % all.size)
    val heroReq = remember { FocusRequester() }
    val restoreKey = mem.focus["home"]

    // el banner cambia solo cada 9 segundos mientras no lo estés mirando con el control
    LaunchedEffect(heroIdx, heroFocused, all.size) {
        if (all.size > 1 && !heroFocused) { delay(9000); heroIdx = (heroIdx + 1) % all.size }
    }
    if (restoreKey == null) AutoFocus(heroReq, slide?.t?.id)

    if (!Store.loaded) { Loading(); return }
    if (all.isEmpty() && rows.isEmpty()) { Empty("Todavía no hay títulos publicados."); return }

    val bgUrl = if (heroFocused || bgTitle == null) slide?.image ?: "" else bgTitle!!.bg
    Box(Modifier.fillMaxSize()) {
        // fondo: la imagen del banner o de lo que tengas seleccionado
        Crossfade(bgUrl, animationSpec = tween(350), label = "bg") { u ->
            Box(Modifier.fillMaxSize()) {
                if (u.isNotBlank()) Img(u, Modifier.fillMaxSize(), alignment = Alignment.TopEnd, crossfade = false)
            }
        }
        Box(Modifier.fillMaxSize().background(leftScrim))
        Box(Modifier.fillMaxSize().background(bottomScrim))
        if (!heroFocused) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .45f)))

        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 60.dp)) {
            item(key = "hero") {
                if (slide != null) Hero(slide, all.size, heroIdx, nav, heroReq) { heroFocused = it }
                else Spacer(Modifier.height(40.dp))
            }
            items(rows, key = { it.key }) { r ->
                TitleRow(r, nav, "home", mem.list("row-" + r.key)) { bgTitle = it }
            }
        }
        Store.siteLogo.takeIf { it.isNotBlank() }?.let {
            Img(it, Modifier.padding(start = 24.dp, top = 22.dp).height(26.dp).width(170.dp), scale = ContentScale.Fit, alignment = Alignment.CenterStart)
        }
    }
}

@Composable
fun Hero(slide: Slide, count: Int, idx: Int, nav: Nav, req: FocusRequester, onFocus: (Boolean) -> Unit) {
    val t = slide.t
    val target = Store.playTarget(t)
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxWidth().height(380.dp).onFocusChanged { onFocus(it.hasFocus) }) {
        Column(Modifier.align(Alignment.BottomStart).padding(start = 24.dp, bottom = 34.dp).width(470.dp)) {
            if (slide.headline.isNotBlank()) Text(slide.headline, style = ts(40, FontWeight.Bold, Color.White), maxLines = 2)
            else TitleLogo(t, 330.dp, 110.dp)
            Spacer(Modifier.height(14.dp))
            MetaLine(t)
            if (slide.text.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(slide.text, style = ts(16, FontWeight.Light, C.fg2), maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                when {
                    t.isSoon -> PillButton(if (t.releaseDate.isNotBlank()) "Próximamente · ${t.releaseDate}" else "Próximamente", { nav.go(Screen.Detail(t.id)) }, icon = null, requester = req)
                    target != null -> PillButton(target.label, { nav.play(t, target.url, target.season, target.episode) }, requester = req)
                    else -> PillButton("Más información", { nav.go(Screen.Detail(t.id)) }, icon = null, requester = req)
                }
                TextButtonTv("Más información", { nav.go(Screen.Detail(t.id)) })
                ListButton(t, nav, scope)
                if (count > 1) Text("${idx % count + 1} · $count", style = ts(14, FontWeight.Bold, C.muted), modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

@Composable
fun ListButton(t: Title, nav: Nav, scope: kotlinx.coroutines.CoroutineScope) {
    val on = t.id in Store.myList
    RoundButton(if (on) Ic.check else Ic.plus, if (on) "Quitar de Mi lista" else "Agregar a Mi lista", {
        if (Store.session == null) nav.go(Screen.Login()) else scope.launch { Store.toggleList(t.id) }
    })
}

/* ---------------- filas ---------------- */

@Composable
fun TitleRow(r: RowSpec, nav: Nav, screenKey: String, state: LazyListState, onFocusTitle: (Title) -> Unit) {
    val mem = nav.memory
    val restore = mem.focus[screenKey]
    Column(Modifier.padding(top = 6.dp)) {
        Text(r.title, style = ts(19, FontWeight.Bold, Color.White), modifier = Modifier.padding(start = 24.dp, bottom = 2.dp))
        LazyRow(
            state = state,
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(if (r.kind == "top") 6.dp else 16.dp)
        ) {
            if (r.kind == "feat") {
                itemsIndexed(r.feat, key = { i, _ -> "f$i" }) { i, (t, item) ->
                    val k = "${r.key}/$i"
                    CardBox(k, restore, nav, screenKey, { nav.go(Screen.Detail(t.id)) }, { onFocusTitle(t) }, Modifier.width(150.dp).aspectRatio(3f / 4.45f)) {
                        FeatCard(t, item)
                    }
                }
            } else itemsIndexed(r.titles, key = { _, t -> t.id }) { i, t ->
                val k = "${r.key}/${t.id}"
                when (r.kind) {
                    "wide" -> {
                        val p = if (r.key == "cont") Store.progress.firstOrNull { it.titleId == t.id } else null
                        CardBox(k, restore, nav, screenKey, {
                            val tg = Store.playTarget(t)
                            if (r.key == "cont" && tg != null) nav.play(t, tg.url, tg.season, tg.episode) else nav.go(Screen.Detail(t.id))
                        }, { onFocusTitle(t) }, Modifier.width(240.dp).aspectRatio(16f / 9f)) {
                            WideCard(t, if (p?.season != null) "T${p.season}:E${p.episode}" else null)
                        }
                    }
                    "top" -> Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "${i + 1}", modifier = Modifier.offset(x = 10.dp, y = 10.dp),
                            style = ts(118, FontWeight.Bold, C.accent).copy(drawStyle = Stroke(width = 5f))
                        )
                        CardBox(k, restore, nav, screenKey, { nav.go(Screen.Detail(t.id)) }, { onFocusTitle(t) }, Modifier.width(118.dp).aspectRatio(2f / 3f)) {
                            PosterCard(t)
                        }
                    }
                    else -> CardBox(k, restore, nav, screenKey, { nav.go(Screen.Detail(t.id)) }, { onFocusTitle(t) }, Modifier.width(118.dp).aspectRatio(2f / 3f)) {
                        PosterCard(t)
                    }
                }
            }
        }
    }
}

@Composable
fun CardBox(
    key: String, restore: String?, nav: Nav, screenKey: String, onClick: () -> Unit, onFocus: () -> Unit,
    modifier: Modifier, content: @Composable () -> Unit
) {
    val req = remember { FocusRequester() }
    if (key == restore) AutoFocus(req, key)
    Focusable(onClick, modifier, requester = req, onFocus = { nav.memory.focus[screenKey] = key; onFocus() }) { content() }
}

@Composable
fun PosterCard(t: Title) {
    Box(Modifier.fillMaxSize().background(C.surface)) {
        if (t.poster.isNotBlank()) Img(t.poster, Modifier.fillMaxSize()) else Placeholder(t, Modifier.fillMaxSize())
        if (t.isSoon) Text(
            "Próximamente", style = ts(11, FontWeight.Bold, Color.Black),
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp).background(C.accent, RoundedCornerShape(99.dp)).padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun WideCard(t: Title, label: String?) {
    Box(Modifier.fillMaxSize().background(C.surface)) {
        if (t.wide.isNotBlank()) Img(t.wide, Modifier.fillMaxSize()) else Placeholder(t, Modifier.fillMaxSize())
        if (label != null) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .55f), Color.Transparent, Color.Transparent))))
            Text(label, style = ts(13, FontWeight.Bold, Color.White), modifier = Modifier.padding(10.dp))
        }
    }
}

@Composable
fun FeatCard(t: Title, item: FeatItem) {
    val img = item.image.ifBlank { t.wide }
    Box(Modifier.fillMaxSize().background(C.surface)) {
        if (img.isNotBlank()) Img(img, Modifier.fillMaxSize()) else Placeholder(t, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, .45f to Color.Transparent, 1f to Color.Black)))
        Column(Modifier.align(Alignment.BottomCenter).padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (t.logo.isNotBlank()) Img(t.logo, Modifier.fillMaxWidth(.8f).height(44.dp), scale = ContentScale.Fit, alignment = Alignment.BottomCenter)
            else Text(t.title, style = ts(15, FontWeight.Bold, Color.White), maxLines = 2)
            if (item.label.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(item.label, style = ts(11, FontWeight.Bold, C.fg2), maxLines = 1)
            }
        }
    }
}

/* ---------------- grillas: series, películas, mi lista ---------------- */

@Composable
fun GridScreen(nav: Nav, kind: String) {
    if (!Store.loaded) { Loading(); return }
    val pub = Store.published()
    var genre by remember(kind) { mutableStateOf("") }
    val base = when (kind) {
        "series" -> pub.filter { it.isSerie }
        "peliculas" -> pub.filter { !it.isSerie }
        else -> Store.myList.mapNotNull { id -> pub.firstOrNull { it.id == id } }
    }
    val genres = if (kind == "milista") emptyList() else base.flatMap { it.genres }.distinct().sorted()
    val list = if (genre.isBlank()) base else base.filter { genre in it.genres }
    val title = when (kind) { "series" -> "Series"; "peliculas" -> "Películas"; else -> "Mi lista" }
    val firstReq = remember { FocusRequester() }
    val restore = nav.memory.focus[kind]

    Column(Modifier.fillMaxSize().padding(start = 24.dp, top = 34.dp)) {
        Text(title, style = ts(34, FontWeight.Bold, Color.White))
        Text("${list.size} título${if (list.size == 1) "" else "s"}", style = ts(15, color = C.muted))
        if (kind == "milista" && Store.session == null) {
            Spacer(Modifier.height(24.dp))
            Text("Entrá con tu cuenta para guardar series en tu lista.", style = ts(18, color = C.fg2))
            Spacer(Modifier.height(16.dp))
            TextButtonTv("Entrar", { nav.go(Screen.Login()) }, requester = firstReq)
            AutoFocus(firstReq, kind)
            return@Column
        }
        if (genres.isNotEmpty()) {
            LazyRow(contentPadding = PaddingValues(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Chip("Todo", genre.isBlank()) { genre = "" } }
                items(genres) { g -> Chip(g, genre == g) { genre = g } }
            }
        } else Spacer(Modifier.height(16.dp))
        if (list.isEmpty()) { Text(if (kind == "milista") "Todavía no agregaste nada." else "No hay títulos.", style = ts(18, color = C.fg2)); return@Column }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(126.dp), state = nav.memory.grid(kind + genre),
            contentPadding = PaddingValues(top = 10.dp, end = 24.dp, bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp), verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            items(list, key = { it.id }) { t ->
                val k = "$kind/${t.id}"
                val req = remember { FocusRequester() }
                if (k == restore || (restore == null && t.id == list.first().id)) AutoFocus(req, k)
                Focusable({ nav.go(Screen.Detail(t.id)) }, Modifier.aspectRatio(2f / 3f), requester = req, onFocus = { nav.memory.focus[kind] = k }) {
                    PosterCard(t)
                }
            }
        }
    }
}

@Composable
fun Chip(text: String, on: Boolean, onClick: () -> Unit) {
    Focusable(onClick, Modifier.height(36.dp), shape = RoundedCornerShape(99.dp), scale = 1.06f, ring = false) { f ->
        Box(
            Modifier.background(if (f) Color.White else if (on) C.accent else Color.White.copy(alpha = .1f)).padding(horizontal = 16.dp).height(36.dp),
            contentAlignment = Alignment.Center
        ) { Text(text, style = ts(14, FontWeight.Bold, if (f || on) Color.Black else C.fg)) }
    }
}

/* ---------------- ficha de la serie / película ---------------- */

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

    Box(Modifier.fillMaxSize()) {
        if (t.bg.isNotBlank()) Img(t.bg, Modifier.fillMaxSize(), alignment = Alignment.TopEnd)
        Box(Modifier.fillMaxSize().background(leftScrim))
        Box(Modifier.fillMaxSize().background(bottomScrim))
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 50.dp)) {
            item {
                Column(Modifier.padding(start = 56.dp, top = 70.dp).width(520.dp)) {
                    TitleLogo(t, 360.dp, 120.dp, 44)
                    Spacer(Modifier.height(16.dp))
                    MetaLine(t)
                    if (t.tagline.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text(t.tagline, style = ts(16, FontWeight.Bold, Color.White)) }
                    if (t.description.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(t.description, style = ts(16, FontWeight.Light, C.fg2), maxLines = 5, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(22.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        when {
                            t.isSoon -> PillButton(if (t.releaseDate.isNotBlank()) "Próximamente · ${t.releaseDate}" else "Próximamente", {}, icon = null, requester = playReq)
                            target != null -> PillButton(target.label, { nav.play(t, target.url, target.season, target.episode) }, requester = playReq)
                            else -> PillButton("Sin video por ahora", {}, icon = null, requester = playReq)
                        }
                        ListButton(t, nav, scope)
                        if (mediaFor(t.trailer) != null) RoundButton(Ic.clapper, "Tráiler", { nav.go(Screen.Player(t.id, t.trailer, null, null, trailer = true)) })
                    }
                    if (t.genres.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Text(t.genres.joinToString("  ·  "), style = ts(14, color = C.muted))
                    }
                }
            }
            if (t.isSerie && t.seasons.isNotEmpty()) {
                if (t.seasons.size > 1) item {
                    LazyRow(contentPadding = PaddingValues(start = 56.dp, end = 24.dp, top = 30.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        itemsIndexed(t.seasons) { i, s -> Chip(s.name.ifBlank { "Temporada ${s.number}" }, i == season) { season = i } }
                    }
                }
                item {
                    val s = t.seasons.getOrNull(season) ?: t.seasons.first()
                    Column {
                        if (t.seasons.size == 1) Text(s.name.ifBlank { "Temporada ${s.number}" }, style = ts(19, FontWeight.Bold, Color.White), modifier = Modifier.padding(start = 56.dp, top = 30.dp))
                        LazyRow(contentPadding = PaddingValues(horizontal = 56.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            items(s.episodes, key = { "${s.number}-${it.number}" }) { e ->
                                EpisodeCard(t, s, e, prog) {
                                    if (!t.isSoon && isHttp(e.url)) nav.play(t, e.url, s.number, e.number)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EpisodeCard(t: Title, s: Season, e: Episode, prog: Progress?, onClick: () -> Unit) {
    val seen = prog?.watched?.contains("${s.number}:${e.number}") == true
    val current = prog?.season == s.number && prog?.episode == e.number
    Column(Modifier.width(236.dp)) {
        Focusable(onClick, Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            Box(Modifier.fillMaxSize().background(C.surface)) {
                val img = e.thumb.ifBlank { t.wide }
                if (img.isNotBlank()) Img(img, Modifier.fillMaxSize()) else Placeholder(t, Modifier.fillMaxSize())
                if (!isHttp(e.url)) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .55f)), contentAlignment = Alignment.Center) {
                    Text("Sin video", style = ts(13, FontWeight.Bold, C.fg2))
                }
                if (seen || current) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(if (current) C.accent else C.accent.copy(alpha = .7f)))
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("E${e.number}${if (e.title.isNotBlank()) " · " + e.title else ""}", style = ts(15, FontWeight.Bold, Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis)
        val sub = listOfNotNull(e.duration.ifBlank { null }, if (seen) "Visto" else null).joinToString(" · ")
        if (sub.isNotBlank()) Text(sub, style = ts(13, color = C.muted), maxLines = 1)
    }
}

/* ---------------- buscar ---------------- */

@Composable
fun SearchScreen(nav: Nav) {
    var q by remember { mutableStateOf(nav.memory.focus["searchQ"] ?: "") }
    val req = remember { FocusRequester() }
    if (nav.memory.focus["search"] == null) AutoFocus(req)
    val pub = Store.published()
    val n = q.trim().lowercase()
    val res = if (n.isBlank()) emptyList() else pub.filter { t ->
        t.title.lowercase().contains(n) || t.genres.any { it.lowercase().contains(n) } || t.description.lowercase().contains(n)
    }
    Column(Modifier.fillMaxSize().padding(start = 24.dp, top = 34.dp, end = 24.dp)) {
        Text("Buscar", style = ts(34, FontWeight.Bold, Color.White))
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = q, onValueChange = { q = it; nav.memory.focus["searchQ"] = it },
            modifier = Modifier.width(520.dp).focusRequester(req),
            placeholder = { Text("Títulos, géneros…", style = ts(17, color = C.faint)) },
            textStyle = ts(18), singleLine = true, colors = fieldColors(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
        )
        Spacer(Modifier.height(18.dp))
        if (n.isNotBlank() && res.isEmpty()) Text("No encontramos nada con “$q”.", style = ts(17, color = C.fg2))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(126.dp), contentPadding = PaddingValues(top = 10.dp, bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp), verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            items(res, key = { it.id }) { t ->
                Focusable({ nav.go(Screen.Detail(t.id)) }, Modifier.aspectRatio(2f / 3f)) { PosterCard(t) }
            }
        }
    }
}


@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = C.fg, unfocusedTextColor = C.fg, cursorColor = C.accent,
    focusedBorderColor = Color.White, unfocusedBorderColor = Color.White.copy(alpha = .3f),
    focusedContainerColor = Color.White.copy(alpha = .06f), unfocusedContainerColor = Color.White.copy(alpha = .04f),
    focusedLabelColor = C.fg, unfocusedLabelColor = C.muted
)

/* ---------------- cuenta ---------------- */

@Composable
fun LoginScreen(s: Screen.Login, nav: Nav) {
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val req = remember { FocusRequester() }
    AutoFocus(req)
    fun done() { if (s.then != null) nav.replace(s.then) else nav.back() }
    fun run(signup: Boolean) {
        if (busy) return
        if (!email.contains("@") || pass.length < 6) { err = "Escribí tu email y una contraseña de al menos 6 caracteres."; return }
        busy = true; err = ""
        scope.launch {
            try { if (signup) Store.signup(email.trim(), pass) else Store.login(email.trim(), pass); done() }
            catch (e: Exception) {
                err = when {
                    e is Api.HttpError && e.code == 400 && !signup -> "Email o contraseña incorrectos."
                    e is Api.HttpError -> e.message ?: "No se pudo entrar."
                    else -> "Sin conexión. Probá de nuevo."
                }
            }
            busy = false
        }
    }
    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF2A1520), Color.Black), radius = 1400f)), contentAlignment = Alignment.Center) {
        Column(Modifier.width(440.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (Store.siteLogo.isNotBlank()) Img(Store.siteLogo, Modifier.width(260.dp).height(48.dp), scale = ContentScale.Fit)
            else LocalLogo(Modifier.width(260.dp).height(48.dp))
            Spacer(Modifier.height(18.dp))
            Text("Entrá con tu cuenta para ver series y películas", style = ts(17, color = C.fg2))
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                email, { email = it }, Modifier.fillMaxWidth().focusRequester(req),
                label = { Text("Email", style = ts(14)) }, textStyle = ts(18), singleLine = true, colors = fieldColors(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next)
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                pass, { pass = it }, Modifier.fillMaxWidth(),
                label = { Text("Contraseña", style = ts(14)) }, textStyle = ts(18), singleLine = true, colors = fieldColors(),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { run(false) })
            )
            if (err.isNotBlank()) { Spacer(Modifier.height(12.dp)); Text(err, style = ts(15, FontWeight.Bold, C.accent)) }
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PillButton(if (busy) "Entrando…" else "Entrar", { run(false) }, icon = null)
                TextButtonTv("Crear cuenta", { run(true) })
                TextButtonTv("Ahora no", { nav.back() })
            }
        }
    }
}

@Composable
fun AccountScreen(nav: Nav) {
    val s = Store.session
    val req = remember { FocusRequester() }
    AutoFocus(req)
    Column(Modifier.fillMaxSize().padding(start = 24.dp, top = 34.dp)) {
        Text(if (s != null) "Mi cuenta" else "Entrar", style = ts(34, FontWeight.Bold, Color.White))
        Spacer(Modifier.height(18.dp))
        if (s != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Color(0xFFFFD1E6), C.accent, Color(0xFFB04C86)))), contentAlignment = Alignment.Center) {
                    Text("★", style = ts(26, color = Color.White))
                }
                Spacer(Modifier.width(16.dp))
                Text(s.email, style = ts(20, FontWeight.Bold))
            }
            Spacer(Modifier.height(26.dp))
            TextButtonTv("Cerrar sesión", { Store.logout(); nav.section(Screen.Home) }, requester = req)
        } else {
            Text("Con tu cuenta podés ver capítulos, guardar series en Mi lista y seguir donde dejaste.", style = ts(18, color = C.fg2), modifier = Modifier.width(560.dp))
            Spacer(Modifier.height(22.dp))
            TextButtonTv("Entrar o crear cuenta", { nav.go(Screen.Login()) }, requester = req)
        }
    }
}

/* ---------------- estados vacíos ---------------- */

@Composable
fun Loading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        LocalLogo(Modifier.width(300.dp).height(50.dp))
    }
}

@Composable
fun Empty(msg: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(if (Store.offline) "Sin conexión a internet. Revisá la red de la tele." else msg, style = ts(20, color = C.fg2))
    }
}

@Composable
fun LocalLogo(modifier: Modifier) {
    Image(painterResource(R.drawable.site_logo), null, modifier, contentScale = ContentScale.Fit)
}
