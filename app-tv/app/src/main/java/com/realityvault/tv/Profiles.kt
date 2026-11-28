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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.material3.Icon
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
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
private val PfFont = androidx.compose.ui.text.font.FontFamily(
    androidx.compose.ui.text.font.Font(R.font.gg_bold, FontWeight.Bold),
    androidx.compose.ui.text.font.Font(R.font.gg_book, FontWeight.Normal))
private val PfYellow = Color(0xFFF2EC3B)
private fun pfText(size: Int, bold: Boolean = false, color: Color = Color.White) =
    TextStyle(fontFamily = PfFont, fontSize = size.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, color = color)

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

/** Foto con su resplandor (del color más fuerte de la imagen). El brillo se dibuja por fuera, sin ocupar lugar. */
@Composable
private fun GlowAvatar(p: Profile?, size: Dp, glow: Float, modifier: Modifier = Modifier) {
    val c = rememberGlow(p)
    val a by animateFloatAsState(glow, tween(300), label = "glow")
    ProfileAvatar(p, size, modifier.drawBehind {
        if (a > 0f) drawCircle(Brush.radialGradient(listOf(c.copy(alpha = .7f * a), c.copy(alpha = .22f * a), Color.Transparent), center = center, radius = this.size.minDimension * .95f),
            radius = this.size.minDimension * .95f, center = center)
    })
}

/** Lápiz dibujado (el carácter ✎ no existe en la letra de la tele y salía un óvalo). */
private val PencilIcon: androidx.compose.ui.graphics.vector.ImageVector by lazy {
    androidx.compose.ui.graphics.vector.ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .addPath(androidx.compose.ui.graphics.vector.addPathNodes("M4 20h4L19 9l-4-4L4 16v4z M13.5 6.5l4 4"), fill = null, stroke = SolidColor(Color.White),
            strokeLineWidth = 2f, strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round, strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round)
        .build()
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
        val bg = when { f -> Color.White; primary -> PfYellow; else -> Color(0xFF1E2230).copy(alpha = .92f) }
        Box(Modifier.scale(if (f) 1.06f else 1f).height(36.dp).background(bg, RoundedCornerShape(99.dp)).padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
            Text(text.uppercase(), style = pfText(13, true, when { f || primary -> Color(0xFF111111); danger -> Color(0xFFFF8FAE); else -> Color.White }).copy(letterSpacing = 1.2.sp), maxLines = 1)
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

/* ---------------- ¿Quién está mirando? (como Peacock) ---------------- */

/** Escena azul con luces desenfocadas (parado en "+"). */
@Composable
private fun BlueScene(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.background(Brush.linearGradient(listOf(Color(0xFF0A0F3A), Color(0xFF1B2BB8), Color(0xFF2747E0)), start = Offset(0f, 0f), end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)))) {
        val w = maxWidth; val h = maxHeight
        listOf(Triple(.18f, .3f, 110), Triple(.42f, .62f, 90), Triple(.24f, .82f, 80), Triple(.62f, .28f, 70), Triple(.8f, .75f, 120)).forEach { (x, y, r) ->
            Box(Modifier.offset(w * x - r.dp, h * y - r.dp).size((r * 2).dp).background(Brush.radialGradient(listOf(Color(0x553A5BFF), Color.Transparent)), CircleShape))
        }
    }
}

@Composable
private fun WhoIsWatching(wide: Boolean, logo: String, onUse: (Profile) -> Unit, onEdit: (Profile) -> Unit, onAdd: () -> Unit, onAccount: (() -> Unit)?) {
    val profiles = Store.profiles
    var focusedId by remember { mutableStateOf((Store.profile ?: profiles.firstOrNull())?.id ?: "") }
    val onPlus = focusedId == "+"
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusSoon(first) }
    val feat = remember { Store.published().filter { it.backdrop.isNotBlank() && it.logo.isNotBlank() }.shuffled().firstOrNull() }
    val plusA by animateFloatAsState(if (onPlus) 1f else 0f, tween(450), label = "plus")

    Box(Modifier.fillMaxSize().background(PfBg)) {
        // fondo: el título destacado a pantalla completa (o la escena azul parado en "+")
        if (feat != null) Box(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - plusA }) {
            coil.compose.AsyncImage(feat.backdrop, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to PfBg, (if (wide) .22f else 0f) to PfBg.copy(alpha = .96f), (if (wide) .48f else .5f) to Color.Transparent)))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to PfBg.copy(alpha = .5f), .25f to Color.Transparent, .7f to Color.Transparent, 1f to PfBg.copy(alpha = .9f))))
            if (wide) Column(Modifier.align(Alignment.BottomEnd).padding(end = 48.dp, bottom = 40.dp), horizontalAlignment = Alignment.Start) {
                coil.compose.AsyncImage(feat.logo, feat.title, Modifier.height(86.dp).widthIn(max = 330.dp), contentScale = ContentScale.Fit, alignment = Alignment.BottomStart)
                val meta = listOf(feat.genres.firstOrNull().orEmpty(), feat.year).filter { it.isNotBlank() }.joinToString(" · ")
                if (meta.isNotBlank()) Text(meta, style = pfText(13, true), modifier = Modifier.padding(top = 10.dp))
            }
        }
        if (plusA > 0f) Box(Modifier.fillMaxSize().graphicsLayer { alpha = plusA }) {
            BlueScene(Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to PfBg, (if (wide) .2f else 0f) to PfBg.copy(alpha = .9f), (if (wide) .42f else .5f) to Color.Transparent)))
            if (wide) Column(Modifier.align(Alignment.CenterEnd).padding(end = 70.dp).width(420.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Creá hasta 5 perfiles", style = pfText(36, true), textAlign = TextAlign.Center)
                Text("Cada persona de tu casa va a tener sus propias recomendaciones según lo que le gusta mirar.", style = pfText(17, false, Color.White.copy(alpha = .92f)), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 14.dp))
            }
        }

        // columna de perfiles
        Text("¿Quién está mirando?", style = pfText(if (wide) 22 else 24, true), modifier = Modifier.padding(start = if (wide) 48.dp else 24.dp, top = if (wide) 34.dp else 56.dp))
        Column(Modifier.fillMaxHeight().width(if (wide) 280.dp else 400.dp).padding(start = if (wide) 24.dp else 0.dp, top = 76.dp, bottom = 64.dp)
            .then(if (wide) Modifier else Modifier.fillMaxWidth()).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            profiles.forEach { p ->
                val isF = focusedId == p.id
                val size by animateFloatAsState(if (isF) (if (wide) 104f else 104f) else (if (wide) 56f else 72f), tween(260), label = "sz")
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = if (isF) 14.dp else 6.dp)) {
                    // lápiz para editar: aparece al lado del perfil enfocado (izquierda en la tele)
                    Box(Modifier.width(44.dp), contentAlignment = Alignment.Center) {
                        if (isF || !wide) PfTap({ onEdit(p) }, Modifier.size(34.dp), onFocus = { focusedId = p.id }) { f ->
                            Box(Modifier.fillMaxSize().background(if (f) Color.White else Color(0xFF2A3245).copy(alpha = .9f), CircleShape), contentAlignment = Alignment.Center) {
                                Icon(PencilIcon, "Editar", tint = if (f) Color(0xFF111111) else Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    PfTap({ onUse(p) }, req = if (p.id == (Store.profile ?: profiles.first()).id) first else null, onFocus = { focusedId = p.id }) { _ ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(150.dp)) {
                            GlowAvatar(p, size.dp, if (isF) .9f else 0f)
                            if (isF || !wide) Text(p.name, style = pfText(17, true), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp))
                        }
                    }
                    Spacer(Modifier.width(44.dp))
                }
            }
            if (profiles.size < 5) {
                val isF = onPlus
                val size by animateFloatAsState(if (isF) (if (wide) 112f else 104f) else (if (wide) 56f else 72f), tween(260), label = "pl")
                PfTap(onAdd, Modifier.padding(start = 44.dp, end = 44.dp), onFocus = { focusedId = "+" }) { _ ->
                    Box(Modifier.padding(vertical = 10.dp).size(size.dp).background(if (isF) PfYellow else Color(0xFF3A3F4C), CircleShape), contentAlignment = Alignment.Center) {
                        Text("+", style = pfText((size * .34f).toInt(), false, if (isF) Color(0xFF111111) else Color.White))
                    }
                }
            }
        }
        if (onAccount != null) Box(Modifier.align(Alignment.TopEnd).padding(top = if (wide) 26.dp else 50.dp, end = if (wide) 40.dp else 16.dp)) { PfButton("⚙  Mi cuenta", onClick = onAccount) }
        if (logo.isNotBlank()) coil.compose.AsyncImage(logo, null, Modifier.align(Alignment.BottomStart).padding(start = if (wide) 48.dp else 24.dp, bottom = 26.dp).height(18.dp).widthIn(max = 150.dp), contentScale = ContentScale.Fit, alignment = Alignment.CenterStart)
    }
}

/* ---------------- elegir la foto (carrusel) ---------------- */

@Composable
private fun AvatarPicker(d: PfDraft, wide: Boolean, onDone: () -> Unit) {
    val all = remember { (Store.avatarLib + Store.avatarDefaults).distinct() }
    val startIdx = all.indexOf(d.avatar).coerceAtLeast(0)
    var cur by remember { mutableIntStateOf(startIdx) }
    val glow = rememberGlow(Profile(d.id, d.name, all.getOrElse(cur) { "" }, emptyList()))
    val col by animateColorAsState(glow, tween(450), label = "g")
    val reqs = remember { all.map { FocusRequester() } }
    val list = rememberLazyListState(maxOf(0, startIdx - 2))
    var glowX by remember { mutableFloatStateOf(-1f) }
    LaunchedEffect(Unit) { delay(80); focusSoon(reqs[startIdx]) }
    LaunchedEffect(cur) { list.animateScrollToItem(maxOf(0, cur - if (wide) 2 else 1)) }
    val big = if (wide) 250.dp else 190.dp; val small = if (wide) 128.dp else 92.dp
    BoxWithConstraints(Modifier.fillMaxSize().background(PfBg)) {
        val h = constraints.maxHeight.toFloat()
        val cx = if (glowX < 0f) constraints.maxWidth * .6f else glowX
        // el brillo sale de atrás de la foto enfocada y tiñe toda la parte de abajo
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(col.copy(alpha = .8f), col.copy(alpha = .35f), col.copy(alpha = .08f), Color.Transparent), center = Offset(cx, h * .6f), radius = h * .75f)))
        Text("Elegí tu foto", style = pfText(if (wide) 28 else 26, true), modifier = Modifier.padding(start = if (wide) 64.dp else 22.dp, top = if (wide) 44.dp else 60.dp))
        LazyRow(state = list, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (wide) 40.dp else 20.dp),
            contentPadding = PaddingValues(horizontal = if (wide) 64.dp else 24.dp), modifier = Modifier.align(Alignment.Center).offset(y = 24.dp).fillMaxWidth().height(big + 40.dp)) {
            itemsIndexed(all) { i, u ->
                val on = i == cur
                val sz by animateFloatAsState(if (on) big.value else small.value, tween(300), label = "av")
                val pr = Profile(d.id, d.name, u, emptyList())
                PfTap({ if (on || wide) { d.avatar = u; onDone() } else cur = i }, req = reqs[i], onFocus = { cur = i },
                    modifier = Modifier.onGloballyPositioned { if (on) glowX = it.positionInRoot().x + it.size.width / 2f }) { _ ->
                    ProfileAvatar(pr, sz.dp, Modifier.graphicsLayer { alpha = if (on) 1f else .9f })
                }
            }
        }
        Text(if (wide) "Elegí con las flechas · OK para confirmar" else "Tocá una foto y tocala de nuevo para elegirla", style = pfText(13, false, Color.White.copy(alpha = .55f)),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 26.dp))
    }
}

/* ---------------- nombre ---------------- */

@Composable
private fun NameStep(d: PfDraft, wide: Boolean, busy: Boolean, onPick: () -> Unit, onNext: () -> Unit, onSave: () -> Unit, onDelete: () -> Unit) {
    val glow = rememberGlow(d.asProfile())
    val col by animateColorAsState(glow, tween(450), label = "g")
    val firstKey = remember { FocusRequester() }
    val field = remember { FocusRequester() }
    var upper by remember { mutableStateOf(d.name.isEmpty()) }
    var sure by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { focusSoon(if (wide) firstKey else field) }
    fun type(s: String) { if (d.name.length < 20) { d.name += if (upper) s.uppercase() else s; upper = false } }
    BoxWithConstraints(Modifier.fillMaxSize().background(PfBg)) {
        val w = constraints.maxWidth.toFloat(); val h = constraints.maxHeight.toFloat()
        // el color de la foto entra desde la derecha
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color.Transparent, .45f to col.copy(alpha = .08f), 1f to col.copy(alpha = .62f))))
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(col.copy(alpha = .55f), Color.Transparent), center = Offset(w * .92f, h * .2f), radius = h * .7f)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(.55f to Color.Transparent, 1f to PfBg.copy(alpha = .85f))))
        // la foto arriba a la derecha (tocándola se cambia)
        PfTap(onPick, Modifier.align(Alignment.TopEnd).padding(top = if (wide) 24.dp else 44.dp, end = if (wide) 44.dp else 16.dp)) { f ->
            Box(contentAlignment = Alignment.BottomCenter) {
                ProfileAvatar(d.asProfile(), if (wide) 138.dp else 96.dp, Modifier.scale(if (f) 1.06f else 1f).border(if (f) 3.dp else 0.dp, Color.White, CircleShape))
                if (f) Box(Modifier.offset(y = 14.dp).background(Color.White, RoundedCornerShape(99.dp)).padding(horizontal = 10.dp, vertical = 3.dp)) { Text("CAMBIAR FOTO", style = pfText(10, true, Color(0xFF111111))) }
            }
        }
        Column(Modifier.fillMaxSize().padding(start = if (wide) 56.dp else 22.dp, end = if (wide) 44.dp else 22.dp, top = if (wide) 40.dp else 60.dp, bottom = if (wide) 26.dp else 30.dp)) {
            Text(if (d.isNew) "¿Cómo se llama el perfil?" else "Editar perfil", style = pfText(if (wide) 26 else 24, true), modifier = Modifier.padding(end = if (wide) 180.dp else 110.dp))
            Spacer(Modifier.height(if (wide) 56.dp else 90.dp))
            if (wide) {
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.height(78.dp)) {
                    Text(d.name, style = pfText(62, true), maxLines = 1)
                    Box(Modifier.padding(start = 4.dp, bottom = 12.dp).width(2.dp).height(58.dp).background(Color.White.copy(alpha = .85f)))
                }
            } else {
                BasicTextField(d.name, { d.name = it.take(20) }, Modifier.fillMaxWidth().focusRequester(field), textStyle = pfText(44, true), singleLine = true, cursorBrush = SolidColor(Color.White),
                    decorationBox = { inner -> if (d.name.isEmpty()) Text("Nombre", style = pfText(44, true, Color.White.copy(alpha = .28f))); inner() })
            }
            Box(Modifier.fillMaxWidth().height(2.dp).background(Color.White.copy(alpha = .8f)))
            Spacer(Modifier.height(16.dp))
            if (wide) {
                val rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnmñ")
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    rows.forEachIndexed { ri, row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEachIndexed { ci, ch -> Key(if (upper) ch.uppercase() else ch.toString(), Modifier.weight(1f), if (ri == 0 && ci == 0) firstKey else null) { type(ch.toString()) } }
                            when (ri) {
                                1 -> Key("⌫", Modifier.weight(1f)) { d.name = d.name.dropLast(1); if (d.name.isEmpty()) upper = true }
                                2 -> { Key(if (upper) "abc" else "ABC", Modifier.weight(1f)) { upper = !upper }; Key("␣", Modifier.weight(1f)) { if (d.name.isNotEmpty() && !d.name.endsWith(" ")) type(" ") } }
                                else -> {}
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (d.isNew) PfButton("Siguiente", onClick = onNext)
                else {
                    PfButton(if (busy) "Guardando…" else "Guardar", onClick = onSave)
                    PfButton("Gustos", onClick = onNext)
                    if (d.id != "main") PfButton(if (sure) "¿Seguro? Tocá de nuevo" else "Eliminar perfil", danger = true) { if (sure) onDelete() else sure = true }
                }
            }
        }
    }
}

@Composable
private fun Key(label: String, modifier: Modifier, req: FocusRequester? = null, onClick: () -> Unit) {
    PfTap(onClick, modifier.height(36.dp), req = req) { f ->
        Box(Modifier.fillMaxSize().background(if (f) PfYellow else Color(0xFF2B3446).copy(alpha = .92f), RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
            Text(label, style = pfText(16, true, if (f) Color(0xFF111111) else Color.White))
        }
    }
}

/* ---------------- gustos: hasta 5 títulos, agrupados por género ---------------- */

private const val PF_MAX_LIKES = 5

@Composable
private fun TastePicker(d: PfDraft, wide: Boolean, busy: Boolean, onSave: () -> Unit) {
    val glow = rememberGlow(d.asProfile())
    val col by animateColorAsState(glow, tween(450), label = "g")
    val pub = remember { Store.published().filter { it.poster.isNotBlank() } }
    // grupos de 6 por género (como Peacock: bloques de 3 × 2 que se recorren para el costado)
    val groups = remember {
        val used = HashSet<String>()
        val gs = pub.flatMap { it.genres }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
        gs.mapNotNull { g -> val l = pub.filter { g in it.genres && it.id !in used }.shuffled().take(6); used.addAll(l.map { it.id }); if (l.size >= 3) g to l else null }.take(8)
    }
    var active by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val row = rememberLazyListState()
    val grid = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusSoon(first) }
    BoxWithConstraints(Modifier.fillMaxSize().background(PfBg)) {
        val w = constraints.maxWidth.toFloat(); val h = constraints.maxHeight.toFloat()
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(col.copy(alpha = .5f), col.copy(alpha = .12f), Color.Transparent), center = Offset(w * .95f, h * .35f), radius = w * .6f)))
        Column(Modifier.fillMaxSize().padding(top = if (wide) 30.dp else 56.dp, bottom = 18.dp)) {
            Text("Elegí hasta $PF_MAX_LIKES títulos que te interesen\npara tener mejores recomendaciones", style = pfText(if (wide) 22 else 20, true), modifier = Modifier.padding(start = if (wide) 48.dp else 18.dp))
            Spacer(Modifier.height(14.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = if (wide) 48.dp else 18.dp)) {
                itemsIndexed(groups) { gi, (g, _) ->
                    PfTap({ active = gi; scope.launch { if (wide) row.animateScrollToItem(gi) else grid.animateScrollToItem(groups.take(gi).sumOf { it.second.size + 1 }) } }) { f ->
                        val on = gi == active
                        Box(Modifier.height(32.dp).background(if (f) Color.White else Color(0xFF1E2230).copy(alpha = if (on) 0f else .9f), RoundedCornerShape(99.dp))
                            .border(if (on && !f) 1.5.dp else 0.dp, Color.White, RoundedCornerShape(99.dp)).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                            Text(g.uppercase(), style = pfText(12, true, if (f) Color(0xFF111111) else Color.White).copy(letterSpacing = 1.sp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            val cell = @Composable { t: Title, req: FocusRequester?, gi: Int ->
                val on = t.id in d.likes
                PfTap({ if (on) d.likes.remove(t.id) else if (d.likes.size < PF_MAX_LIKES) d.likes.add(t.id) }, Modifier.aspectRatio(.7f, matchHeightConstraintsFirst = wide), req = req, onFocus = { active = gi }) { f ->
                    val s by animateFloatAsState(if (f) 1.05f else 1f, tween(140), label = "po")
                    Box(Modifier.fillMaxSize().scale(s).border(if (f) 3.dp else 0.dp, PfYellow, RoundedCornerShape(12.dp)).padding(if (f) 3.dp else 0.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF221E2A))) {
                        coil.compose.AsyncImage(t.poster, t.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp).background(if (on) PfYellow else Color.Black.copy(alpha = .35f), CircleShape)
                            .border(1.5.dp, if (on) PfYellow else Color.White.copy(alpha = .75f), CircleShape), contentAlignment = Alignment.Center) {
                            Text("✓", style = pfText(12, true, if (on) Color(0xFF111111) else Color.White.copy(alpha = .85f)))
                        }
                    }
                }
            }
            if (wide) LazyRow(state = row, modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(40.dp), contentPadding = PaddingValues(horizontal = 48.dp)) {
                itemsIndexed(groups) { gi, (_, ts) ->
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxHeight()) {
                        ts.chunked(3).take(2).forEach { r ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                                r.forEach { t -> Box(Modifier.fillMaxHeight()) { cell(t, if (gi == 0 && t == ts.first()) first else null, gi) } }
                            }
                        }
                    }
                }
            } else LazyVerticalGrid(GridCells.Fixed(3), Modifier.weight(1f).padding(horizontal = 18.dp), state = grid, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                groups.forEachIndexed { gi, (g, ts) ->
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) { Text(g, style = pfText(15, true), modifier = Modifier.padding(top = 8.dp)) }
                    items(ts, key = { "$gi-${it.id}" }) { t -> cell(t, if (gi == 0 && t == ts.first()) first else null, gi) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp, start = if (wide) 48.dp else 18.dp, end = if (wide) 48.dp else 18.dp), verticalAlignment = Alignment.CenterVertically) {
                PfButton(if (busy) "Guardando…" else if (d.likes.isEmpty()) "Saltear" else "Listo", primary = d.likes.isNotEmpty()) { if (!busy) onSave() }
                Spacer(Modifier.weight(1f))
                Text(pfCount(d.likes.size), style = pfText(14, true))
            }
        }
    }
}

private fun pfCount(n: Int) = androidx.compose.ui.text.buildAnnotatedString {
    pushStyle(androidx.compose.ui.text.SpanStyle(color = PfYellow)); append("$n"); pop()
    append(if (n == 1) " título elegido" else " títulos elegidos")
}
