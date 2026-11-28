package com.realityvault.tv

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
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
 * Perfiles, igual en la tele y en el celular (este archivo se comparte entre las dos apps):
 * "¿Quién está mirando?", administrar, crear / editar (la foto se elige en una pantalla aparte,
 * como en Netflix) y elegir gustos para las recomendaciones. Se guardan en la cuenta, así que la web
 * ve los mismos perfiles.
 */

/** Para abrir la pantalla de perfiles desde cualquier lado ("pick", "manage", "new"). */
object PfUi { var open by mutableStateOf("") }

private val PfPink = Color(0xFFFF3D8B)
private fun pfText(size: Int, bold: Boolean = false, color: Color = Color.White) =
    TextStyle(fontSize = size.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, color = color)

private fun pfHue(id: String): Float { var h = 0L; id.forEach { h = (h * 31 + it.code) % 360 }; return h.toFloat() }

@Composable
fun ProfileAvatar(p: Profile?, size: Dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(size * .24f)
    Box(modifier.size(size).clip(shape).background(
        if (p == null || p.avatar.isBlank()) Brush.linearGradient(listOf(Color.hsv(pfHue(p?.id ?: "x"), .55f, .98f), Color.hsv((pfHue(p?.id ?: "x") + 40) % 360, .7f, .6f)))
        else SolidColor(Color(0xFF2A2633))
    ), contentAlignment = Alignment.Center) {
        if (p != null && p.avatar.isNotBlank()) coil.compose.AsyncImage(p.avatar, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text((p?.name?.firstOrNull() ?: '?').uppercase(), style = pfText((size.value * .4f).toInt(), true))
    }
}

/** Botón que se ve igual con el control de la tele (foco) y con el dedo. */
@Composable
private fun PfTap(onClick: () -> Unit, modifier: Modifier = Modifier, req: FocusRequester? = null, content: @Composable BoxScope.(Boolean) -> Unit) {
    val src = remember { MutableInteractionSource() }
    val f by src.collectIsFocusedAsState()
    Box(modifier.then(if (req != null) Modifier.focusRequester(req) else Modifier)
        .clickable(interactionSource = src, indication = null, onClick = onClick), contentAlignment = Alignment.Center) { content(f) }
}

@Composable
private fun PfButton(text: String, primary: Boolean = false, danger: Boolean = false, req: FocusRequester? = null, onClick: () -> Unit) {
    PfTap(onClick, req = req) { f ->
        val bg = when { f -> Color.White; primary -> PfPink; else -> Color.White.copy(alpha = .1f) }
        Box(Modifier.scale(if (f) 1.05f else 1f).height(42.dp).background(bg, RoundedCornerShape(99.dp))
            .border(1.dp, if (danger && !f) Color(0x80FF7A9C) else Color.Transparent, RoundedCornerShape(99.dp)).padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
            Text(text, style = pfText(15, true, when { f -> Color(0xFF111111); danger -> Color(0xFFFF7A9C); else -> Color.White }), maxLines = 1)
        }
    }
}

private class PfDraft(val isNew: Boolean, val id: String, name: String, avatar: String, likes: List<String>) {
    var name by mutableStateOf(name); var avatar by mutableStateOf(avatar)
    val likes = mutableStateListOf<String>().apply { addAll(likes) }
}

/**
 * Pantalla completa de perfiles. startMode: "pick" (al abrir la app), "manage" o "new".
 * onClose se llama al elegir un perfil o al salir.
 */
@Composable
fun ProfileGate(startMode: String, logo: String, onAccount: (() -> Unit)? = null, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(if (startMode == "new") "edit" else startMode) }
    var draft by remember { mutableStateOf<PfDraft?>(null) }
    var step by remember { mutableIntStateOf(1) }
    var picking by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    LaunchedEffect(msg) { if (msg.isNotBlank()) { delay(2600); msg = "" } }
    fun newDraft() { val n = Store.profiles.size; val ch = Store.avatarChoices(); draft = PfDraft(true, Store.newProfileId(), "", ch[n % ch.size], emptyList()); step = 1; picking = false; mode = "edit" }
    LaunchedEffect(Unit) { if (startMode == "new") newDraft() }

    BackHandler {
        when {
            picking -> picking = false
            mode == "edit" && step == 2 -> step = 1
            mode == "edit" -> { draft = null; mode = if (startMode == "new") "close" else "manage"; if (mode == "close") onClose() }
            mode == "manage" && startMode == "pick" -> mode = "pick"
            else -> { Store.askProfile = false; onClose() }
        }
    }

    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF2E1430), Color(0xFF0B0A12)), radius = 1600f, center = androidx.compose.ui.geometry.Offset(800f, 0f)))) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth > maxHeight
            val d = draft
            when {
                mode == "edit" && d != null && picking -> AvatarPicker(d, wide) { picking = false }
                mode == "edit" && d != null && step == 2 -> TastePicker(d, wide, busy, onBack = { step = 1 }) {
                    busy = true
                    scope.launch {
                        val p = Profile(d.id, d.name.trim().take(20), d.avatar, d.likes.take(40))
                        val list = Store.profiles.toMutableList(); val i = list.indexOfFirst { it.id == p.id }
                        if (i < 0) list += p else list[i] = p
                        runCatching { Store.saveProfiles(list) }
                            .onSuccess { if (d.isNew) { Store.useProfile(p); onClose() } else { draft = null; mode = "manage" } }
                            .onFailure { msg = "No se pudo guardar el perfil" }
                        busy = false
                    }
                }
                mode == "edit" && d != null -> EditProfile(d, wide, logo, busy,
                    onPick = { picking = true },
                    onCancel = { draft = null; if (startMode == "new") onClose() else mode = "manage" },
                    onNext = { if (d.name.isBlank()) msg = "Ponele un nombre" else step = 2 },
                    onSave = {
                        if (d.name.isBlank()) { msg = "Ponele un nombre"; return@EditProfile }
                        busy = true
                        scope.launch {
                            val p = Profile(d.id, d.name.trim().take(20), d.avatar, d.likes.toList())
                            val list = Store.profiles.map { if (it.id == p.id) p else it }
                            runCatching { Store.saveProfiles(list) }.onSuccess { draft = null; mode = "manage" }.onFailure { msg = "No se pudo guardar" }
                            busy = false
                        }
                    },
                    onDelete = {
                        busy = true
                        scope.launch {
                            val p = Store.profiles.firstOrNull { it.id == d.id }
                            if (p != null) runCatching { Store.deleteProfile(p) }.onFailure { msg = "No se pudo eliminar" }
                            draft = null; mode = "manage"; busy = false
                        }
                    })
                else -> PickProfiles(manage = mode == "manage", wide = wide, logo = logo,
                    onUse = { p -> scope.launch { Store.useProfile(p); onClose() } },
                    onEdit = { p -> draft = PfDraft(false, p.id, p.name, p.avatar, p.likes); step = 1; picking = false; mode = "edit" },
                    onAdd = { if (Store.profiles.size >= 5) msg = "Podés tener hasta 5 perfiles" else newDraft() },
                    onManage = { mode = "manage" },
                    onDone = { if (startMode == "pick") mode = "pick" else onClose() },
                    onAccount = if (Store.askProfile) null else onAccount)
            }
        }
        if (msg.isNotBlank()) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 30.dp).background(Color(0xEE1A1520), RoundedCornerShape(99.dp)).padding(horizontal = 22.dp, vertical = 12.dp)) {
            Text(msg, style = pfText(15, true))
        }
    }
}

@Composable
private fun PfLogo(logo: String, h: Dp) {
    if (logo.isNotBlank()) coil.compose.AsyncImage(logo, null, Modifier.height(h).widthIn(max = 220.dp), contentScale = ContentScale.Fit)
}

@Composable
private fun PickProfiles(manage: Boolean, wide: Boolean, logo: String, onUse: (Profile) -> Unit, onEdit: (Profile) -> Unit, onAdd: () -> Unit, onManage: () -> Unit, onDone: () -> Unit, onAccount: (() -> Unit)?) {
    val first = remember { FocusRequester() }
    LaunchedEffect(manage) { repeat(6) { if (runCatching { first.requestFocus() }.isSuccess) return@LaunchedEffect; delay(60) } }
    val av = if (wide) 116.dp else 104.dp
    Column(Modifier.fillMaxSize().padding(vertical = if (wide) 30.dp else 56.dp, horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        PfLogo(logo, 24.dp)
        Spacer(Modifier.weight(.6f))
        Text(if (manage) "Administrar perfiles" else "¿Quién está mirando?", style = pfText(if (wide) 30 else 26, true), textAlign = TextAlign.Center)
        Spacer(Modifier.height(30.dp))
        val items = Store.profiles.map { it as Profile? } + (if (Store.profiles.size < 5) listOf(null) else emptyList())
        val rows = if (wide) listOf(items) else items.chunked(2)
        rows.forEachIndexed { ri, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(if (wide) 30.dp else 22.dp)) {
                row.forEachIndexed { ci, p ->
                    PfTap({ if (p == null) onAdd() else if (manage) onEdit(p) else onUse(p) }, Modifier.width(av + 16.dp), req = if (ri == 0 && ci == 0) first else null) { f ->
                        val s by animateFloatAsState(if (f) 1.1f else 1f, tween(160), label = "pf")
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.scale(s).border(if (f) 3.dp else 0.dp, Color.White, RoundedCornerShape(av * .24f)), contentAlignment = Alignment.Center) {
                                if (p == null) Box(Modifier.size(av).background(Color.White.copy(alpha = .07f), RoundedCornerShape(av * .24f))
                                    .border(2.dp, Color.White.copy(alpha = .3f), RoundedCornerShape(av * .24f)), contentAlignment = Alignment.Center) { Text("+", style = pfText(44, false, Color.White.copy(alpha = .75f))) }
                                else ProfileAvatar(p, av)
                                if (manage && p != null) Box(Modifier.size(40.dp).background(Color(0x99000000), CircleShape), contentAlignment = Alignment.Center) { Text("✎", style = pfText(18, true)) }
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(p?.name ?: "Agregar", style = pfText(16, f, if (f) Color.White else Color.White.copy(alpha = .7f)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            Spacer(Modifier.height(22.dp))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (manage) PfButton("Listo", primary = true, onClick = onDone) else PfButton("Administrar perfiles", onClick = onManage)
            if (!manage && onAccount != null) PfButton("Mi cuenta", onClick = onAccount)
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun EditProfile(d: PfDraft, wide: Boolean, logo: String, busy: Boolean, onPick: () -> Unit, onCancel: () -> Unit, onNext: () -> Unit, onSave: () -> Unit, onDelete: () -> Unit) {
    val nameReq = remember { FocusRequester() }
    var sure by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { repeat(6) { if (runCatching { nameReq.requestFocus() }.isSuccess) return@LaunchedEffect; delay(60) } }
    Column(Modifier.fillMaxSize().padding(vertical = if (wide) 30.dp else 56.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        PfLogo(logo, 24.dp)
        Spacer(Modifier.height(if (wide) 30.dp else 40.dp))
        Text(if (d.isNew) "Nuevo perfil" else "Editar perfil", style = pfText(28, true))
        Spacer(Modifier.height(28.dp))
        val avatarBox = @Composable {
            PfTap(onPick) { f ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box {
                        ProfileAvatar(Profile(d.id, d.name.ifBlank { "?" }, d.avatar, emptyList()), 150.dp,
                            Modifier.border(if (f) 3.dp else 0.dp, Color.White, RoundedCornerShape(36.dp)))
                        Box(Modifier.align(Alignment.BottomEnd).offset(8.dp, 8.dp).size(40.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
                            Text("✎", style = pfText(18, true, Color(0xFF111111)))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Cambiar foto", style = pfText(14, f, Color.White.copy(alpha = if (f) 1f else .7f)))
                }
            }
        }
        val nameField = @Composable {
            Column(Modifier.widthIn(max = 420.dp).fillMaxWidth()) {
                Text("Nombre", style = pfText(13, true, Color.White.copy(alpha = .7f)))
                Spacer(Modifier.height(6.dp))
                val src = remember { MutableInteractionSource() }
                val f by src.collectIsFocusedAsState()
                BasicTextField(d.name, { d.name = it.take(20) }, Modifier.fillMaxWidth().focusRequester(nameReq)
                    .background(Color.White.copy(alpha = .08f), RoundedCornerShape(12.dp)).border(1.5.dp, if (f) Color.White else Color.White.copy(alpha = .18f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                    textStyle = pfText(18), singleLine = true, cursorBrush = SolidColor(Color.White), interactionSource = src,
                    decorationBox = { inner -> if (d.name.isEmpty()) Text("Ej: Valen", style = pfText(18, false, Color.White.copy(alpha = .35f))); inner() })
            }
        }
        if (wide) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(40.dp)) { avatarBox(); nameField() }
        else { avatarBox(); Spacer(Modifier.height(26.dp)); nameField() }
        Spacer(Modifier.height(34.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!d.isNew && d.id != "main") PfButton(if (sure) "¿Seguro? Tocá de nuevo" else "Eliminar", danger = true) { if (sure) onDelete() else sure = true }
            PfButton("Cancelar", onClick = onCancel)
            if (d.isNew) PfButton("Siguiente", primary = true, onClick = onNext)
            else { PfButton("Gustos", onClick = onNext); PfButton(if (busy) "Guardando…" else "Guardar", primary = true, onClick = onSave) }
        }
    }
}

/** Elegir foto: pantalla aparte con las del sitio (las que sube el admin) y las clásicas. */
@Composable
private fun AvatarPicker(d: PfDraft, wide: Boolean, onDone: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { repeat(6) { if (runCatching { first.requestFocus() }.isSuccess) return@LaunchedEffect; delay(60) } }
    val mine = Store.avatarLib; val classic = Store.avatarDefaults
    Column(Modifier.fillMaxSize().padding(horizontal = if (wide) 48.dp else 18.dp, vertical = if (wide) 28.dp else 50.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Elegí una foto de perfil", style = pfText(if (wide) 26 else 22, true))
                Text(d.name.ifBlank { "Nuevo perfil" }, style = pfText(14, false, Color.White.copy(alpha = .65f)))
            }
            ProfileAvatar(Profile(d.id, d.name.ifBlank { "?" }, d.avatar, emptyList()), 56.dp)
        }
        Spacer(Modifier.height(20.dp))
        LazyVerticalGrid(GridCells.Adaptive(if (wide) 104.dp else 92.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(6.dp)) {
            var firstSet = false
            fun section(title: String, list: List<String>) {
                if (list.isEmpty()) return
                item(span = { GridItemSpan(maxLineSpan) }) { Text(title, style = pfText(17, true), modifier = Modifier.padding(top = 6.dp)) }
                val isFirst = !firstSet; firstSet = true
                items(list.size) { i ->
                    val u = list[i]
                    PfTap({ d.avatar = u; onDone() }, Modifier.aspectRatio(1f), req = if (isFirst && i == 0) first else null) { f ->
                        val s by animateFloatAsState(if (f) 1.08f else 1f, tween(140), label = "av")
                        Box(Modifier.fillMaxSize().scale(s).clip(RoundedCornerShape(24.dp))
                            .border(if (f || u == d.avatar) 3.dp else 0.dp, if (f) Color.White else PfPink, RoundedCornerShape(24.dp))) {
                            coil.compose.AsyncImage(u, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                    }
                }
            }
            section("Del sitio", mine)
            section("Clásicas", classic)
        }
    }
}

/** Paso 2 al crear un perfil: elegir lo que le gusta (arma las recomendaciones). */
@Composable
private fun TastePicker(d: PfDraft, wide: Boolean, busy: Boolean, onBack: () -> Unit, onSave: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { repeat(6) { if (runCatching { first.requestFocus() }.isSuccess) return@LaunchedEffect; delay(60) } }
    val pool = remember { Store.published().filter { it.poster.isNotBlank() }.let { l -> l.filter { it.id in d.likes } + l.filter { it.id !in d.likes }.shuffled() } }
    Column(Modifier.fillMaxSize().padding(horizontal = if (wide) 48.dp else 16.dp, vertical = if (wide) 24.dp else 50.dp)) {
        Text("¿Qué te gusta, ${d.name.trim()}?", style = pfText(if (wide) 26 else 22, true))
        Text("Elegí 3 o más. Con esto armamos tus recomendaciones desde el primer día.", style = pfText(14, false, Color.White.copy(alpha = .7f)), modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))
        LazyVerticalGrid(GridCells.Adaptive(if (wide) 104.dp else 100.dp), Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(6.dp)) {
            items(pool, key = { it.id }) { t ->
                val on = t.id in d.likes
                PfTap({ if (on) d.likes.remove(t.id) else d.likes.add(t.id) }, Modifier.aspectRatio(2f / 3f), req = if (t == pool.first()) first else null) { f ->
                    val s by animateFloatAsState(if (f) 1.06f else 1f, tween(140), label = "po")
                    Box(Modifier.fillMaxSize().scale(s).clip(RoundedCornerShape(10.dp)).background(Color(0xFF221E2A))
                        .border(if (on) 3.dp else if (f) 2.dp else 0.dp, if (on) PfPink else Color.White, RoundedCornerShape(10.dp))) {
                        coil.compose.AsyncImage(t.poster, t.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = if (on) .7f else 1f)
                        if (on) Box(Modifier.align(Alignment.TopEnd).padding(7.dp).size(26.dp).background(PfPink, CircleShape), contentAlignment = Alignment.Center) { Text("✓", style = pfText(14, true)) }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (d.likes.isEmpty()) "Ninguno elegido" else "${d.likes.size} elegido${if (d.likes.size > 1) "s" else ""}", style = pfText(14, false, Color.White.copy(alpha = .7f)), modifier = Modifier.weight(1f))
            PfButton("Atrás", onClick = onBack)
            PfButton(if (busy) "Guardando…" else if (d.likes.size < 3 && d.isNew) "Saltear" else "Listo", primary = true) { if (!busy) onSave() }
        }
    }
}
