package com.realityvault.tv

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/* ---------------- diálogo de actualización, estilo TV ---------------- */

/**
 * Se muestra sobre cualquier pantalla EXCEPTO el reproductor (nunca interrumpe un video en curso)
 * y la pantalla de entrada obligatoria. "Más tarde" lo oculta por el resto de esta sesión para esa
 * versión puntual — no insiste en cada pantalla, pero vuelve a avisar si se reabre la app.
 */
@Composable
fun UpdateOverlay(nav: Nav) {
    val info = Updater.available ?: return
    val top = nav.top
    if (top is Screen.Player || top is Screen.Splash || (top is Screen.Login && top.required)) return

    var dismissed by remember(info.versionCode) { mutableStateOf(false) }
    if (dismissed) return

    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = Updater.download
    val req = remember { FocusRequester() }
    AutoFocus(req, info.versionCode)

    // Apenas la descarga queda lista y verificada, se abre el instalador real de Android.
    LaunchedEffect(state) {
        if (state is DownloadState.ReadyToInstall && Updater.canInstall()) Updater.install(ctx)
    }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .78f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(min = 460.dp, max = 620.dp).background(C.surface, RoundedCornerShape(20.dp)).padding(36.dp)
        ) {
            when (state) {
                is DownloadState.Idle, is DownloadState.Cancelled -> {
                    Text("Hay una nueva versión disponible", style = ts(20, FontWeight.Bold, Color.White))
                    Spacer(Modifier.height(8.dp))
                    Text("Versión ${info.versionName}", style = ts(14, FontWeight.Bold, C.accent))
                    if (info.notes.isNotBlank()) {
                        Spacer(Modifier.height(14.dp))
                        Text(info.notes, style = ts(14, color = C.fg2))
                    }
                    if (state is DownloadState.Cancelled) {
                        Spacer(Modifier.height(10.dp))
                        Text("Descarga cancelada.", style = ts(13, color = C.muted))
                    }
                    Spacer(Modifier.height(26.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        PillButton("Actualizar ahora", { Updater.startDownload(info, scope) }, icon = null, requester = req)
                        if (!info.mandatory) TextButtonTv("Más tarde", { dismissed = true })
                    }
                }
                is DownloadState.Progress -> {
                    Text("Descargando actualización…", style = ts(18, FontWeight.Bold, Color.White))
                    Spacer(Modifier.height(18.dp))
                    Box(Modifier.fillMaxWidth().height(6.dp).background(Color.White.copy(alpha = .18f), RoundedCornerShape(99.dp))) {
                        val frac = (if (state.pct >= 0) state.pct else 0) / 100f
                        Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).height(6.dp).background(C.accent, RoundedCornerShape(99.dp)))
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(if (state.pct >= 0) "${state.pct}%" else "Descargando…", style = ts(13, color = C.fg2))
                    Spacer(Modifier.height(24.dp))
                    TextButtonTv("Cancelar", { Updater.cancelDownload() }, requester = req)
                }
                is DownloadState.Verifying -> {
                    Text("Verificando el archivo…", style = ts(18, FontWeight.Bold, Color.White))
                    Spacer(Modifier.height(8.dp))
                    Text("Comprobando que la descarga esté completa y sea segura.", style = ts(13, color = C.fg2))
                }
                is DownloadState.ReadyToInstall -> {
                    Text("Descarga lista", style = ts(18, FontWeight.Bold, Color.White))
                    Spacer(Modifier.height(10.dp))
                    if (!Updater.canInstall()) {
                        Text("Para instalar, autorizá a Reality Vault a instalar aplicaciones desde esta fuente.", style = ts(14, color = C.fg2))
                        Spacer(Modifier.height(18.dp))
                        PillButton("Autorizar", { Updater.requestInstallPermission(ctx) }, icon = null, requester = req)
                    } else {
                        Text("Abriendo el instalador de Android…", style = ts(14, color = C.fg2))
                    }
                }
                is DownloadState.Failed -> {
                    Text("No se pudo actualizar", style = ts(18, FontWeight.Bold, Color.White))
                    Spacer(Modifier.height(8.dp))
                    Text(state.msg, style = ts(14, color = C.fg2))
                    Spacer(Modifier.height(22.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        PillButton("Reintentar", { Updater.startDownload(info, scope) }, icon = null, requester = req)
                        TextButtonTv("Cerrar", { dismissed = true })
                    }
                }
            }
        }
    }
}
