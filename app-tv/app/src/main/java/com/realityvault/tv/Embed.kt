package com.realityvault.tv

import android.net.Uri

/** Cómo se reproduce un enlace: con el reproductor nativo (mp4 / m3u8) o con el reproductor del servicio (iframe). */
sealed class Media {
    data class Native(val url: String, val hls: Boolean) : Media()
    data class Embed(val url: String) : Media()
}

fun mediaFor(raw: String): Media? {
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
    if (Regex("\\.(mp4|webm|mkv|mov|m4v)$", RegexOption.IGNORE_CASE).containsMatchIn(path)) return Media.Native(url, false)
    if (Regex("\\.m3u8$", RegexOption.IGNORE_CASE).containsMatchIn(path)) return Media.Native(url, true)
    return Media.Embed(url)
}
