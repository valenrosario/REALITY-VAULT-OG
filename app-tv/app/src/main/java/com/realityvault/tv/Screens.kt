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
import androidx.compose.material3.Icon
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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/* ---------------- navegación ---------------- */

sealed class Screen {
    data object Splash : Screen()
    data object Home : Screen()
    data object Series : Screen()
    data object Movies : Screen()
    data object MyList : Screen()
    data object Search : Screen()
    data object Account : Screen()
    data class Detail(val id: String) : Screen()
    data class Player(val titleId: String, val url: String, val season: Int?, val episode: Int?, val trailer: Boolean = false) : Screen()
    /** required = true: es la puerta de entrada obligatoria, no se puede saltear. */
    data class Login(val then: Screen? = null, val required: Boolean = false) : Screen()
    data class Person(val name: String) : Screen()
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
    val stack = mutableStateListOf<Screen>(Screen.Splash)
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

    Column(Modifier.fillMaxSize().padding(start = 48.dp, top = 74.dp)) {
        Text(title, style = ts(24, FontWeight.Bold, Color.White))
        Text("${list.size} título${if (list.size == 1) "" else "s"}", style = ts(12, color = C.muted))
        if (kind == "milista" && Store.session == null) {
            Spacer(Modifier.height(24.dp))
            Text("Entrá con tu cuenta para guardar series en tu lista.", style = ts(15, color = C.fg2))
            Spacer(Modifier.height(16.dp))
            TextButtonTv("Entrar", { nav.go(Screen.Login()) }, requester = firstReq)
            AutoFocus(firstReq, kind)
            return@Column
        }
        if (genres.isNotEmpty()) {
            LazyRow(contentPadding = PaddingValues(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Chip("Todo", genre.isBlank()) { genre = "" } }
                items(genres) { g -> Chip(g, genre == g) { genre = g } }
            }
        } else Spacer(Modifier.height(16.dp))
        if (list.isEmpty()) { Text(if (kind == "milista") "Todavía no agregaste nada." else "No hay títulos.", style = ts(15, color = C.fg2)); return@Column }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(100.dp), state = nav.memory.grid(kind + genre),
            contentPadding = PaddingValues(top = 10.dp, end = 48.dp, bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(16.dp)
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
    Focusable(onClick, Modifier.height(30.dp), shape = RoundedCornerShape(99.dp), scale = 1.06f, ring = false) { f ->
        Box(
            Modifier.background(if (f) Color.White else if (on) C.accent else Color.White.copy(alpha = .1f)).padding(horizontal = 13.dp).height(30.dp),
            contentAlignment = Alignment.Center
        ) { Text(text, style = ts(12, FontWeight.Bold, if (f || on) Color.Black else C.fg)) }
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
    Row(Modifier.fillMaxSize().padding(start = 48.dp, top = 74.dp, end = 48.dp)) {
        Column(Modifier.width(420.dp)) {
            Text("Buscar", style = ts(24, FontWeight.Bold, Color.White))
            Spacer(Modifier.height(14.dp))
            TvFieldDisplay("BUSCAR", q, "Títulos, géneros…")
            Spacer(Modifier.height(14.dp))
            TvKeyboard(
                onChar = { c -> q += c; nav.memory.focus["searchQ"] = q },
                onBackspace = { q = q.dropLast(1); nav.memory.focus["searchQ"] = q },
                onDone = {}, doneLabel = "Listo",
                firstReq = req
            )
        }
        Column(Modifier.fillMaxWidth().padding(start = 40.dp, top = 38.dp)) {
            if (n.isNotBlank() && res.isEmpty()) Text("No encontramos nada con “$q”.", style = ts(14, color = C.fg2))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(100.dp), contentPadding = PaddingValues(top = 10.dp, bottom = 40.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(res, key = { it.id }) { t ->
                    Focusable({ nav.go(Screen.Detail(t.id)) }, Modifier.aspectRatio(2f / 3f)) { PosterCard(t) }
                }
            }
        }
    }
}

/* ---------------- cuenta ---------------- */

@Composable
fun EmailLogin(nav: Nav, done: () -> Unit, note: String?, required: Boolean = false, onBack: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var active by remember { mutableStateOf("email") }
    var err by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val kbReq = remember { FocusRequester() }
    AutoFocus(kbReq)
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
        Row(Modifier.width(900.dp), horizontalArrangement = Arrangement.spacedBy(56.dp)) {
            Column(Modifier.width(380.dp)) {
                if (Store.siteLogo.isNotBlank()) Img(Store.siteLogo, Modifier.width(220.dp).height(42.dp), scale = ContentScale.Fit, alignment = Alignment.CenterStart)
                else LocalLogo(Modifier.width(220.dp).height(42.dp))
                Spacer(Modifier.height(18.dp))
                Text(note ?: "Entrá con tu cuenta para ver series y películas", style = ts(14, color = C.fg2))
                Spacer(Modifier.height(22.dp))
                TvFieldChip("EMAIL", email, active == "email") { active = "email" }
                Spacer(Modifier.height(10.dp))
                TvFieldChip("CONTRASEÑA", pass, active == "password", masked = true) { active = "password" }
                if (err.isNotBlank()) { Spacer(Modifier.height(12.dp)); Text(err, style = ts(13, FontWeight.Bold, C.accent)) }
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    PillButton(if (busy) "Entrando…" else "Entrar", { run(false) }, icon = null)
                    TextButtonTv("Crear cuenta", { run(true) })
                }
                Spacer(Modifier.height(10.dp))
                TextButtonTv(if (note == null) "Volver" else "Ahora no", { if (note == null || required) onBack() else nav.back() })
            }
            TvKeyboard(
                onChar = { c -> if (active == "email") email += c else pass += c },
                onBackspace = { if (active == "email") email = email.dropLast(1) else pass = pass.dropLast(1) },
                onDone = { run(false) },
                doneLabel = "Entrar ✓",
                modifier = Modifier.width(460.dp),
                firstReq = kbReq
            )
        }
    }
}

@Composable
fun AccountScreen(nav: Nav) {
    val s = Store.session
    val req = remember { FocusRequester() }
    AutoFocus(req)
    Column(Modifier.fillMaxSize().padding(start = 48.dp, top = 74.dp)) {
        Text(if (s != null) "Mi cuenta" else "Entrar", style = ts(24, FontWeight.Bold, Color.White))
        Spacer(Modifier.height(18.dp))
        if (s != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Color(0xFFFFD1E6), C.accent, Color(0xFFB04C86)))), contentAlignment = Alignment.Center) {
                    Text("★", style = ts(26, color = Color.White))
                }
                Spacer(Modifier.width(16.dp))
                Text(s.email, style = ts(16, FontWeight.Bold))
            }
            Spacer(Modifier.height(26.dp))
            TextButtonTv("Cerrar sesión", { Store.logout(); nav.section(Screen.Home) }, requester = req)
        } else {
            Text("Con tu cuenta podés ver capítulos, guardar series en Mi lista y seguir donde dejaste.", style = ts(15, color = C.fg2), modifier = Modifier.width(560.dp))
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
