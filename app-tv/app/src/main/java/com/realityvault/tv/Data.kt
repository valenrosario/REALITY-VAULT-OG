package com.realityvault.tv

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/* ---------------- modelos ---------------- */

data class Episode(
    val number: Int, val title: String, val description: String,
    val duration: String, val url: String, val thumb: String
)

data class Season(val number: Int, val name: String, val episodes: List<Episode>)

data class Title(
    val id: String, val type: String, val title: String, val tagline: String,
    val description: String, val year: String, val yearEnd: String, val rating: String,
    val duration: String, val genres: List<String>, val poster: String, val backdrop: String,
    val logo: String, val thumbWide: String, val bannerImage: String, val url: String,
    val trailer: String, val featured: Boolean, val status: String, val availability: String,
    val releaseDate: String, val createdAt: Long, val seasons: List<Season>
) {
    val isSerie get() = type == "serie"
    val isSoon get() = availability == "proximamente"
    val wide get() = thumbWide.ifBlank { backdrop.ifBlank { bannerImage.ifBlank { poster } } }
    val bg get() = backdrop.ifBlank { bannerImage.ifBlank { thumbWide.ifBlank { poster } } }
    fun meta(): String {
        val parts = mutableListOf<String>()
        if (year.isNotBlank()) parts += if (yearEnd.isNotBlank()) "$year–$yearEnd" else year
        if (isSerie) { if (seasons.isNotEmpty()) parts += "${seasons.size} temporada" + if (seasons.size == 1) "" else "s" }
        else if (duration.isNotBlank()) parts += duration
        if (genres.isNotEmpty()) parts += genres.take(2).joinToString(", ")
        return parts.joinToString("  •  ")
    }
}

data class Banner(
    val id: String, val titleId: String, val image: String, val headline: String,
    val text: String, val active: Boolean, val order: Int
)

data class FeatItem(val titleId: String, val label: String, val image: String)
data class FeatRow(val id: String, val title: String, val items: List<FeatItem>)

data class Progress(val titleId: String, val season: Int?, val episode: Int?, val watched: List<String>, val updated: String)

data class Session(val access: String, val refresh: String, val userId: String, val email: String, val expiresAt: Long)

/* ---------------- lectura de JSON ---------------- */

private fun JSONObject.s(k: String) = when (val v = opt(k)) { null, JSONObject.NULL -> ""; else -> v.toString() }
private fun JSONObject.i(k: String, d: Int = 0) = s(k).trim().toIntOrNull() ?: d
private fun JSONObject.strs(k: String): List<String> {
    val a = optJSONArray(k) ?: return emptyList()
    return (0 until a.length()).mapNotNull { a.optString(it).takeIf { s -> s.isNotBlank() } }
}

fun parseTitle(o: JSONObject): Title {
    val seasons = mutableListOf<Season>()
    o.optJSONArray("seasons")?.let { sa ->
        for (i in 0 until sa.length()) {
            val so = sa.optJSONObject(i) ?: continue
            val eps = mutableListOf<Episode>()
            so.optJSONArray("episodes")?.let { ea ->
                for (j in 0 until ea.length()) {
                    val eo = ea.optJSONObject(j) ?: continue
                    eps += Episode(eo.i("number", j + 1), eo.s("title"), eo.s("description"), eo.s("duration"), eo.s("url").trim(), eo.s("thumb"))
                }
            }
            seasons += Season(so.i("number", i + 1), so.s("name").ifBlank { so.s("title") }, eps)
        }
    }
    return Title(
        id = o.s("id"), type = o.s("type"), title = o.s("title"), tagline = o.s("tagline"),
        description = o.s("description"), year = o.s("year"), yearEnd = o.s("yearEnd"), rating = o.s("rating"),
        duration = o.s("duration"), genres = o.strs("genres"), poster = o.s("poster"), backdrop = o.s("backdrop"),
        logo = o.s("logo"), thumbWide = o.s("thumbWide"), bannerImage = o.s("bannerImage"), url = o.s("url").trim(),
        trailer = o.s("trailer").trim(), featured = o.optBoolean("featured"), status = o.s("status"),
        availability = o.s("availability"), releaseDate = o.s("releaseDate"), createdAt = o.optLong("createdAt"),
        seasons = seasons.sortedBy { it.number }
    )
}

fun isHttp(u: String) = u.startsWith("http://", true) || u.startsWith("https://", true)

/* ---------------- conexión con Supabase ---------------- */

object Api {
    private val base = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val key = BuildConfig.SUPABASE_KEY

    class HttpError(val code: Int, msg: String) : Exception(msg)

    suspend fun call(
        method: String, path: String, body: String? = null, token: String? = null,
        prefer: String? = null
    ): String = withContext(Dispatchers.IO) {
        val c = URL(base + path).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 12000; c.readTimeout = 20000
            c.setRequestProperty("apikey", key)
            c.setRequestProperty("Authorization", "Bearer " + (token ?: key))
            c.setRequestProperty("Accept", "application/json")
            if (prefer != null) c.setRequestProperty("Prefer", prefer)
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                val msg = runCatching { JSONObject(text).let { j -> j.optString("msg").ifBlank { j.optString("error_description").ifBlank { j.optString("message") } } } }.getOrNull()
                throw HttpError(code, msg?.ifBlank { null } ?: "Error $code")
            }
            text
        } finally { c.disconnect() }
    }
}

/* ---------------- estado de la app ---------------- */

object Store {
    var titles by mutableStateOf<List<Title>>(emptyList())
    var banners by mutableStateOf<List<Banner>>(emptyList())
    var featRows by mutableStateOf<List<FeatRow>>(emptyList())
    var siteLogo by mutableStateOf("")
    var top10 by mutableStateOf<List<String>>(emptyList())
    var myList by mutableStateOf<List<String>>(emptyList())
    var progress by mutableStateOf<List<Progress>>(emptyList())
    var session by mutableStateOf<Session?>(null)
    var loaded by mutableStateOf(false)
    var offline by mutableStateOf(false)

    private lateinit var app: Context
    private val prefs get() = app.getSharedPreferences("rv", Context.MODE_PRIVATE)
    private val cacheFile get() = File(app.filesDir, "catalog.json")

    val viewerId: String get() {
        session?.let { return it.userId }
        var v = prefs.getString("viewer", null)
        if (v == null) { v = "tv-" + UUID.randomUUID().toString().take(12); prefs.edit().putString("viewer", v).apply() }
        return v
    }

    fun init(ctx: Context) {
        app = ctx.applicationContext
        prefs.getString("session", null)?.let { s ->
            runCatching {
                val j = JSONObject(s)
                session = Session(j.s("access"), j.s("refresh"), j.s("userId"), j.s("email"), j.optLong("expiresAt"))
            }
        }
        // arranque instantáneo con lo último que se descargó
        runCatching { if (cacheFile.exists()) applyCatalog(JSONObject(cacheFile.readText())) }
    }

    fun published() = titles.filter { it.status != "borrador" }
    fun byId(id: String) = titles.firstOrNull { it.id == id }

    private fun applyCatalog(j: JSONObject) {
        val ts = j.optJSONArray("titles") ?: JSONArray()
        titles = (0 until ts.length()).mapNotNull { k ->
            val r = ts.optJSONObject(k) ?: return@mapNotNull null
            val d = r.optJSONObject("data") ?: return@mapNotNull null
            if (d.s("id").isBlank()) d.put("id", r.s("id"))
            runCatching { parseTitle(d) }.getOrNull()
        }
        val bs = j.optJSONArray("banners") ?: JSONArray()
        banners = (0 until bs.length()).mapNotNull { k ->
            val d = bs.optJSONObject(k)?.optJSONObject("data") ?: return@mapNotNull null
            Banner(d.s("id").ifBlank { bs.optJSONObject(k)?.s("id") ?: "" }, d.s("titleId"), d.s("image"), d.s("headline"), d.s("text"),
                d.opt("active") != false, d.i("order"))
        }.filter { it.active }.sortedBy { it.order }
        val site = j.optJSONArray("site") ?: JSONArray()
        var rows = emptyList<FeatRow>()
        for (k in 0 until site.length()) {
            val r = site.optJSONObject(k) ?: continue
            val d = r.optJSONObject("data") ?: continue
            when (r.s("id")) {
                "config" -> siteLogo = d.s("logo")
                "rows" -> {
                    val ra = d.optJSONArray("rows") ?: JSONArray()
                    rows = (0 until ra.length()).mapNotNull { x ->
                        val ro = ra.optJSONObject(x) ?: return@mapNotNull null
                        val ia = ro.optJSONArray("items") ?: JSONArray()
                        FeatRow(ro.s("id"), ro.s("title"), (0 until ia.length()).mapNotNull { y ->
                            ia.optJSONObject(y)?.let { FeatItem(it.s("titleId"), it.s("label"), it.s("image")) }
                        })
                    }
                }
            }
        }
        featRows = rows
        loaded = true
    }

    suspend fun refreshCatalog() {
        try {
            val t = Api.call("GET", "/rest/v1/titles?select=id,data")
            val b = Api.call("GET", "/rest/v1/banners?select=id,data")
            val s = Api.call("GET", "/rest/v1/site?select=id,data")
            val j = JSONObject().put("titles", JSONArray(t)).put("banners", JSONArray(b)).put("site", JSONArray(s))
            applyCatalog(j)
            offline = false
            withContext(Dispatchers.IO) { runCatching { cacheFile.writeText(j.toString()) } }
        } catch (e: Exception) {
            offline = true
            loaded = true
        }
        refreshTop10()
    }

    suspend fun refreshTop10() {
        runCatching {
            val a = JSONArray(Api.call("POST", "/rest/v1/rpc/top10", "{}"))
            top10 = (0 until a.length()).mapNotNull { a.optJSONObject(it)?.s("title_id") }
        }
    }

    /* ---------- cuenta ---------- */

    private fun saveSession(j: JSONObject) {
        val user = j.optJSONObject("user")
        val s = Session(
            j.s("access_token"), j.s("refresh_token"), user?.s("id") ?: session?.userId ?: "",
            user?.s("email") ?: session?.email ?: "",
            System.currentTimeMillis() / 1000 + j.optLong("expires_in", 3600)
        )
        session = s
        prefs.edit().putString("session", JSONObject()
            .put("access", s.access).put("refresh", s.refresh).put("userId", s.userId)
            .put("email", s.email).put("expiresAt", s.expiresAt).toString()).apply()
    }

    suspend fun login(email: String, pass: String) {
        val body = JSONObject().put("email", email).put("password", pass).toString()
        saveSession(JSONObject(Api.call("POST", "/auth/v1/token?grant_type=password", body)))
        refreshUser()
    }

    suspend fun signup(email: String, pass: String) {
        val body = JSONObject().put("email", email).put("password", pass).toString()
        val r = JSONObject(Api.call("POST", "/auth/v1/signup", body))
        if (r.s("access_token").isNotBlank()) { saveSession(r); refreshUser() }
        else login(email, pass)
    }

    fun logout() {
        session = null; myList = emptyList(); progress = emptyList()
        prefs.edit().remove("session").apply()
    }

    /** Token válido (lo renueva si está por vencer). */
    suspend fun token(): String? {
        val s = session ?: return null
        if (s.expiresAt - 60 > System.currentTimeMillis() / 1000) return s.access
        return try {
            val body = JSONObject().put("refresh_token", s.refresh).toString()
            saveSession(JSONObject(Api.call("POST", "/auth/v1/token?grant_type=refresh_token", body)))
            session?.access
        } catch (e: Api.HttpError) {
            if (e.code in 400..499) logout()
            null
        } catch (e: Exception) { null }
    }

    suspend fun refreshUser() {
        val tk = token() ?: return
        runCatching {
            val a = JSONArray(Api.call("GET", "/rest/v1/user_lists?select=title_id&order=created_at.desc", token = tk))
            myList = (0 until a.length()).mapNotNull { a.optJSONObject(it)?.s("title_id") }
        }
        runCatching {
            val a = JSONArray(Api.call("GET", "/rest/v1/watch_progress?select=*&order=updated_at.desc", token = tk))
            progress = (0 until a.length()).mapNotNull { k ->
                val o = a.optJSONObject(k) ?: return@mapNotNull null
                val w = o.optJSONArray("watched")
                Progress(o.s("title_id"), o.s("last_season").toIntOrNull(), o.s("last_episode").toIntOrNull(),
                    if (w == null) emptyList() else (0 until w.length()).map { w.optString(it) }, o.s("updated_at"))
            }
        }
    }

    suspend fun toggleList(id: String) {
        val tk = token() ?: return
        val uid = session?.userId ?: return
        val on = id in myList
        myList = if (on) myList - id else listOf(id) + myList
        runCatching {
            if (on) Api.call("DELETE", "/rest/v1/user_lists?user_id=eq.$uid&title_id=eq.${enc(id)}", token = tk)
            else Api.call("POST", "/rest/v1/user_lists", JSONObject().put("user_id", uid).put("title_id", id).toString(), tk, "return=minimal")
        }
    }

    suspend fun recordWatch(id: String, season: Int?, episode: Int?) {
        val tk = token() ?: return
        val uid = session?.userId ?: return
        val prev = progress.firstOrNull { it.titleId == id }
        val watched = LinkedHashSet(prev?.watched ?: emptyList())
        if (season != null && episode != null) watched += "$season:$episode"
        val now = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date())
        val p = Progress(id, season, episode, watched.toList(), now)
        progress = listOf(p) + progress.filter { it.titleId != id }
        val row = JSONObject().put("user_id", uid).put("title_id", id)
            .put("last_season", season ?: JSONObject.NULL).put("last_episode", episode ?: JSONObject.NULL)
            .put("watched", JSONArray(watched.toList())).put("updated_at", now)
        runCatching { Api.call("POST", "/rest/v1/watch_progress", row.toString(), tk, "resolution=merge-duplicates,return=minimal") }
    }

    suspend fun removeProgress(id: String) {
        val tk = token() ?: return
        val uid = session?.userId ?: return
        progress = progress.filter { it.titleId != id }
        runCatching { Api.call("DELETE", "/rest/v1/watch_progress?user_id=eq.$uid&title_id=eq.${enc(id)}", token = tk) }
    }

    private val lastViews = HashMap<String, Long>()
    suspend fun trackView(id: String) {
        val now = System.currentTimeMillis()
        if ((lastViews[id] ?: 0) > now - 10 * 60_000) return
        lastViews[id] = now
        runCatching {
            Api.call("POST", "/rest/v1/title_views", JSONObject().put("title_id", id).put("viewer", viewerId).toString(), prefer = "return=minimal")
        }
    }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")

    /** Lo que se reproduce al tocar "Ver" (mismo criterio que el sitio). */
    data class Target(val url: String, val season: Int?, val episode: Int?, val label: String, val cont: Boolean)

    fun playTarget(t: Title): Target? {
        if (t.isSoon) return null
        val p = progress.firstOrNull { it.titleId == t.id }
        if (!t.isSerie) return if (isHttp(t.url)) Target(t.url, null, null, if (p != null) "Volver a ver" else "Ver", false) else null
        val eps = t.seasons.flatMap { s -> s.episodes.filter { isHttp(it.url) }.map { s to it } }
        if (eps.isEmpty()) return null
        var i = 0; var cont = false
        if (p != null) {
            val j = eps.indexOfFirst { it.first.number == p.season && it.second.number == p.episode }
            if (j >= 0) { i = if (j + 1 < eps.size) j + 1 else j; cont = true }
        }
        val (s, e) = eps[i]
        return Target(e.url, s.number, e.number, "${if (cont) "Continuar" else "Ver"} T${s.number}:E${e.number}", cont)
    }
}
