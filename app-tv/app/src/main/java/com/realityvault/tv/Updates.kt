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

/* ======================================================================
   Sistema de actualizaciones (APK fuera de la Play Store)
   ------------------------------------------------------------------------
   La app NUNCA decide sola cuál es "la última versión": todo sale de un
   manifiesto JSON remoto (BuildConfig.UPDATE_URL). Publicar una versión
   nueva es, de este lado, solo actualizar ese JSON — el código del
   updater no cambia.
   ====================================================================== */

/** Lo que trae el manifiesto remoto. */
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val notes: String,
    val sha256: String,
    val mandatory: Boolean
)

sealed class DownloadState {
    data object Idle : DownloadState()
    data class Progress(val pct: Int) : DownloadState()
    data object Verifying : DownloadState()
    data object ReadyToInstall : DownloadState()
    data class Failed(val msg: String) : DownloadState()
    data object Cancelled : DownloadState()
}

object Updater {
    /** Actualización detectada y pendiente de mostrar/instalar (null = no hay nada nuevo). */
    var available by mutableStateOf<UpdateInfo?>(null)
    var download by mutableStateOf<DownloadState>(DownloadState.Idle)
    var checking by mutableStateOf(false)

    /** Mensaje para "Buscar actualizaciones" manual (Ajustes / Acerca de). Se limpia solo. */
    var lastResult by mutableStateOf<String?>(null)

    private lateinit var app: Context
    private val prefs get() = app.getSharedPreferences("rv_update", Context.MODE_PRIVATE)
    private var downloadJob: kotlinx.coroutines.Job? = null
    private var apkFile: File? = null

    // no comprobar más de 1 vez cada 4 horas salvo que el usuario lo pida a mano
    private const val MIN_CHECK_INTERVAL_MS = 4 * 60 * 60_000L

    fun init(ctx: Context) {
        app = ctx.applicationContext
    }

    private fun httpGetJson(url: String): String = run {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "GET"
            c.connectTimeout = 10_000
            c.readTimeout = 15_000
            c.setRequestProperty("Accept", "application/json")
            val code = c.responseCode
            if (code !in 200..299) throw Exception("HTTP $code")
            c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    /**
     * Comprobación silenciosa: para el arranque y el chequeo periódico. Nunca interrumpe nada —
     * si falla (sin internet, servidor caído, URL no configurada) la app sigue funcionando igual.
     */
    suspend fun checkSilently() {
        val last = prefs.getLong("lastCheck", 0)
        val now = System.currentTimeMillis()
        if (now - last < MIN_CHECK_INTERVAL_MS) return
        check(manual = false, ignoreVersion = false)
    }

    /** "Buscar actualizaciones" manual: siempre avisa el resultado, aunque ya esté al día. */
    suspend fun checkManually() = check(manual = true, ignoreVersion = false)

    /**
     * Solo para PROBAR el diálogo y el flujo real de descarga/verificación/instalación con el
     * manifiesto y el APK publicados de verdad, sin tener que subir una versión falsa al servidor.
     * Ignora la comparación de versionCode a propósito — es una herramienta de prueba, se nota
     * en el mensaje que muestra.
     */
    suspend fun simulateForTesting() = check(manual = true, ignoreVersion = true)

    private suspend fun check(manual: Boolean, ignoreVersion: Boolean) {
        if (checking) return
        checking = true
        if (manual) lastResult = null
        withContext(Dispatchers.IO) {
            try {
                val url = BuildConfig.UPDATE_URL
                if (url.isBlank()) {
                    if (manual) lastResult = "El servidor de actualizaciones no está configurado."
                    return@withContext
                }
                val j = JSONObject(httpGetJson(url))
                val info = UpdateInfo(
                    versionCode = j.optInt("versionCode", -1),
                    versionName = j.optString("versionName"),
                    apkUrl = j.optString("apkUrl"),
                    notes = j.optString("notes"),
                    sha256 = j.optString("sha256").trim().lowercase(),
                    mandatory = j.optBoolean("mandatory", false)
                )
                prefs.edit().putLong("lastCheck", System.currentTimeMillis()).apply()
                if (info.versionCode <= 0 || info.apkUrl.isBlank()) {
                    if (manual) lastResult = "No se pudo leer la información de la última versión."
                    return@withContext
                }
                if (ignoreVersion || info.versionCode > BuildConfig.VERSION_CODE) {
                    available = info
                    if (manual) lastResult = if (ignoreVersion) "Simulación: mostrando el aviso con la versión publicada (${info.versionName})." else "Hay una versión nueva disponible: ${info.versionName}."
                } else {
                    if (manual) lastResult = "Ya tenés instalada la última versión (${BuildConfig.VERSION_NAME})."
                }
            } catch (e: Exception) {
                if (manual) lastResult = "No se pudo comprobar: revisá la conexión a internet."
                // si no es manual, queda en silencio — nunca bloquea ni molesta al usuario
            }
        }
        checking = false
    }

    fun dismiss() {
        available = null
        download = DownloadState.Idle
    }

    private fun updatesDir(): File {
        val d = File(app.getExternalFilesDir(null), "updates")
        if (!d.exists()) d.mkdirs()
        return d
    }

    /** Ya hay un APK de ESTA versión descargado y verificado — no hace falta bajarlo de nuevo. */
    private fun alreadyDownloaded(info: UpdateInfo): File? {
        val f = File(updatesDir(), "update-${info.versionCode}.apk")
        if (!f.exists()) return null
        if (info.sha256.isBlank()) return f
        return if (sha256(f).equals(info.sha256, ignoreCase = true)) f else { f.delete(); null }
    }

    fun startDownload(info: UpdateInfo, scope: CoroutineScope) {
        downloadJob?.cancel()
        download = DownloadState.Progress(0)
        downloadJob = scope.launch(Dispatchers.IO) {
            alreadyDownloaded(info)?.let {
                apkFile = it
                download = DownloadState.ReadyToInstall
                return@launch
            }
            val out = File(updatesDir(), "update-${info.versionCode}.apk")
            try {
                val c = URL(info.apkUrl).openConnection() as HttpURLConnection
                try {
                    c.connectTimeout = 15_000
                    c.readTimeout = 20_000
                    val total = c.contentLengthLong
                    var read = 0L
                    c.inputStream.use { input ->
                        out.outputStream().use { output ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                ensureActive()
                                val n = input.read(buf)
                                if (n < 0) break
                                output.write(buf, 0, n)
                                read += n
                                val pct = if (total > 0) ((read * 100) / total).toInt().coerceIn(0, 100) else -1
                                download = DownloadState.Progress(pct)
                            }
                        }
                    }
                    if (total > 0 && read != total) throw Exception("La descarga quedó incompleta.")
                } finally {
                    c.disconnect()
                }

                download = DownloadState.Verifying
                if (info.sha256.isNotBlank() && !sha256(out).equals(info.sha256, ignoreCase = true)) {
                    throw Exception("El archivo descargado no coincide con el publicado (verificación fallida).")
                }
                // Confirma que el APK descargado es de ESTA app antes de ofrecer instalarlo.
                val pkgInfo = app.packageManager.getPackageArchiveInfo(out.absolutePath, 0)
                    ?: throw Exception("El archivo descargado no es un APK válido.")
                if (pkgInfo.packageName != app.packageName) {
                    throw Exception("El archivo descargado no corresponde a esta aplicación.")
                }

                apkFile = out
                download = DownloadState.ReadyToInstall
            } catch (e: CancellationException) {
                out.delete()
                download = DownloadState.Cancelled
            } catch (e: Exception) {
                out.delete()
                val msg = when {
                    e.message?.contains("ENOSPC", true) == true || e.message?.contains("No space", true) == true ->
                        "No hay espacio suficiente para descargar la actualización."
                    else -> e.message ?: "No se pudo descargar la actualización."
                }
                download = DownloadState.Failed(msg)
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
    }

    private fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** API 26+: hace falta que el usuario autorice instalar desde esta app. */
    fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || app.packageManager.canRequestPackageInstalls()

    fun requestInstallPermission(ctx: Context) {
        val i = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName))
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(i) }
    }

    /** Lanza el instalador real de Android — nunca instala en silencio, el sistema pide confirmación. */
    fun install(ctx: Context): Boolean {
        val f = apkFile ?: return false
        return runCatching {
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
            val i = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(i)
        }.isSuccess
    }
}

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
