package com.realityvault.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
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
import com.realityvault.tv.Api
import com.realityvault.tv.BuildConfig
import com.realityvault.tv.Store
import kotlinx.coroutines.launch

/* ---------------- entrar / crear cuenta ---------------- */

@Composable
fun LoginScreen(s: Screen.Login, nav: Nav) {
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val kb = LocalSoftwareKeyboardController.current

    fun done() {
        when {
            s.then != null -> nav.replace(s.then)
            s.required -> nav.section(Screen.Home)
            else -> nav.back()
        }
    }
    fun run(signup: Boolean) {
        if (busy) return
        if (!email.contains("@") || pass.length < 6) { err = "Escribí tu email y una contraseña de al menos 6 caracteres."; return }
        kb?.hide(); busy = true; err = ""
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

    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF2A1520), Color.Black), radius = 1400f))) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
        ) {
            Spacer(Modifier.height(24.dp))
            if (Store.siteLogo.isNotBlank()) Img(Store.siteLogo, Modifier.width(240.dp).height(46.dp), scale = ContentScale.Fit)
            else LocalLogo(Modifier.width(240.dp).height(46.dp))
            Spacer(Modifier.height(16.dp))
            Text(
                if (s.then != null) "Entrá con tu cuenta para ver este video" else "Entrá con tu cuenta para ver series y películas",
                style = ts(14, color = C.fg2).copy(textAlign = TextAlign.Center)
            )
            Spacer(Modifier.height(26.dp))
            Field("EMAIL", email, { email = it }, KeyboardType.Email, ImeAction.Next, onNext = { focus.moveFocus(FocusDirection.Down) })
            Spacer(Modifier.height(12.dp))
            Field("CONTRASEÑA", pass, { pass = it }, KeyboardType.Password, ImeAction.Done, secret = !show, onDone = { run(false) },
                trailing = { Text(if (show) "Ocultar" else "Ver", style = ts(12, FontWeight.Bold, C.accent), modifier = Modifier.tap({ show = !show }).padding(6.dp)) })
            if (err.isNotBlank()) { Spacer(Modifier.height(12.dp)); Text(err, style = ts(13, FontWeight.Bold, C.accent).copy(textAlign = TextAlign.Center)) }
            Spacer(Modifier.height(22.dp))
            PillButton(if (busy) "Entrando…" else "Entrar", { run(false) }, Modifier.fillMaxWidth(), icon = null)
            Spacer(Modifier.height(10.dp))
            PillButton("Crear cuenta", { run(true) }, Modifier.fillMaxWidth(), icon = null, dark = true)
            if (!s.required) {
                Spacer(Modifier.height(10.dp))
                Text("Ahora no", style = ts(13.5f, FontWeight.Bold, C.muted), modifier = Modifier.tap({ nav.back() }).padding(12.dp))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Field(
    label: String, value: String, onChange: (String) -> Unit, type: KeyboardType, ime: ImeAction,
    secret: Boolean = false, onNext: () -> Unit = {}, onDone: () -> Unit = {}, trailing: (@Composable () -> Unit)? = null
) {
    Column(
        Modifier.fillMaxWidth().background(Color.White.copy(alpha = .06f), RoundedCornerShape(14.dp))
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
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassButton(Ic.back, "Volver", { nav.back() }, size = 40.dp)
            Text(if (s != null) "Mi cuenta" else "Entrar", style = ts(24, FontWeight.Bold, Color.White), modifier = Modifier.padding(start = 14.dp))
        }
        Spacer(Modifier.height(24.dp))
        if (s != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(60.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Color(0xFFFFD1E6), C.accent, Color(0xFFB04C86)))), contentAlignment = Alignment.Center) {
                    val ph = avatarRes(s.avatar)
                    if (ph != null) androidx.compose.foundation.Image(painterResource(ph), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Text("★", style = ts(26, color = Color.White))
                }
                Text(s.email, style = ts(16, FontWeight.Bold), modifier = Modifier.padding(start = 16.dp))
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
    }
}
