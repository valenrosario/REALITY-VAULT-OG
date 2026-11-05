package com.realityvault.mobile

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.realityvault.tv.Api
import com.realityvault.tv.BuildConfig
import com.realityvault.tv.R
import com.realityvault.tv.Store
import com.realityvault.tv.Title
import com.realityvault.tv.Updater
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/* ---------------- sonidito al elegir un favorito ---------------- */

private class Pick(ctx: Context) {
    private val pool = SoundPool.Builder().setMaxStreams(3)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    private val id = pool.load(ctx, R.raw.pick, 1)
    fun play(up: Boolean) { pool.play(id, .55f, .55f, 1, 0, if (up) 1f else .8f) }
    fun release() = pool.release()
}

/* ---------------- entrar / crear cuenta (mismos pasos que la web) ---------------- */

@Composable
fun LoginScreen(s: Screen.Login, nav: Nav) {
    var signup by remember { mutableStateOf(false) }
    var step by remember { mutableIntStateOf(1) }      // crear cuenta: 1 email · 2 foto · 3 nombre y contraseña · 4 favoritos
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var avatar by remember { mutableStateOf("") }
    val favs = remember { mutableStateListOf<String>() }
    var show by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var confirmMail by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val kb = LocalSoftwareKeyboardController.current
    val side = remember { Store.published().filter { it.backdropMobile.isNotBlank() || it.backdrop.isNotBlank() }.shuffled().firstOrNull() }

    fun done() {
        when {
            s.then != null -> nav.replace(s.then)
            s.required -> nav.section(Screen.Home)
            else -> nav.back()
        }
    }
    fun errText(e: Exception, login: Boolean): String {
        val m = (e as? Api.HttpError)?.message.orEmpty()
        return when {
            e !is Api.HttpError -> "Sin conexión. Probá de nuevo."
            Regex("already registered|already been registered", RegexOption.IGNORE_CASE).containsMatchIn(m) -> "Ese email ya tiene una cuenta. Tocá \"Ingresá\"."
            Regex("not confirmed", RegexOption.IGNORE_CASE).containsMatchIn(m) -> "Tenés que confirmar tu email antes de ingresar. Revisá tu correo."
            Regex("rate limit", RegexOption.IGNORE_CASE).containsMatchIn(m) -> "Demasiados intentos. Probá en unos minutos."
            Regex("signups not allowed", RegexOption.IGNORE_CASE).containsMatchIn(m) -> "El registro está desactivado por ahora."
            login && e.code == 400 -> "Email o contraseña incorrectos."
            else -> m.ifBlank { "No se pudo completar." }
        }
    }
    fun doLogin() {
        if (busy) return
        if (!email.contains("@") || pass.isEmpty()) { err = "Escribí tu email y tu contraseña."; return }
        kb?.hide(); busy = true; err = ""
        scope.launch {
            try { Store.login(email.trim(), pass); done() } catch (e: Exception) { err = errText(e, true) }
            busy = false
        }
    }
    fun doSignup() {
        if (busy) return
        if (name.isBlank()) { err = "Decinos cómo te llamamos."; return }
        if (pass.length < 6) { err = "La contraseña necesita al menos 6 caracteres."; return }
        kb?.hide(); busy = true; err = ""
        scope.launch {
            try {
                if (Store.signup(email.trim(), pass, name.trim(), avatar)) step = 4 else confirmMail = true
            } catch (e: Exception) { err = errText(e, false) }
            busy = false
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // portada de fondo, difuminada hacia abajo
        if (side != null) {
            Img(side.backdropMobile.ifBlank { side.backdrop }, Modifier.fillMaxWidth().aspectRatio(.9f), crossfade = false)
            Box(Modifier.fillMaxWidth().aspectRatio(.9f).background(Brush.verticalGradient(0f to Color.Black.copy(alpha = .45f), .45f to Color.Black.copy(alpha = .55f), 1f to Color.Black)))
        }
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (signup && step in 2..3 && !confirmMail) GlassButton(Ic.back, "Volver", { step--; err = "" }, size = 40.dp)
                else if (!s.required) GlassButton(Ic.close, "Cerrar", { nav.back() }, size = 40.dp)
                else Spacer(Modifier.size(40.dp))
            }
            Spacer(Modifier.height(if (signup && step == 4) 6.dp else 34.dp))
            if (Store.siteLogo.isNotBlank()) Img(Store.siteLogo, Modifier.width(220.dp).height(42.dp), scale = ContentScale.Fit)
            else LocalLogo(Modifier.width(220.dp).height(42.dp))
            Spacer(Modifier.height(26.dp))

            AnimatedContent(
                targetState = Triple(signup, step, confirmMail),
                transitionSpec = { (slideInHorizontally(tween(260)) { it / 5 } + fadeIn(tween(260))) togetherWith fadeOut(tween(150)) },
                label = "auth"
            ) { (su, st, cm) ->
                Column(Modifier.fillMaxWidth().widthIn(max = 460.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    when {
                        cm -> {
                            Title("¡Listo, ${name.trim().split(" ").first()}!", "Te mandamos un email para confirmar tu cuenta. Abrilo, tocá el enlace y después ingresá.")
                            Spacer(Modifier.height(22.dp))
                            PillButton("Ingresar", { confirmMail = false; signup = false; step = 1; pass = "" }, Modifier.fillMaxWidth(), icon = null)
                        }
                        !su -> {
                            Title("Qué bueno verte de nuevo", if (s.then != null) "Entrá con tu cuenta para ver este video." else "Entrá con tu cuenta.")
                            Spacer(Modifier.height(22.dp))
                            Field("EMAIL", email, { email = it }, KeyboardType.Email, ImeAction.Next, onNext = { focus.moveFocus(FocusDirection.Down) })
                            Spacer(Modifier.height(12.dp))
                            Field("CONTRASEÑA", pass, { pass = it }, KeyboardType.Password, ImeAction.Done, secret = !show, onDone = { doLogin() },
                                trailing = { Text(if (show) "Ocultar" else "Ver", style = ts(12, FontWeight.Bold, C.accent), modifier = Modifier.tap({ show = !show }).padding(6.dp)) })
                            ErrLine(err)
                            Spacer(Modifier.height(20.dp))
                            PillButton(if (busy) "Ingresando…" else "Ingresar", { doLogin() }, Modifier.fillMaxWidth(), icon = null)
                            Spacer(Modifier.height(18.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("¿No tenés cuenta? ", style = ts(13.5f, color = C.fg2))
                                Text("Creá una gratis", style = ts(13.5f, FontWeight.Bold, C.accent), modifier = Modifier.tap({ signup = true; step = 1; err = "" }).padding(vertical = 8.dp))
                            }
                        }
                        st == 1 -> {
                            Steps(1)
                            Title("Creá tu cuenta gratis", "Es gratis y tarda un minuto.")
                            Spacer(Modifier.height(22.dp))
                            Field("EMAIL", email, { email = it }, KeyboardType.Email, ImeAction.Next, onNext = {
                                if (Regex("^\\S+@\\S+\\.\\S+$").matches(email.trim())) { step = 2; err = "" } else err = "Revisá el email, no parece válido."
                            })
                            ErrLine(err)
                            Spacer(Modifier.height(20.dp))
                            PillButton("Continuar", { if (Regex("^\\S+@\\S+\\.\\S+$").matches(email.trim())) { step = 2; err = ""; kb?.hide() } else err = "Revisá el email, no parece válido." }, Modifier.fillMaxWidth(), icon = null)
                            Spacer(Modifier.height(18.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("¿Ya tenés cuenta? ", style = ts(13.5f, color = C.fg2))
                                Text("Ingresá", style = ts(13.5f, FontWeight.Bold, C.accent), modifier = Modifier.tap({ signup = false; err = "" }).padding(vertical = 8.dp))
                            }
                        }
                        st == 2 -> {
                            Steps(2)
                            Title("Elegí tu foto de perfil", "Así te vamos a ver en REALITY VAULT.")
                            Spacer(Modifier.height(26.dp))
                            AvatarPicker(avatar) { avatar = it }
                            Spacer(Modifier.height(26.dp))
                            PillButton("Continuar", { if (avatar.isNotBlank()) step = 3 }, Modifier.fillMaxWidth(), icon = null, dark = avatar.isBlank())
                        }
                        st == 3 -> {
                            Steps(3)
                            Title("Casi listo", "Un último paso para tener tu cuenta.")
                            Spacer(Modifier.height(22.dp))
                            Field("¿CÓMO TE LLAMAMOS?", name, { if (it.length <= 40) name = it }, KeyboardType.Text, ImeAction.Next, onNext = { focus.moveFocus(FocusDirection.Down) })
                            Spacer(Modifier.height(12.dp))
                            Field("CONTRASEÑA (MÍNIMO 6 CARACTERES)", pass, { pass = it }, KeyboardType.Password, ImeAction.Done, secret = !show, onDone = { doSignup() },
                                trailing = { Text(if (show) "Ocultar" else "Ver", style = ts(12, FontWeight.Bold, C.accent), modifier = Modifier.tap({ show = !show }).padding(6.dp)) })
                            ErrLine(err)
                            Spacer(Modifier.height(20.dp))
                            PillButton(if (busy) "Creando tu cuenta…" else "Crear mi cuenta", { doSignup() }, Modifier.fillMaxWidth(), icon = null)
                        }
                        else -> FavPicker(favs, busy) { skip ->
                            if (busy) return@FavPicker
                            busy = true
                            scope.launch { if (!skip && favs.isNotEmpty()) Store.addToList(favs.toList()); busy = false; done() }
                        }
                    }
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun Steps(n: Int) {
    Text("PASO $n DE 4", style = ts(11, FontWeight.Bold, C.accent).copy(letterSpacing = 2.sp))
    Row(Modifier.padding(top = 8.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(4) { i -> Box(Modifier.width(34.dp).height(4.dp).clip(RoundedCornerShape(9.dp)).background(if (i < n) C.accent else Color.White.copy(alpha = .2f))) }
    }
}

@Composable
private fun Title(h: String, sub: String) {
    Text(h, style = ts(26, FontWeight.Bold, Color.White).copy(textAlign = TextAlign.Center))
    Text(sub, style = ts(14.5f, color = C.fg2).copy(textAlign = TextAlign.Center), modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun ErrLine(err: String) {
    if (err.isNotBlank()) Text(err, style = ts(13, FontWeight.Bold, Color(0xFFFF8A8A)).copy(textAlign = TextAlign.Center), modifier = Modifier.padding(top = 12.dp))
}

/** Las mismas dos fotos de perfil que la web, con un estallido de brillitos al elegir. */
@Composable
fun AvatarPicker(selected: String, onPick: (String) -> Unit) {
    val ctx = LocalContext.current
    val sfx = remember { Pick(ctx) }
    DisposableEffect(Unit) { onDispose { sfx.release() } }
    Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
        listOf("a1", "a2").forEach { k ->
            val on = selected == k
            val pop = remember { Animatable(1f) }
            val burst = remember { Animatable(0f) }
            val scope = rememberCoroutineScope()
            Box(contentAlignment = Alignment.Center) {
                Sparks(burst.value, 56f)
                Box(
                    Modifier.size(118.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value; alpha = if (selected.isBlank() || on) 1f else .5f }
                        .clip(CircleShape).border(3.dp, if (on) C.accent else Color.Transparent, CircleShape)
                        .tap({
                            onPick(k); sfx.play(true)
                            scope.launch { pop.snapTo(.86f); pop.animateTo(1f, spring(dampingRatio = .4f, stiffness = Spring.StiffnessMediumLow)) }
                            scope.launch { burst.snapTo(0f); burst.animateTo(1f, tween(650, easing = FastOutSlowInEasing)) }
                        }, 1f)
                ) {
                    androidx.compose.foundation.Image(painterResource(avatarRes(k)!!), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                if (on) Box(Modifier.align(Alignment.BottomEnd).size(30.dp).background(C.accent, CircleShape).border(2.dp, Color.Black, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Ic.check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/** Brillitos que salen disparados en círculo (progreso 0 → 1). */
@Composable
private fun Sparks(p: Float, radius: Float) {
    if (p <= 0f || p >= 1f) return
    val colors = listOf(Color(0xFFFF2E93), Color(0xFFFF9A5A), Color.White, Color(0xFFFFD36B))
    Canvas(Modifier.size(1.dp)) {
        repeat(12) { i ->
            val a = i / 12f * 6.283f + .3f
            val d = (radius + 42f * p) * density
            drawCircle(colors[i % 4].copy(alpha = 1f - p), radius = (4.5f - 3f * p) * density, center = Offset(cos(a) * d, sin(a) * d))
        }
    }
}

/** Paso 4: "Armemos tu inicio". Se guardan en Mi lista y alimentan "Para vos". */
@Composable
private fun FavPicker(favs: MutableList<String>, busy: Boolean, onFinish: (skip: Boolean) -> Unit) {
    val ctx = LocalContext.current
    val sfx = remember { Pick(ctx) }
    DisposableEffect(Unit) { onDispose { sfx.release() } }
    val pool = remember { Store.published().filter { it.poster.isNotBlank() }.shuffled().take(12) }
    Steps(4)
    Title("Armemos tu inicio", "Elegí lo que te gusta: lo guardamos en Mi lista y te recomendamos según tus gustos.")
    Spacer(Modifier.height(20.dp))
    pool.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            row.forEach { t -> FavCard(t, t.id in favs, Modifier.weight(1f)) {
                val adding = t.id !in favs
                if (adding) favs.add(t.id) else favs.remove(t.id)
                sfx.play(adding)
            } }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
    Spacer(Modifier.height(10.dp))
    PillButton(
        if (busy) "Guardando…" else if (favs.isNotEmpty()) "Guardar y entrar" else "Entrar a REALITY VAULT",
        { onFinish(false) }, Modifier.fillMaxWidth(), icon = null
    )
    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(if (favs.isEmpty()) "Elegí los que quieras · " else "${favs.size} elegido${if (favs.size > 1) "s" else ""} · ", style = ts(13, color = C.fg2))
        Text("Saltar", style = ts(13, FontWeight.Bold, C.accent), modifier = Modifier.tap({ onFinish(true) }).padding(vertical = 8.dp))
    }
}

@Composable
private fun FavCard(t: Title, on: Boolean, modifier: Modifier, onTap: () -> Unit) {
    val pop = remember { Animatable(1f) }
    val tilt = remember { Animatable(0f) }
    val burst = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Box(modifier.aspectRatio(2f / 3f), contentAlignment = Alignment.Center) {
        Box(
            Modifier.fillMaxSize().graphicsLayer { scaleX = pop.value; scaleY = pop.value; rotationZ = tilt.value }
                .clip(RoundedCornerShape(10.dp)).border(3.dp, if (on) C.accent else Color.Transparent, RoundedCornerShape(10.dp))
                .tap({
                    val adding = !on
                    onTap()
                    scope.launch {
                        if (adding) { pop.snapTo(.84f); pop.animateTo(1f, spring(dampingRatio = .38f, stiffness = Spring.StiffnessMediumLow)) }
                        else { pop.snapTo(.94f); pop.animateTo(1f, spring(dampingRatio = .6f)) }
                    }
                    if (adding) {
                        scope.launch { tilt.snapTo(-6f); tilt.animateTo(0f, spring(dampingRatio = .3f, stiffness = Spring.StiffnessLow)) }
                        scope.launch { burst.snapTo(0f); burst.animateTo(1f, tween(620, easing = FastOutSlowInEasing)) }
                    }
                }, 1f)
        ) {
            PosterCard(t, Modifier.fillMaxSize())
            if (on) Box(Modifier.fillMaxSize().background(C.accent.copy(alpha = .22f)))
        }
        Sparks(burst.value, 38f)
        if (on) Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(26.dp).background(C.accent, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Ic.check, null, tint = Color.White, modifier = Modifier.size(15.dp))
        }
    }
}

@Composable
private fun Field(
    label: String, value: String, onChange: (String) -> Unit, type: KeyboardType, ime: ImeAction,
    secret: Boolean = false, onNext: () -> Unit = {}, onDone: () -> Unit = {}, trailing: (@Composable () -> Unit)? = null
) {
    Column(
        Modifier.fillMaxWidth().background(Color.White.copy(alpha = .07f), RoundedCornerShape(14.dp))
            .border(1.5.dp, Color.White.copy(alpha = .18f), RoundedCornerShape(14.dp)).padding(horizontal = 16.dp, vertical = 9.dp)
    ) {
        Text(label, style = ts(10, FontWeight.Bold, C.muted))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value, onChange, singleLine = true, textStyle = ts(16, color = C.fg), cursorBrush = SolidColor(C.accent),
                visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = type, imeAction = ime, autoCorrect = false),
                keyboardActions = KeyboardActions(onNext = { onNext() }, onDone = { onDone() }),
                modifier = Modifier.weight(1f).padding(vertical = 4.dp)
            )
            trailing?.invoke()
        }
    }
}

/* ---------------- mi cuenta ---------------- */

@Composable
fun AccountScreen(nav: Nav) {
    val s = Store.session
    val scope = rememberCoroutineScope()
    var changing by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassButton(Ic.back, "Volver", { nav.back() }, size = 40.dp)
            Text(if (s != null) "Mi cuenta" else "Entrar", style = ts(24, FontWeight.Bold, Color.White), modifier = Modifier.padding(start = 14.dp))
        }
        Spacer(Modifier.height(24.dp))
        if (s != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(64.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Color(0xFFFFD1E6), C.accent, Color(0xFFB04C86)))), contentAlignment = Alignment.Center) {
                    val ph = avatarRes(s.avatar)
                    if (ph != null) androidx.compose.foundation.Image(painterResource(ph), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Text("★", style = ts(26, color = Color.White))
                }
                Column(Modifier.padding(start = 16.dp)) {
                    Text(s.email, style = ts(16, FontWeight.Bold))
                    Text(if (changing) "Cerrar" else "Cambiar foto de perfil", style = ts(13, FontWeight.Bold, C.accent), modifier = Modifier.padding(top = 2.dp).tap({ changing = !changing }).padding(vertical = 4.dp))
                }
            }
            if (changing) {
                Spacer(Modifier.height(20.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    AvatarPicker(s.avatar) { k -> scope.launch { runCatching { Store.setAvatar(k) } } }
                }
            }
            Spacer(Modifier.height(26.dp))
            PillButton("Cerrar sesión", { Store.logout(); nav.section(Screen.Login(required = true)) }, icon = null, dark = true)
        } else {
            Text("Con tu cuenta podés ver capítulos, guardar series en Mi lista y seguir donde dejaste.", style = ts(15, color = C.fg2))
            Spacer(Modifier.height(22.dp))
            PillButton("Entrar o crear cuenta", { nav.go(Screen.Login()) }, icon = null)
        }
        Spacer(Modifier.height(40.dp))
        Text("Acerca de", style = ts(16, FontWeight.Bold, Color.White))
        Spacer(Modifier.height(8.dp))
        Text("Reality Vault · versión ${BuildConfig.VERSION_NAME}", style = ts(13, color = C.muted))
        Text(if (Store.offline) "Sin conexión: mostrando el último catálogo guardado." else "Catálogo al día.", style = ts(13, color = C.muted), modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            PillButton(if (Updater.available != null) "Instalar versión nueva" else "Buscar actualizaciones", {
                UpdateUi.open = true; scope.launch { Updater.checkManually() }
            }, icon = null, dark = Updater.available == null)
            if (Updater.checking) CircularProgressIndicator(color = C.accent, strokeWidth = 2.dp, modifier = Modifier.padding(start = 12.dp).size(18.dp))
        }
        Spacer(Modifier.height(30.dp))
    }
}
