package com.realityvault.tv

import android.net.Uri

/** Cómo se reproduce un enlace: con el reproductor nativo (mp4 / m3u8) o con el reproductor del servicio (iframe). */
sealed class Media {
    data class Native(val url: String, val hls: Boolean, val subsUrl: String = "") : Media()
    data class Embed(val url: String) : Media()
}

fun mediaFor(raw: String, subsUrl: String = ""): Media? {
    val url = raw.trim()
    if (!isHttp(url)) return null
    val u = runCatching { Uri.parse(url) }.getOrNull() ?: return null
    val h = (u.host ?: "").lowercase().removePrefix("www.").removePrefix("m.")
    val path = u.path ?: ""
    var id: String? = null
    if (h == "youtu.be") id = path.trim('/').substringBefore('/')
    else if (h.endsWith("youtube.com") || h == "youtube-nocookie.com") {
        id = u.getQueryParameter("v")
        if (id == null) Regex("/(embed|shorts|live|v)/([\\w-]{6,})").find(path)?.let { id = it.groupValues[2] }
    }
    if (!id.isNullOrBlank()) {
        val t = (u.getQueryParameter("t") ?: u.getQueryParameter("start") ?: "0").filter { it.isDigit() }.toIntOrNull() ?: 0
        return Media.Embed("https://www.youtube-nocookie.com/embed/$id?autoplay=1&rel=0&modestbranding=1&playsinline=1" + if (t > 0) "&start=$t" else "")
    }
    if (h == "vimeo.com" || h == "player.vimeo.com") {
        Regex("(\\d{5,})(?:/([0-9a-f]{6,}))?").find(path)?.let { m ->
            val hash = m.groupValues[2].ifBlank { u.getQueryParameter("h") ?: "" }
            return Media.Embed("https://player.vimeo.com/video/${m.groupValues[1]}?autoplay=1" + if (hash.isNotBlank()) "&h=$hash" else "")
        }
    }
    if (h == "drive.google.com") {
        val fid = Regex("/file/d/([^/]+)").find(path)?.groupValues?.get(1) ?: u.getQueryParameter("id")
        if (fid != null) return Media.Embed("https://drive.google.com/file/d/$fid/preview")
    }
    if (h == "dailymotion.com" || h == "dai.ly") {
        val vid = if (h == "dai.ly") path.trim('/') else Regex("video/([a-z0-9]+)", RegexOption.IGNORE_CASE).find(path)?.groupValues?.get(1)
        if (!vid.isNullOrBlank()) return Media.Embed("https://www.dailymotion.com/embed/video/$vid?autoplay=1")
    }
    if (h == "ok.ru") Regex("video(?:embed)?/(\\d+)").find(path)?.let { return Media.Embed("https://ok.ru/videoembed/${it.groupValues[1]}") }
    if (h == "archive.org") Regex("/(?:details|embed)/(.+)").find(path)?.let { return Media.Embed("https://archive.org/embed/${it.groupValues[1]}") }
    if (Regex("\\.(mp4|webm|mkv|mov|m4v|ts|m2ts|mts)$", RegexOption.IGNORE_CASE).containsMatchIn(path)) return Media.Native(url, false, subsUrl)
    if (Regex("\\.m3u8$", RegexOption.IGNORE_CASE).containsMatchIn(path)) return Media.Native(url, true, subsUrl)
    return Media.Embed(url)
}

/**
 * Baja el subtítulo y lo deja siempre como WebVTT: acepta .vtt, .srt o un .txt con formato SRT
 * (y textos guardados en Windows-1252). Devuelve un "data:" listo para el reproductor, o "" si no se pudo
 * (el video se ve igual, sin subtítulos). Llamar fuera del hilo principal.
 */
fun subsAsVtt(url: String): String = runCatching {
    val c = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply { connectTimeout = 8000; readTimeout = 12000 }
    val bytes = c.inputStream.use { it.readBytes() }
    if (bytes.size > 3_000_000) return ""
    var txt = String(bytes, Charsets.UTF_8)
    if (txt.contains('\uFFFD')) txt = String(bytes, charset("windows-1252"))
    txt = txt.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
    if (!txt.trimStart().startsWith("WEBVTT")) {
        if (!txt.contains("-->")) return ""
        txt = "WEBVTT\n\n" + txt.replace(Regex("(\\d{1,2}:\\d{2}:\\d{2}),(\\d{3})"), "$1.$2")
    }
    "data:text/vtt;base64," + android.util.Base64.encodeToString(txt.toByteArray(), android.util.Base64.NO_WRAP)
}.getOrDefault("")

/** Lee también los .ts de TV grabada que casi no traen cuadros IDR: sin esto, adelantar o saltear los deja trabados. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
fun rvExtractors() = androidx.media3.extractor.DefaultExtractorsFactory()
    .setTsExtractorFlags(androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES)
