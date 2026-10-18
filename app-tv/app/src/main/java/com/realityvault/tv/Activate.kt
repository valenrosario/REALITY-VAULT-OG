package com.realityvault.tv

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
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
    var unavailable by remember { mutableStateOf<String?>(null) }
    fun done() {
        when {
            s.then != null -> nav.replace(s.then)
            s.required -> nav.section(Screen.Home)
            else -> nav.back()
        }
    }
    if (useEmail || unavailable != null) EmailLogin(nav, ::done, unavailable?.let { "La activación con el celular no funcionó ($it). Entrá con tu email y contraseña." }, required = s.required) { useEmail = false }
    else ActivateTv(nav, ::done, onEmail = { useEmail = true }, onUnavailable = { unavailable = it }, required = s.required)
}

@Composable
fun ActivateTv(nav: Nav, done: () -> Unit, onEmail: () -> Unit, onUnavailable: (String) -> Unit, required: Boolean = false) {
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
            val ok = try { Store.tvCreate(c, sc) } catch (e: Api.HttpError) { if (e.code == 404 || e.code == 400) { onUnavailable("código: error ${e.code} · ${e.message}"); return@LaunchedEffect }; false } catch (e: Exception) { false }
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
                    catch (e: Api.HttpError) { if (e.code == 404 || e.code == 401 || e.code == 403) { onUnavailable("función tv-login: error ${e.code} · ${e.message}"); return@LaunchedEffect }; status = "No se pudo entrar (${e.code} · ${e.message}). Generando otro código…"; delay(4000); round++; return@LaunchedEffect }
                    catch (e: Exception) { status = "No se pudo entrar. Generando otro código…"; delay(2500); round++; return@LaunchedEffect }
                }
                "vencido" -> { round++; return@LaunchedEffect }
            }
            if (now - startedAt > 15 * 60_000) { round++; return@LaunchedEffect }
        }
    }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }

    val left = ((15 * 60_000 - (now - startedAt)) / 1000).coerceIn(0, 15 * 60)

    val infinite = rememberInfiniteTransition(label = "activate")
    val blob1 by infinite.animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b1")
    val blob2 by infinite.animateFloat(0f, 1f, infiniteRepeatable(tween(11000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b2")
    val qrPulse by infinite.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "qrPulse")
    val numPulse by infinite.animateFloat(0f, 1f, infiniteRepeatable(tween(2000, easing = LinearEasing)), label = "numPulse")

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // fondo: dos manchas de luz que flotan lento
        Box(
            Modifier.size(700.dp).offset(x = (-180 + 40 * blob1).dp, y = (-160 + 30 * blob1).dp)
                .background(Brush.radialGradient(listOf(Color(0x552A1520), Color.Transparent)), CircleShape)
        )
        Box(
            Modifier.size(620.dp).align(Alignment.BottomEnd).offset(x = (120 - 35 * blob2).dp, y = (140 - 25 * blob2).dp)
                .background(Brush.radialGradient(listOf(Color(0x2EF5A3C4), Color.Transparent)), CircleShape)
        )

        Column(Modifier.align(Alignment.CenterStart).padding(start = 64.dp).width(560.dp)) {
            if (Store.siteLogo.isNotBlank()) Img(Store.siteLogo, Modifier.height(26.dp).width(170.dp), scale = ContentScale.Fit, alignment = Alignment.CenterStart)
            else LocalLogo(Modifier.height(26.dp).width(170.dp))
            Spacer(Modifier.height(30.dp))
            Text("Activá tu tele", style = ts(36, FontWeight.Bold, Color.White))
            Spacer(Modifier.height(8.dp))
            Text("Escaneá el código, creá tu cuenta gratis en segundos y quedás conectado. Sin escribir nada con el control.", style = ts(14, FontWeight.Light, C.fg2))
            Spacer(Modifier.height(26.dp))
            Step(1, active = true, pulse = numPulse) {
                Text(buildAnnotatedString {
                    append("Escaneá el ")
                    withStyle(SpanStyle(color = C.accent, fontWeight = FontWeight.Bold)) { append("código QR") }
                    append(" con la cámara del celular, o entrá a ")
                    withStyle(SpanStyle(color = C.accent, fontWeight = FontWeight.Bold)) { append("$siteHost/activar") }
                }, style = ts(14, color = C.fg))
            }
            Step(2, active = false) { Text("Creá tu cuenta o iniciá sesión.", style = ts(14)) }
            Step(3, active = false) {
                Column {
                    Text("Confirmá este código:", style = ts(14))
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        (0 until 6).forEach { i ->
                            if (i == 3) Spacer(Modifier.width(8.dp))
                            val filled = code.getOrNull(i) != null
                            Box(
                                Modifier.size(38.dp, 48.dp)
                                    .background(if (filled) C.accent.copy(alpha = .1f) else Color.White.copy(alpha = .07f), RoundedCornerShape(8.dp))
                                    .border(1.dp, if (filled) C.accent.copy(alpha = .55f) else Color.White.copy(alpha = .2f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) { Text(code.getOrNull(i)?.toString() ?: "", style = ts(26, FontWeight.Bold, Color.White)) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (code.isNotBlank()) Text("Vence en %d:%02d".format(left / 60, left % 60), style = ts(11, color = C.faint))
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton("Entrar con email y contraseña", onEmail, icon = null, requester = emailReq)
                if (!required) TextButtonTv("Ahora no", { nav.back() })
            }
        }

        Column(Modifier.align(Alignment.CenterEnd).padding(end = 90.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(248.dp), contentAlignment = Alignment.Center) {
                // anillo que pulsa alrededor del QR
                val ringAlpha = (1f - qrPulse) * .55f
                val ringScale = 0.92f + qrPulse * .14f
                Canvas(
                    Modifier.size(248.dp).graphicsLayer { scaleX = ringScale; scaleY = ringScale; alpha = ringAlpha }
                ) {
                    drawRoundRect(
                        C.accent, topLeft = Offset(4f, 4f),
                        size = Size(size.width - 8f, size.height - 8f),
                        cornerRadius = CornerRadius(28f, 28f), style = Stroke(2.dp.toPx())
                    )
                }
                Box(Modifier.size(220.dp).background(Color.White, RoundedCornerShape(20.dp)).padding(16.dp), contentAlignment = Alignment.Center) {
                    if (code.isNotBlank()) Qr("${BuildConfig.SITE_URL.trimEnd('/')}/activar?c=$code", Modifier.fillMaxSize())
                }
            }
            Spacer(Modifier.height(18.dp))
            Text("Escaneá con la cámara para registrarte", style = ts(14, color = C.fg2))
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).background(C.accent.copy(alpha = .4f + .6f * (1f - numPulse)), CircleShape))
                Spacer(Modifier.width(7.dp))
                Text(status, style = ts(11, color = C.muted))
            }
        }
    }
}

@Composable
private fun Step(n: Int, active: Boolean, pulse: Float = 0f, content: @Composable () -> Unit) {
    Row(Modifier.padding(bottom = 18.dp)) {
        Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
            if (active) Canvas(Modifier.size(30.dp)) {
                drawCircle(C.accent.copy(alpha = (1f - pulse) * .5f), radius = size.minDimension / 2 * (0.75f + pulse * .5f))
            }
            Box(
                Modifier.size(28.dp)
                    .background(if (active) C.accent.copy(alpha = .14f) else Color.Transparent, CircleShape)
                    .border(1.dp, if (active) C.accent.copy(alpha = .8f) else Color.White.copy(alpha = .4f), CircleShape),
                contentAlignment = Alignment.Center
            ) { Text("$n", style = ts(13, FontWeight.Bold, if (active) C.accent else Color.White)) }
        }
        Spacer(Modifier.width(14.dp))
        Box(Modifier.padding(top = 4.dp)) { content() }
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
