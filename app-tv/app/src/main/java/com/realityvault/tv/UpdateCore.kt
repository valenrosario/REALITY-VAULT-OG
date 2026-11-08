package com.realityvault.tv

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

// Lógica compartida con la app del celular (app-mobile la copia al compilar). El diálogo de cada app va aparte.

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
        try { withContext(Dispatchers.IO) {
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
        } } finally { checking = false }
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

