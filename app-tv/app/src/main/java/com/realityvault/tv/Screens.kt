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
import androidx.compose.ui.unit.sp
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
    data class Player(val titleId: String, val url: String, val season: Int?, val episode: Int?, val trailer: Boolean = false, val extra: Boolean = false, val restart: Boolean = false) : Screen()
    /** required = true: es la puerta de entrada obligatoria, no se puede saltear. */
    data class Login(val then: Screen? = null, val required: Boolean = false) : Screen()
    data class Person(val name: String) : Screen()
    data class Collection(val id: String) : Screen()
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
    // abrir una ficha de nuevo arranca siempre desde la primera temporada
    fun go(s: Screen) { if (s is Screen.Detail) memory.focus.remove("season-" + s.id); stack.add(s) }
    fun section(s: Screen) { stack.clear(); stack.add(s) }
    fun back(): Boolean { if (stack.size > 1) { stack.removeAt(stack.lastIndex); return true }; return false }
    fun replace(s: Screen) { stack[stack.lastIndex] = s }

    /** Ver algo: pide cuenta igual que el sitio. */
    fun play(t: Title, url: String, season: Int?, episode: Int?, restart: Boolean = false) {
        val p = Screen.Player(t.id, url, season, episode, restart = restart)
        if (Store.session == null) go(Screen.Login(p)) else go(p)
    }

    /** Ver un extra de temporada: no cuenta para "Seguir viendo" ni para el siguiente capítulo. */
    fun playExtra(t: Title, url: String) {
        val p = Screen.Player(t.id, url, null, null, extra = true)
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
            // Bastante más separación vertical que horizontal: el póster enfocado de la fila de
            // arriba crece hacia abajo al agrandarse (ver pivotTop en Focusable), y con poco
            // margen terminaba chocando contra la fila de abajo.
            horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(34.dp)
        ) {
            items(list, key = { it.id }) { t ->
                val k = "$kind/${t.id}"
                val req = remember { FocusRequester() }
                if (k == restore || (restore == null && t.id == list.first().id)) AutoFocus(req, k)
                Focusable({ nav.go(Screen.Detail(t.id)) }, Modifier.aspectRatio(2f / 3f), requester = req, onFocus = { nav.memory.focus[kind] = k }, pivotTop = true) {
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

/* ---------------- colección ---------------- */

@Composable
fun CollectionScreen(id: String, nav: Nav) {
    if (!Store.loaded) { Loading(); return }
    val pub = Store.published()
    val c = Store.colls.firstOrNull { it.id == id }
    val list = c?.items?.mapNotNull { i -> pub.firstOrNull { it.id == i } } ?: emptyList()
    val key = "coll-$id"
    val restore = nav.memory.focus[key]
    val firstReq = remember { FocusRequester() }
    if (c == null || list.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(start = 48.dp, top = 74.dp)) {
            Text("Esta colección no está disponible.", style = ts(18, FontWeight.Bold, Color.White))
            Spacer(Modifier.height(16.dp))
            TextButtonTv("Volver", { nav.back() }, requester = firstReq)
            AutoFocus(firstReq, key)
        }
        return
    }
    // Portada grande (imagen de la colección + logo) y abajo una fila con los títulos, en el orden elegido.
    val art = c.image.ifBlank { list.firstNotNullOfOrNull { it.tvBg.ifBlank { null } } ?: list.first().poster }
    val tint = c.colorArgb?.let { androidx.compose.ui.graphics.Color(it) } ?: Color(0xFF0A090B)
    Column(Modifier.fillMaxSize().padding(top = 66.dp)) {
        Box(Modifier.padding(horizontal = 48.dp).fillMaxWidth().height(262.dp).clip(RoundedCornerShape(18.dp)).background(C.surface)) {
            if (art.isNotBlank()) Img(art, Modifier.fillMaxSize(), alignment = Alignment.TopCenter)
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color.Black.copy(alpha = .65f), .6f to Color.Transparent)))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, .55f to Color.Transparent, 1f to tint.copy(alpha = .92f))))
            Column(Modifier.align(Alignment.BottomStart).padding(start = 30.dp, bottom = 22.dp, end = 30.dp).width(520.dp)) {
                Text("COLECCIÓN", style = ts(11, FontWeight.Bold, C.accent).copy(letterSpacing = 2.sp))
                if (c.logo.isNotBlank()) Img(c.logo, Modifier.padding(top = 6.dp).width(260.dp).height(62.dp), scale = ContentScale.Fit, alignment = Alignment.CenterStart)
                else Text(c.name, style = ts(30, FontWeight.Bold, Color.White), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                if (c.tagline.isNotBlank()) Text(c.tagline, style = ts(13, color = Color.White.copy(alpha = .85f)), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                Text("${list.size} título${if (list.size == 1) "" else "s"}", style = ts(12, color = Color.White.copy(alpha = .6f)), modifier = Modifier.padding(top = 6.dp))
            }
        }
        LazyRow(
            state = nav.memory.list(key), contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 22.dp, bottom = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(list, key = { it.id }) { t ->
                val k = "$key/${t.id}"
                val req = remember { FocusRequester() }
                if (k == restore || (restore == null && t.id == list.first().id)) AutoFocus(req, k)
                Focusable({ nav.go(Screen.Detail(t.id)) }, Modifier.width(112.dp).aspectRatio(2f / 3f), requester = req, onFocus = { nav.memory.focus[key] = k }) {
                    PosterCard(t)
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
    var q by remember { mutableStateOf(nav.memory.focus["searchQ"] ?: "") }
    val req = remember { FocusRequester() }
    if (nav.memory.focus["search"] == null) AutoFocus(req)
    val pub = Store.published()
    val n = q.trim().lowercase()
    val res = if (n.isBlank()) emptyList() else searchTitles(q, pub)
    // sin nada escrito se muestran los más vistos (o los más nuevos), así la pantalla nunca queda vacía
    val shown = if (n.isBlank()) Store.top10.mapNotNull { id -> pub.firstOrNull { it.id == id } }.ifEmpty { pub.sortedByDescending { it.createdAt }.take(12) } else res
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
            if (shown.isNotEmpty()) Text(if (n.isBlank()) "Popular ahora" else "${shown.size} resultado${if (shown.size == 1) "" else "s"}", style = ts(13, FontWeight.Bold, Color.White.copy(alpha = .85f)))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(100.dp), contentPadding = PaddingValues(top = 10.dp, bottom = 40.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(34.dp)
            ) {
                items(shown, key = { it.id }) { t ->
                    Focusable({ nav.go(Screen.Detail(t.id)) }, Modifier.aspectRatio(2f / 3f), pivotTop = true) { PosterCard(t) }
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
            try {
                if (signup) {
                    val ok = Store.signup(email.trim(), pass)
                    // si el proyecto pide confirmar el email, no hay sesión todavía: se lo decimos en vez de seguir como si nada
                    if (!ok && Store.session == null) { err = "Te mandamos un email para confirmar la cuenta. Confirmalo y después tocá Entrar."; busy = false; return@launch }
                } else Store.login(email.trim(), pass)
                done()
            }
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
                    val ph = avatarRes(s.avatar)
                    if (ph != null) androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(ph), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Text("★", style = ts(26, color = Color.White))
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
        Spacer(Modifier.height(40.dp))
        AboutSection()
    }
}

/* ---------------- acerca de / actualizaciones ---------------- */

@Composable
private fun AboutSection() {
    val scope = rememberCoroutineScope()
    Column(Modifier.width(560.dp)) {
        Text("Acerca de", style = ts(16, FontWeight.Bold, Color.White))
        Spacer(Modifier.height(8.dp))
        Text("Versión ${BuildConfig.VERSION_NAME}", style = ts(13, color = C.muted))
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            TextButtonTv(if (Updater.checking) "Buscando…" else "Buscar actualizaciones", { scope.launch { Updater.checkManually() } })
            // Herramienta de prueba: muestra el aviso real (con el manifiesto/APK publicados de
            // verdad) sin esperar a que exista una versión nueva. Pensada para verificar el
            // diálogo y la descarga/instalación sin tener que subir una build falsa.
        }
        Updater.lastResult?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, style = ts(13, color = C.fg2))
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

/** Pantalla que ven todos menos el admin cuando el panel tiene el sitio "cerrado". */
@Composable
fun ClosedScreen(nav: Nav, onExit: () -> Unit) {
    BackHandler { onExit() }
    val req = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    AutoFocus(req)
    Box(Modifier.fillMaxSize().background(C.bg), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (Store.siteLogo.isNotBlank()) {
                coil.compose.AsyncImage(Store.siteLogo, null, Modifier.height(70.dp).width(300.dp), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
                Spacer(Modifier.height(34.dp))
            }
            Text("No disponible por ahora", style = ts(40, FontWeight.Bold, Color.White))
            Spacer(Modifier.height(12.dp))
            Text(Store.closedMsg.ifBlank { "Estamos haciendo cambios. Volvé a intentar más tarde." }, style = ts(18, color = C.muted))
            Spacer(Modifier.height(36.dp))
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp)) {
                TextButtonTv("Reintentar", { scope.launch { Store.refreshClosed() } }, requester = req)
                if (Store.session == null) TextButtonTv("Soy el administrador", { nav.go(Screen.Login()) })
                TextButtonTv("Salir", onExit)
            }
        }
    }
}
