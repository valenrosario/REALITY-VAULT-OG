package com.realityvault.tv

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * Perfiles, igual en la tele y en el celular (este archivo se comparte entre las dos apps), al estilo Peacock:
 * cada foto de perfil tiene su propio resplandor, del color más fuerte de la imagen.
 *  - "¿Quién está mirando?": los perfiles en una columna a la izquierda; a la derecha un título destacado
 *    teñido con el color del perfil enfocado (en el celular, una grilla).
 *  - Crear: 1) elegir la foto (carrusel con la foto enfocada grande), 2) el nombre (teclado propio en la tele),
 *    3) los títulos que le gustan (arma las recomendaciones).
 * Se guardan en la cuenta, así que la web ve los mismos perfiles.
 */

/** Para abrir la pantalla de perfiles desde cualquier lado ("pick", "manage", "new"). */
object PfUi { var open by mutableStateOf("") }

private val PfPink = Color(0xFFFF3D8B)
private val PfBg = Color(0xFF07060B)
private fun pfText(size: Int, bold: Boolean = false, color: Color = Color.White) =
    TextStyle(fontSize = size.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, color = color)

private fun pfHue(id: String): Float { var h = 0L; id.forEach { h = (h * 31 + it.code) % 360 }; return h.toFloat() }

/* ---------- color más fuerte de cada foto (para el resplandor) ---------- */
private val domCache = HashMap<String, Color>()
private fun dominantOf(bmp: android.graphics.Bitmap): Color {
    val bins = FloatArray(24); val rs = FloatArray(24); val gs = FloatArray(24); val bs = FloatArray(24)
    val hsv = FloatArray(3)
    val w = bmp.width; val h = bmp.height
    val step = maxOf(1, minOf(w, h) / 40)
    var y = 0
    while (y < h) {
        var x = 0
        while (x < w) {
            val c = bmp.getPixel(x, y)
            if (android.graphics.Color.alpha(c) > 200) {
                android.graphics.Color.colorToHSV(c, hsv)
                val wgt = hsv[1] * hsv[1] * hsv[2]          // los colores vivos pesan más que los grises
                if (wgt > .05f) {
                    val b = ((hsv[0] / 15f).toInt()).coerceIn(0, 23)
                    bins[b] += wgt; rs[b] += android.graphics.Color.red(c) * wgt; gs[b] += android.graphics.Color.green(c) * wgt; bs[b] += android.graphics.Color.blue(c) * wgt
                }
            }
            x += step
        }
        y += step
    }
    val best = bins.indices.maxByOrNull { bins[it] } ?: return PfPink
    if (bins[best] <= 0f) return PfPink
    val r = rs[best] / bins[best]; val g = gs[best] / bins[best]; val bl = bs[best] / bins[best]
    android.graphics.Color.RGBToHSV(r.toInt(), g.toInt(), bl.toInt(), hsv)
    hsv[1] = (hsv[1] * 1.15f).coerceIn(.55f, 1f); hsv[2] = hsv[2].coerceIn(.75f, 1f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/** El color fuerte de una foto (lo calcula una vez y lo recuerda). */
@Composable
fun rememberGlow(p: Profile?): Color {
    val url = p?.avatar.orEmpty()
    val fallback = if (p == null) PfPink else Color.hsv(pfHue(p.id), .7f, .95f)
    var c by remember(url) { mutableStateOf(domCache[url] ?: fallback) }
    val ctx = LocalContext.current
    LaunchedEffect(url) {
        if (url.isBlank() || domCache.containsKey(url)) return@LaunchedEffect
        runCatching {
            val req = coil.request.ImageRequest.Builder(ctx).data(url).size(64).allowHardware(false).build()
            val d = coil.Coil.imageLoader(ctx).execute(req).drawable
            val bmp = (d as? android.graphics.drawable.BitmapDrawable)?.bitmap ?: return@runCatching
            val col = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { dominantOf(bmp) }
            domCache[url] = col; c = col
        }
    }
    return c
}

@Composable
fun ProfileAvatar(p: Profile?, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(CircleShape).background(
        if (p == null || p.avatar.isBlank()) Brush.linearGradient(listOf(Color.hsv(pfHue(p?.id ?: "x"), .55f, .98f), Color.hsv((pfHue(p?.id ?: "x") + 40) % 360, .7f, .6f)))
        else SolidColor(Color(0xFF2A2633))
    ), contentAlignment = Alignment.Center) {
        if (p != null && p.avatar.isNotBlank()) coil.compose.AsyncImage(p.avatar, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text((p?.name?.firstOrNull() ?: '?').uppercase(), style = pfText((size.value * .4f).toInt(), true))
    }
}

/** Foto con su resplandor (del color más fuerte de la imagen). */
@Composable
private fun GlowAvatar(p: Profile?, size: Dp, glow: Float, modifier: Modifier = Modifier) {
    val c = rememberGlow(p)
    Box(modifier.size(size * 1.7f), contentAlignment = Alignment.Center) {
        if (glow > 0f) Box(Modifier.size(size * 1.7f).background(Brush.radialGradient(listOf(c.copy(alpha = .75f * glow), c.copy(alpha = .25f * glow), Color.Transparent)), CircleShape))
        ProfileAvatar(p, size)
    }
}

/** Botón que se ve igual con el control de la tele (foco) y con el dedo. */
@Composable
private fun PfTap(onClick: () -> Unit, modifier: Modifier = Modifier, req: FocusRequester? = null, onFocus: (() -> Unit)? = null, content: @Composable BoxScope.(Boolean) -> Unit) {
    val src = remember { MutableInteractionSource() }
    val f by src.collectIsFocusedAsState()
    Box(modifier.then(if (req != null) Modifier.focusRequester(req) else Modifier)
        .onFocusChanged { if (it.isFocused) onFocus?.invoke() }
        .clickable(interactionSource = src, indication = null, onClick = onClick), contentAlignment = Alignment.Center) { content(f) }
}

@Composable
private fun PfButton(text: String, primary: Boolean = false, danger: Boolean = false, req: FocusRequester? = null, onClick: () -> Unit) {
    PfTap(onClick, req = req) { f ->
        val bg = when { f -> Color.White; primary -> Color(0xFFF2EC3B); else -> Color.White.copy(alpha = .1f) }
        Box(Modifier.scale(if (f) 1.06f else 1f).height(40.dp).background(bg, RoundedCornerShape(99.dp))
            .border(1.dp, if (danger && !f) Color(0x80FF7A9C) else Color.Transparent, RoundedCornerShape(99.dp)).padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
            Text(text, style = pfText(14, true, when { f || primary -> Color(0xFF111111); danger -> Color(0xFFFF7A9C); else -> Color.White }), maxLines = 1)
        }
    }
}

private suspend fun focusSoon(r: FocusRequester) { repeat(8) { if (runCatching { r.requestFocus() }.isSuccess) return; delay(60) } }

private class PfDraft(val isNew: Boolean, val id: String, name: String, avatar: String, likes: List<String>) {
    var name by mutableStateOf(name); var avatar by mutableStateOf(avatar)
    val likes = mutableStateListOf<String>().apply { addAll(likes) }
    fun asProfile() = Profile(id, name.ifBlank { "?" }, avatar, likes.toList())
}

/** Fondo negro con un brillo grande del color [c] (se mueve suave cuando cambia). */
@Composable
private fun GlowBackdrop(c: Color, wide: Boolean, center: Offset? = null) {
    val col by animateColorAsState(c, tween(500), label = "bg")
    Box(Modifier.fillMaxSize().background(PfBg))
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat(); val h = constraints.maxHeight.toFloat()
        val ctr = center ?: if (wide) Offset(w * .78f, h * .55f) else Offset(w * .5f, h * .3f)
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(col.copy(alpha = .55f), col.copy(alpha = .18f), Color.Transparent), center = ctr, radius = maxOf(w, h) * .62f)))
    }
}

/**
 * Pantalla completa de perfiles. startMode: "pick" (al abrir la app), "manage" o "new".
 * onClose se llama al elegir un perfil o al salir.
 */
@Composable
fun ProfileGate(startMode: String, logo: String, onAccount: (() -> Unit)? = null, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    // pick · avatar · name · taste
    var screen by remember { mutableStateOf("pick") }
    var draft by remember { mutableStateOf<PfDraft?>(null) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    LaunchedEffect(msg) { if (msg.isNotBlank()) { delay(2600); msg = "" } }
    fun newDraft() {
        if (Store.profiles.size >= 5) { msg = "Podés tener hasta 5 perfiles"; return }
        val ch = Store.avatarChoices(); draft = PfDraft(true, Store.newProfileId(), "", ch[Store.profiles.size % ch.size], emptyList()); screen = "avatar"
    }
    LaunchedEffect(Unit) { if (startMode == "new") newDraft() }
    fun leaveEdit() { draft = null; if (startMode == "new") onClose() else screen = "pick" }
    fun save(d: PfDraft, then: () -> Unit) {
        busy = true
        scope.launch {
            val p = Profile(d.id, d.name.trim().take(20), d.avatar, d.likes.take(40))
            val list = Store.profiles.toMutableList(); val i = list.indexOfFirst { it.id == p.id }
            if (i < 0) list += p else list[i] = p
            runCatching { Store.saveProfiles(list) }.onSuccess { if (d.isNew) { Store.useProfile(p); onClose() } else then() }.onFailure { msg = "No se pudo guardar el perfil" }
            busy = false
        }
    }

    BackHandler {
        when (screen) {
            "avatar" -> if (draft?.isNew == true) leaveEdit() else screen = "name"
            "name" -> if (draft?.isNew == true) screen = "avatar" else leaveEdit()
            "taste" -> screen = "name"
            else -> { Store.askProfile = false; onClose() }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(PfBg)) {
        val wide = maxWidth > maxHeight
        val d = draft
        when {
            screen == "avatar" && d != null -> AvatarPicker(d, wide) { if (d.isNew) screen = "name" else screen = "name" }
            screen == "name" && d != null -> NameStep(d, wide, busy,
                onPick = { screen = "avatar" },
                onNext = { if (d.name.isBlank()) msg = "Ponele un nombre" else screen = "taste" },
                onSave = { if (d.name.isBlank()) msg = "Ponele un nombre" else save(d) { draft = null; screen = "pick" } },
                onDelete = {
                    busy = true
                    scope.launch {
                        Store.profiles.firstOrNull { it.id == d.id }?.let { runCatching { Store.deleteProfile(it) }.onFailure { msg = "No se pudo eliminar" } }
                        draft = null; screen = "pick"; busy = false
                    }
                })
            screen == "taste" && d != null -> TastePicker(d, wide, busy) { if (d.name.isBlank()) { msg = "Ponele un nombre"; screen = "name" } else save(d) { draft = null; screen = "pick" } }
            else -> WhoIsWatching(wide, logo,
                onUse = { p -> scope.launch { Store.useProfile(p); onClose() } },
                onEdit = { p -> draft = PfDraft(false, p.id, p.name, p.avatar, p.likes); screen = "name" },
                onAdd = { newDraft() },
                onAccount = if (Store.askProfile) null else onAccount)
        }
        if (msg.isNotBlank()) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 30.dp).background(Color(0xEE1A1520), RoundedCornerShape(99.dp)).padding(horizontal = 22.dp, vertical = 12.dp)) {
            Text(msg, style = pfText(15, true))
        }
    }
}

/* ---------------- ¿Quién está mirando? ---------------- */

@Composable
private fun WhoIsWatching(wide: Boolean, logo: String, onUse: (Profile) -> Unit, onEdit: (Profile) -> Unit, onAdd: () -> Unit, onAccount: (() -> Unit)?) {
    val profiles = Store.profiles
    var focused by remember { mutableStateOf<Profile?>(Store.profile ?: profiles.firstOrNull()) }
    var onPlus by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusSoon(first) }
    val glow = if (onPlus) Color(0xFFF2EC3B) else rememberGlow(focused)
    // un título destacado para la derecha (como Peacock)
    val feat = remember { Store.published().filter { (it.backdrop.isNotBlank()) && it.logo.isNotBlank() }.shuffled().firstOrNull() }

    if (wide) Box(Modifier.fillMaxSize()) {
        GlowBackdrop(glow, true)
        // derecha: destacado (o, parado en "+", el cartel de crear perfil)
        Box(Modifier.fillMaxSize().padding(start = 300.dp)) {
            if (!onPlus && feat != null) {
                coil.compose.AsyncImage(feat.backdrop, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = .9f)
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(PfBg, glow.copy(alpha = .35f), Color.Transparent), endX = 700f)))
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, PfBg.copy(alpha = .85f)))))
                Column(Modifier.align(Alignment.BottomEnd).padding(end = 48.dp, bottom = 44.dp), horizontalAlignment = Alignment.End) {
                    coil.compose.AsyncImage(feat.logo, feat.title, Modifier.height(80.dp).widthIn(max = 320.dp), contentScale = ContentScale.Fit, alignment = Alignment.CenterEnd)
                    val meta = listOf(feat.genres.firstOrNull().orEmpty(), feat.year).filter { it.isNotBlank() }.joinToString(" · ")
                    if (meta.isNotBlank()) Text(meta, style = pfText(14, true), modifier = Modifier.padding(top = 10.dp))
                }
            } else Column(Modifier.align(Alignment.Center).padding(horizontal = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Creá hasta 5 perfiles", style = pfText(36, true), textAlign = TextAlign.Center)
                Text("Cada persona tiene sus propias recomendaciones, su lista y lo que viene mirando.", style = pfText(17, false, Color.White.copy(alpha = .85f)), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 14.dp).widthIn(max = 460.dp))
            }
        }
        // izquierda: la columna de perfiles
        Box(Modifier.width(300.dp).fillMaxHeight().background(Brush.horizontalGradient(listOf(PfBg, PfBg.copy(alpha = .92f), Color.Transparent))))
        Column(Modifier.width(300.dp).fillMaxHeight().padding(start = 48.dp, top = 34.dp)) {
            Text("¿Quién está mirando?", style = pfText(24, true))
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 30.dp)) {
                itemsIndexed(profiles) { i, p ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PfTap({ onEdit(p) }, Modifier.size(36.dp)) { f ->
                            Box(Modifier.fillMaxSize().background(if (f) Color.White else Color.White.copy(alpha = .1f), CircleShape), contentAlignment = Alignment.Center) {
                                Text("✎", style = pfText(15, true, if (f) Color(0xFF111111) else Color.White.copy(alpha = .8f)))
                            }
                        }
                        PfTap({ onUse(p) }, req = if (p.id == (Store.profile ?: profiles.first()).id) first else null, onFocus = { focused = p; onPlus = false }) { f ->
                            val s by animateFloatAsState(if (f) 1.12f else 1f, tween(180), label = "pf")
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(start = 6.dp)) {
                                GlowAvatar(p, 92.dp, if (f) 1f else .0f, Modifier.scale(s))
                                Text(p.name, style = pfText(16, true, if (f) Color.White else Color.White.copy(alpha = .65f)), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.offset(y = (-14).dp))
                            }
                        }
                    }
                }
                if (profiles.size < 5) item {
                    PfTap(onAdd, Modifier.padding(start = 42.dp), onFocus = { onPlus = true }) { f ->
                        val s by animateFloatAsState(if (f) 1.12f else 1f, tween(180), label = "pl")
                        Box(Modifier.padding(vertical = 18.dp).scale(s).size(92.dp).background(if (f) Color(0xFFF2EC3B) else Color.White.copy(alpha = .12f), CircleShape), contentAlignment = Alignment.Center) {
                            Text("+", style = pfText(40, false, if (f) Color(0xFF111111) else Color.White))
                        }
                    }
                }
            }
        }
        if (onAccount != null) Box(Modifier.align(Alignment.TopEnd).padding(top = 26.dp, end = 40.dp)) { PfButton("⚙  Mi cuenta", onClick = onAccount) }
        if (logo.isNotBlank()) coil.compose.AsyncImage(logo, null, Modifier.align(Alignment.BottomStart).padding(start = 48.dp, bottom = 26.dp).height(20.dp).widthIn(max = 160.dp), contentScale = ContentScale.Fit)
    } else Box(Modifier.fillMaxSize()) {
        GlowBackdrop(glow, false)
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (logo.isNotBlank()) coil.compose.AsyncImage(logo, null, Modifier.height(22.dp).widthIn(max = 200.dp), contentScale = ContentScale.Fit)
            Spacer(Modifier.height(36.dp))
            Text("¿Quién está mirando?", style = pfText(26, true))
            Spacer(Modifier.height(20.dp))
            val items = profiles.map { it as Profile? } + (if (profiles.size < 5) listOf(null) else emptyList())
            items.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { p ->
                        Box(Modifier.width(150.dp)) {
                            PfTap({ if (p == null) onAdd() else onUse(p) }, Modifier.fillMaxWidth(), onFocus = { if (p != null) focused = p }) { f ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    if (p == null) Box(Modifier.padding(25.dp).size(100.dp).background(Color.White.copy(alpha = .12f), CircleShape), contentAlignment = Alignment.Center) { Text("+", style = pfText(40)) }
                                    else GlowAvatar(p, 100.dp, if (p.id == focused?.id) 1f else .45f)
                                    Text(p?.name ?: "Agregar", style = pfText(16, true, Color.White.copy(alpha = if (p != null && p.id == focused?.id) 1f else .75f)), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.offset(y = (-16).dp))
                                }
                            }
                            if (p != null) PfTap({ onEdit(p) }, Modifier.align(Alignment.TopEnd).padding(top = 22.dp, end = 18.dp).size(34.dp)) { _ ->
                                Box(Modifier.fillMaxSize().background(Color.White, CircleShape), contentAlignment = Alignment.Center) { Text("✎", style = pfText(15, true, Color(0xFF111111))) }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            if (onAccount != null) PfButton("Mi cuenta", onClick = onAccount)
        }
    }
}

/* ---------------- elegir la foto ---------------- */

@Composable
private fun AvatarPicker(d: PfDraft, wide: Boolean, onDone: () -> Unit) {
    val all = remember { (Store.avatarLib + Store.avatarDefaults).distinct() }
    val startIdx = all.indexOf(d.avatar).coerceAtLeast(0)
    var cur by remember { mutableIntStateOf(startIdx) }
    val glow = rememberGlow(Profile(d.id, d.name, all.getOrElse(cur) { "" }, emptyList()))
    val reqs = remember { all.map { FocusRequester() } }
    val list = rememberLazyListState(maxOf(0, startIdx - 1))
    LaunchedEffect(Unit) { delay(80); focusSoon(reqs[startIdx]) }
    Box(Modifier.fillMaxSize()) {
        GlowBackdrop(glow, wide, null)
        Column(Modifier.fillMaxSize().padding(top = if (wide) 40.dp else 56.dp)) {
            Text("Elegí tu foto", style = pfText(if (wide) 30 else 26, true), modifier = Modifier.padding(start = if (wide) 64.dp else 22.dp))
            Text(if (d.isNew) "Después le ponés el nombre" else d.name, style = pfText(15, false, Color.White.copy(alpha = .7f)), modifier = Modifier.padding(start = if (wide) 64.dp else 22.dp, top = 4.dp))
            Spacer(Modifier.weight(1f))
            val big = if (wide) 230.dp else 190.dp; val small = if (wide) 120.dp else 92.dp
            LazyRow(state = list, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (wide) 34.dp else 18.dp),
                contentPadding = PaddingValues(horizontal = if (wide) 64.dp else 24.dp), modifier = Modifier.fillMaxWidth().height(big * 1.5f)) {
                itemsIndexed(all) { i, u ->
                    val on = i == cur
                    val sz by animateFloatAsState(if (on) big.value else small.value, tween(260), label = "av")
                    PfTap({ d.avatar = u; onDone() }, req = reqs[i], onFocus = { cur = i }) { _ ->
                        val pr = Profile(d.id, d.name, u, emptyList())
                        if (on) GlowAvatar(pr, sz.dp, 1f) else Box(Modifier.size(sz.dp * 1.7f), contentAlignment = Alignment.Center) { ProfileAvatar(pr, sz.dp, Modifier.clickable { cur = i }) }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Text(if (wide) "Elegí con las flechas y apretá OK" else "Tocá una foto para elegirla", style = pfText(14, false, Color.White.copy(alpha = .6f)),
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 34.dp))
        }
    }
}

/* ---------------- nombre ---------------- */

private val KeyRows = listOf("qwertyuiop", "asdfghjklñ", "zxcvbnm")

@Composable
private fun NameStep(d: PfDraft, wide: Boolean, busy: Boolean, onPick: () -> Unit, onNext: () -> Unit, onSave: () -> Unit, onDelete: () -> Unit) {
    val glow = rememberGlow(d.asProfile())
    val firstKey = remember { FocusRequester() }
    val field = remember { FocusRequester() }
    var upper by remember { mutableStateOf(d.name.isEmpty()) }
    var sure by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { focusSoon(if (wide) firstKey else field) }
    fun type(s: String) { if (d.name.length < 20) { d.name += if (upper) s.uppercase() else s; upper = false } }
    Box(Modifier.fillMaxSize()) {
        GlowBackdrop(glow, true, null)
        // la foto arriba a la derecha (tocándola se cambia)
        PfTap(onPick, Modifier.align(Alignment.TopEnd).padding(top = if (wide) 20.dp else 40.dp, end = if (wide) 40.dp else 10.dp)) { f ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                GlowAvatar(d.asProfile(), if (wide) 120.dp else 92.dp, if (f) 1f else .6f, Modifier.scale(if (f) 1.06f else 1f))
                Text("Cambiar foto", style = pfText(13, f, Color.White.copy(alpha = if (f) 1f else .7f)), modifier = Modifier.offset(y = (-18).dp))
            }
        }
        Column(Modifier.fillMaxSize().padding(start = if (wide) 64.dp else 22.dp, end = if (wide) 64.dp else 22.dp, top = if (wide) 44.dp else 60.dp, bottom = 24.dp)) {
            Text(if (d.isNew) "¿Cómo se llama\nel perfil?" else "Editar perfil", style = pfText(if (wide) 32 else 26, true))
            Spacer(Modifier.height(if (wide) 40.dp else 70.dp))
            if (wide) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(d.name.ifEmpty { " " }, style = pfText(54, true), maxLines = 1)
                    Box(Modifier.padding(start = 3.dp, bottom = 8.dp).width(2.dp).height(52.dp).background(Color.White.copy(alpha = .8f)))
                }
            } else {
                BasicTextField(d.name, { d.name = it.take(20) }, Modifier.fillMaxWidth().focusRequester(field), textStyle = pfText(40, true), singleLine = true, cursorBrush = SolidColor(Color.White),
                    decorationBox = { inner -> if (d.name.isEmpty()) Text("Nombre", style = pfText(40, true, Color.White.copy(alpha = .3f))); inner() })
            }
            Box(Modifier.fillMaxWidth().height(2.dp).background(Color.White.copy(alpha = .75f)))
            Spacer(Modifier.height(18.dp))
            if (wide) {
                // teclado propio, como Peacock (el de la tele tapa media pantalla)
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    KeyRows.forEachIndexed { ri, row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            row.forEachIndexed { ci, ch -> Key(if (upper) ch.uppercase() else ch.toString(), Modifier.weight(1f), if (ri == 0 && ci == 0) firstKey else null) { type(ch.toString()) } }
                            when (ri) {
                                1 -> Key("⌫", Modifier.weight(1f)) { d.name = d.name.dropLast(1); if (d.name.isEmpty()) upper = true }
                                2 -> { Key("Aa", Modifier.weight(1f)) { upper = !upper }; Key("espacio", Modifier.weight(2f)) { if (d.name.isNotEmpty() && !d.name.endsWith(" ")) type(" ") } }
                                else -> {}
                            }
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (d.isNew) PfButton("Siguiente", primary = true, onClick = onNext)
                else {
                    PfButton(if (busy) "Guardando…" else "Guardar", primary = true, onClick = onSave)
                    PfButton("Gustos", onClick = onNext)
                    if (d.id != "main") PfButton(if (sure) "¿Seguro? Tocá de nuevo" else "Eliminar perfil", danger = true) { if (sure) onDelete() else sure = true }
                }
            }
        }
    }
}

@Composable
private fun Key(label: String, modifier: Modifier, req: FocusRequester? = null, onClick: () -> Unit) {
    PfTap(onClick, modifier.height(42.dp), req = req) { f ->
        Box(Modifier.fillMaxSize().background(if (f) Color(0xFFF2EC3B) else Color(0xFF26303F).copy(alpha = .9f), RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) {
            Text(label, style = pfText(17, true, if (f) Color(0xFF111111) else Color.White))
        }
    }
}

/* ---------------- gustos ---------------- */

@Composable
private fun TastePicker(d: PfDraft, wide: Boolean, busy: Boolean, onSave: () -> Unit) {
    val glow = rememberGlow(d.asProfile())
    val pub = remember { Store.published().filter { it.poster.isNotBlank() } }
    val genres = remember { pub.flatMap { it.genres }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(6).map { it.key } }
    var genre by remember { mutableStateOf("") }
    val pool = remember(genre) { pub.filter { genre.isBlank() || genre in it.genres }.let { l -> l.filter { it.id in d.likes } + l.filter { it.id !in d.likes }.shuffled() } }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusSoon(first) }
    Box(Modifier.fillMaxSize()) {
        GlowBackdrop(glow, wide, null)
        Column(Modifier.fillMaxSize().padding(start = if (wide) 48.dp else 16.dp, top = if (wide) 30.dp else 54.dp, bottom = 16.dp)) {
            Text("Elegí los títulos que te gustan\npara tener mejores recomendaciones", style = pfText(if (wide) 24 else 20, true))
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (listOf("") + genres).take(if (wide) 7 else 4).forEach { g ->
                    PfTap({ genre = g }) { f ->
                        val on = g == genre
                        Box(Modifier.height(34.dp).background(if (f) Color.White else if (on) Color.White.copy(alpha = .18f) else Color.Transparent, RoundedCornerShape(99.dp))
                            .border(1.dp, Color.White.copy(alpha = if (on || f) 0f else .3f), RoundedCornerShape(99.dp)).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                            Text(g.ifBlank { "Todo" }, style = pfText(13, true, if (f) Color(0xFF111111) else Color.White))
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            val cell = @Composable { t: Title, req: FocusRequester? ->
                val on = t.id in d.likes
                PfTap({ if (on) d.likes.remove(t.id) else d.likes.add(t.id) }, Modifier.aspectRatio(2f / 3f), req = req) { f ->
                    val s by animateFloatAsState(if (f) 1.06f else 1f, tween(140), label = "po")
                    Box(Modifier.fillMaxSize().scale(s).clip(RoundedCornerShape(10.dp)).background(Color(0xFF221E2A))
                        .border(if (f) 3.dp else if (on) 2.dp else 0.dp, if (f) Color(0xFFF2EC3B) else Color.White, RoundedCornerShape(10.dp))) {
                        coil.compose.AsyncImage(t.poster, t.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        Box(Modifier.align(Alignment.TopEnd).padding(7.dp).size(24.dp).background(if (on) Color(0xFFF2EC3B) else Color.Black.copy(alpha = .45f), CircleShape)
                            .border(1.5.dp, Color.White.copy(alpha = if (on) 0f else .8f), CircleShape), contentAlignment = Alignment.Center) {
                            Text("✓", style = pfText(13, true, if (on) Color(0xFF111111) else Color.White))
                        }
                    }
                }
            }
            if (wide) LazyHorizontalGrid(GridCells.Fixed(2), Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(end = 48.dp, top = 4.dp, bottom = 4.dp)) {
                items(pool, key = { it.id }) { t -> cell(t, if (t == pool.firstOrNull()) first else null) }
            } else LazyVerticalGrid(GridCells.Adaptive(100.dp), Modifier.weight(1f).padding(end = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(pool, key = { it.id }) { t -> cell(t, if (t == pool.firstOrNull()) first else null) }
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp, end = if (wide) 48.dp else 16.dp), verticalAlignment = Alignment.CenterVertically) {
                PfButton(if (busy) "Guardando…" else if (d.likes.isEmpty()) "Saltear" else "Listo", primary = d.likes.isNotEmpty()) { if (!busy) onSave() }
                Spacer(Modifier.weight(1f))
                Text("${d.likes.size} elegido${if (d.likes.size == 1) "" else "s"}", style = pfText(14, true, Color(0xFFF2EC3B)))
            }
        }
    }
}
