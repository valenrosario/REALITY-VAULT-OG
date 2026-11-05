package com.realityvault.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.realityvault.tv.BuildConfig
import com.realityvault.tv.DownloadState
import com.realityvault.tv.Updater
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/* ---------------- actualizaciones dentro de la app ----------------
   La app revisa sola si hay versión nueva (al abrir y cada tanto) y avisa con un cartel.
   "Actualizar" la descarga, la verifica y abre el instalador de Android — sin bajar el APK a mano.
   La campanita del inicio muestra un punto mientras haya una versión nueva pendiente. */

object UpdateUi {
    /** Cartel abierto. Se abre solo al detectar una versión nueva, o desde la campanita. */
    var open by mutableStateOf(false)
    /** "Más tarde": no vuelve a abrirse solo para esta versión durante la sesión. */
    var snoozed = -1
}

/** Revisión silenciosa al abrir la app; si hay algo nuevo, abre el cartel. */
suspend fun autoCheckUpdates() {
    Updater.checkSilently()
    val a = Updater.available
    if (a != null && UpdateUi.snoozed != a.versionCode) UpdateUi.open = true
}

/** Campanita: punto rosa si hay una versión nueva; al tocarla busca y muestra el resultado. */
@Composable
fun BellButton(size: Dp = 40.dp, iconSize: Dp = 23.dp) {
    val scope = rememberCoroutineScope()
    Box(Modifier.size(size).tap({ UpdateUi.open = true; scope.launch { Updater.checkManually() } }, .9f), contentAlignment = Alignment.Center) {
        Icon(Ic.bell, "Novedades de la app", tint = Color.White, modifier = Modifier.size(iconSize))
        if (Updater.available != null) Box(Modifier.align(Alignment.Center).offset(x = 7.dp, y = (-8).dp).size(9.dp).background(C.accent, CircleShape))
    }
}

@Composable
fun UpdateDialog(nav: Nav) {
    if (!UpdateUi.open) return
    val top = nav.top
    if (top is Screen.Player || top is Screen.Splash) return
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val info = Updater.available
    val state = Updater.download
    fun close() { UpdateUi.open = false; info?.let { UpdateUi.snoozed = it.versionCode }; Updater.lastResult = null }

    // apenas termina la descarga verificada, se abre el instalador de Android
    LaunchedEffect(state) { if (state is DownloadState.ReadyToInstall && Updater.canInstall()) Updater.install(ctx) }
    // al volver de autorizar "instalar apps desconocidas", sigue solo
    LaunchedEffect(state) {
        while (state is DownloadState.ReadyToInstall && !Updater.canInstall()) { delay(700); if (Updater.canInstall()) { Updater.install(ctx); break } }
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = .72f)).tap({ if (state !is DownloadState.Progress) close() }, 1f),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.padding(24.dp).widthIn(max = 420.dp).fillMaxWidth().background(C.surface, RoundedCornerShape(20.dp)).tap({}, 1f).padding(24.dp)
        ) {
            when {
                info == null && Updater.checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = C.accent, strokeWidth = 2.5.dp, modifier = Modifier.size(22.dp))
                    Text("Buscando actualizaciones…", style = ts(15, FontWeight.Bold, Color.White), modifier = Modifier.padding(start = 14.dp))
                }
                info == null -> {
                    Text("Estás al día", style = ts(19, FontWeight.Bold, Color.White))
                    Text(Updater.lastResult ?: "Tenés la versión ${BuildConfig.VERSION_NAME}.", style = ts(14, color = C.fg2), modifier = Modifier.padding(top = 8.dp))
                    Spacer(Modifier.height(20.dp))
                    PillButton("Listo", { close() }, icon = null)
                }
                state is DownloadState.Progress -> {
                    Text("Descargando la actualización…", style = ts(17, FontWeight.Bold, Color.White))
                    Spacer(Modifier.height(16.dp))
                    Box(Modifier.fillMaxWidth().height(6.dp).background(Color.White.copy(alpha = .18f), RoundedCornerShape(99.dp))) {
                        Box(Modifier.fillMaxWidth((state.pct.coerceAtLeast(0) / 100f).coerceIn(0f, 1f)).height(6.dp).background(C.accent, RoundedCornerShape(99.dp)))
                    }
                    Text(if (state.pct >= 0) "${state.pct}%" else "Descargando…", style = ts(13, color = C.fg2), modifier = Modifier.padding(top = 8.dp))
                    Spacer(Modifier.height(18.dp))
                    PillButton("Cancelar", { Updater.cancelDownload() }, icon = null, dark = true)
                }
                state is DownloadState.Verifying -> Text("Verificando el archivo…", style = ts(16, FontWeight.Bold, Color.White))
                state is DownloadState.ReadyToInstall -> {
                    Text("Lista para instalar", style = ts(18, FontWeight.Bold, Color.White))
                    if (!Updater.canInstall()) {
                        Text("Android te va a pedir que autorices a Reality Vault a instalar actualizaciones. Es una sola vez.", style = ts(14, color = C.fg2), modifier = Modifier.padding(top = 8.dp))
                        Spacer(Modifier.height(18.dp))
                        PillButton("Autorizar", { Updater.requestInstallPermission(ctx) }, icon = null)
                    } else {
                        Text("Se abrió el instalador de Android: tocá \"Actualizar\".", style = ts(14, color = C.fg2), modifier = Modifier.padding(top = 8.dp))
                        Spacer(Modifier.height(18.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PillButton("Abrir instalador", { Updater.install(ctx) }, icon = null)
                            PillButton("Cerrar", { close() }, icon = null, dark = true)
                        }
                    }
                }
                else -> {
                    Text("Hay una versión nueva", style = ts(19, FontWeight.Bold, Color.White))
                    Text("Versión ${info.versionName}", style = ts(13, FontWeight.Bold, C.accent), modifier = Modifier.padding(top = 4.dp))
                    if (info.notes.isNotBlank()) Text(info.notes, style = ts(14, color = C.fg2), modifier = Modifier.padding(top = 12.dp))
                    if (state is DownloadState.Failed) Text(state.msg, style = ts(13, color = Color(0xFFFF8A8A)), modifier = Modifier.padding(top = 10.dp))
                    if (state is DownloadState.Cancelled) Text("Descarga cancelada.", style = ts(13, color = C.muted), modifier = Modifier.padding(top = 10.dp))
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PillButton(if (state is DownloadState.Failed) "Reintentar" else "Actualizar", { Updater.startDownload(info, scope) }, icon = null)
                        if (!info.mandatory) PillButton("Más tarde", { close() }, icon = null, dark = true)
                    }
                }
            }
        }
    }
}
