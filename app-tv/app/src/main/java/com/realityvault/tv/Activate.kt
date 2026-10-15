package com.realityvault.tv

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.delay
import java.security.SecureRandom

private const val CHARS = "ABCDEFGHJKLMNPRSTUVWXYZ23456789"
private val rnd = SecureRandom()
private fun randomCode() = (1..6).map { CHARS[rnd.nextInt(CHARS.length)] }.joinToString("")
private fun randomSecret() = (1..40).map { "abcdefghijklmnopqrstuvwxyz0123456789"[rnd.nextInt(36)] }.joinToString("")

private val siteHost get() = BuildConfig.SITE_URL.removePrefix("https://").removePrefix("http://").trimEnd('/')

/** Pantalla para entrar: activar con el celular (QR + código) o con email y contraseña. */
@Composable
fun LoginScreen(s: Screen.Login, nav: Nav) {
    var useEmail by remember { mutableStateOf(false) }
    var unavailable by remember { mutableStateOf(false) }
    fun done() { if (s.then != null) nav.replace(s.then) else nav.back() }
    if (useEmail || unavailable) EmailLogin(nav, ::done, if (unavailable) "La activación con el celular todavía no está configurada. Entrá con tu email y contraseña." else null) { useEmail = false }
    else ActivateTv(nav, ::done, onEmail = { useEmail = true }, onUnavailable = { unavailable = true })
}

@Composable
fun ActivateTv(nav: Nav, done: () -> Unit, onEmail: () -> Unit, onUnavailable: () -> Unit) {
    var round by remember { mutableIntStateOf(0) }
    var code by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var startedAt by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var status by remember { mutableStateOf("Generando código…") }
    val emailReq = remember { FocusRequester() }
    AutoFocus(emailReq)

    // crea un código nuevo (y otro cada vez que vence)
    LaunchedEffect(round) {
        code = ""; status = "Generando código…"
        var tries = 0
        while (true) {
            val c = randomCode(); val sc = randomSecret()
            val ok = try { Store.tvCreate(c, sc) } catch (e: Api.HttpError) { if (e.code == 404 || e.code == 400) { onUnavailable(); return@LaunchedEffect }; false } catch (e: Exception) { false }
            if (ok) { code = c; secret = sc; startedAt = System.currentTimeMillis(); status = "Esperando que actives desde el celular…"; break }
            if (++tries > 4) { status = "Sin conexión. Reintentando…"; delay(4000); tries = 0 }
        }
        // espera a que lo actives desde el celular
        while (true) {
            delay(2000)
            now = System.currentTimeMillis()
            val st = try { Store.tvStatus(code, secret) } catch (e: Exception) { "error" }
            when (st) {
                "listo" -> {
                    status = "¡Listo! Entrando…"
                    try { Store.tvFinish(code, secret); done(); return@LaunchedEffect }
                    catch (e: Api.HttpError) { if (e.code == 404) { onUnavailable(); return@LaunchedEffect }; status = "No se pudo entrar. Generando otro código…"; delay(2500); round++; return@LaunchedEffect }
                    catch (e: Exception) { status = "No se pudo entrar. Generando otro código…"; delay(2500); round++; return@LaunchedEffect }
                }
                "vencido" -> { round++; return@LaunchedEffect }
            }
            if (now - startedAt > 15 * 60_000) { round++; return@LaunchedEffect }
        }
    }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }

    val left = ((15 * 60_000 - (now - startedAt)) / 1000).coerceIn(0, 15 * 60)
    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF2A1520), Color.Black), center = Offset(1400f, 400f), radius = 1300f))) {
        Column(Modifier.padding(start = 64.dp, top = 40.dp).width(470.dp)) {
            if (Store.siteLogo.isNotBlank()) Img(Store.siteLogo, Modifier.height(24.dp).width(160.dp), scale = ContentScale.Fit, alignment = Alignment.CenterStart)
            else LocalLogo(Modifier.height(24.dp).width(160.dp))
            Spacer(Modifier.height(30.dp))
            Text("Activá tu tele", style = ts(30, FontWeight.Bold, Color.White))
            Spacer(Modifier.height(6.dp))
            Text("Usá tu celular para entrar con tu cuenta, sin escribir con el control.", style = ts(13, FontWeight.Light, C.fg2))
            Spacer(Modifier.height(24.dp))
            Step(1) {
                Text(buildAnnotatedString {
                    append("Escaneá el código QR con la cámara del celular, o entrá a ")
                    withStyle(SpanStyle(color = C.accent, fontWeight = FontWeight.Bold)) { append("$siteHost/activar") }
                }, style = ts(14, color = C.fg))
            }
            Step(2) { Text("Iniciá sesión o creá tu cuenta gratis.", style = ts(14)) }
            Step(3) {
                Column {
                    Text("Escribí este código:", style = ts(14))
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        (0 until 6).forEach { i ->
                            if (i == 3) Spacer(Modifier.width(8.dp))
                            Box(
                                Modifier.size(38.dp, 48.dp).background(Color.White.copy(alpha = .08f), RoundedCornerShape(8.dp))
                                    .border(1.dp, Color.White.copy(alpha = .22f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) { Text(code.getOrNull(i)?.toString() ?: "", style = ts(26, FontWeight.Bold, Color.White)) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (code.isNotBlank()) Text("El código vence en %d:%02d".format(left / 60, left % 60), style = ts(11, color = C.faint))
                }
            }
        }
        Row(Modifier.align(Alignment.BottomStart).padding(start = 64.dp, bottom = 40.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton("Entrar con email y contraseña", onEmail, icon = null, requester = emailReq)
            TextButtonTv("Ahora no", { nav.back() })
        }
        Column(Modifier.align(Alignment.CenterEnd).padding(end = 90.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(220.dp).background(Color.White, RoundedCornerShape(16.dp)).padding(14.dp), contentAlignment = Alignment.Center) {
                if (code.isNotBlank()) Qr("${BuildConfig.SITE_URL.trimEnd('/')}/activar?c=$code", Modifier.fillMaxSize())
            }
            Spacer(Modifier.height(14.dp))
            Text("Escaneá con la cámara", style = ts(13, color = C.fg2))
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).background(C.accent, CircleShape))
                Spacer(Modifier.width(7.dp))
                Text(status, style = ts(11, color = C.muted))
            }
        }
    }
}

@Composable
private fun Step(n: Int, content: @Composable () -> Unit) {
    Row(Modifier.padding(bottom = 16.dp)) {
        Box(Modifier.size(26.dp).border(1.dp, Color.White.copy(alpha = .45f), CircleShape), contentAlignment = Alignment.Center) {
            Text("$n", style = ts(13, FontWeight.Bold))
        }
        Spacer(Modifier.width(14.dp))
        Box(Modifier.padding(top = 3.dp)) { content() }
    }
}

/** Código QR dibujado directo (sin internet). */
@Composable
fun Qr(text: String, modifier: Modifier) {
    val m = remember(text) {
        QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M))
    }
    Canvas(modifier) {
        val n = m.width
        val cell = size.minDimension / n
        for (y in 0 until n) for (x in 0 until n) {
            if (m.get(x, y)) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + .6f, cell + .6f))
        }
    }
}
