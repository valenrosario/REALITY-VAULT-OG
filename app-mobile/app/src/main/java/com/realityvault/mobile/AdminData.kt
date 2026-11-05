package com.realityvault.mobile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.realityvault.tv.Api
import com.realityvault.tv.BuildConfig
import com.realityvault.tv.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/* ======================================================================
   Panel de control: datos
   Trabaja sobre los mismos datos que el panel de la web (tablas titles, banners y site de
   Supabase, guardados como JSON). Se edita el JSON original, así nunca se pierde ningún campo
   que la app no conozca. Solo la cuenta de administrador puede escribir (lo controla Supabase).
   ====================================================================== */

object AdminData {
    var isAdmin by mutableStateOf(false); private set
    var titles by mutableStateOf<List<JSONObject>>(emptyList()); private set
    var banners by mutableStateOf<List<JSONObject>>(emptyList()); private set
    var rows by mutableStateOf<JSONArray>(JSONArray()); private set
    var colls by mutableStateOf<JSONArray>(JSONArray()); private set
    var site by mutableStateOf(JSONObject()); private set
    var loading by mutableStateOf(false); private set
    var loaded by mutableStateOf(false); private set
    /** Cambia con cada modificación local, para redibujar. */
    var ver by mutableIntStateOf(0)

    val GENRES = listOf("Reality", "Competencia", "Docureality", "Citas", "Supervivencia", "Cocina", "Talento", "Convivencia", "Drama", "Comedia", "Documental", "Thriller", "Romance", "Aventura")
    val RATINGS = listOf("ATP", "+7", "+13", "+16", "+18")

    fun uid(p: String) = p + java.lang.Long.toString(System.currentTimeMillis(), 36) + (1..6).map { "abcdefghijklmnopqrstuvwxyz0123456789".random() }.joinToString("")

    suspend fun checkAdmin() {
        val tk = Store.token()
        isAdmin = tk != null && runCatching { Api.call("POST", "/rest/v1/rpc/is_admin", "{}", tk).trim() == "true" }.getOrDefault(false)
    }

    private suspend fun rows(table: String): List<JSONObject> {
        val a = JSONArray(Api.call("GET", "/rest/v1/$table?select=id,data", token = Store.token()))
        return (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val d = o.optJSONObject("data") ?: JSONObject()
            d.put("id", o.optString("id"))
        }
    }

    suspend fun load() {
        if (loading) return
        loading = true
        try {
            titles = rows("titles").sortedByDescending { it.optInt("code", 0) }
            banners = rows("banners").sortedBy { it.optInt("order", 0) }
            val s = rows("site")
            rows = s.firstOrNull { it.optString("id") == "rows" }?.optJSONArray("rows") ?: JSONArray()
            colls = s.firstOrNull { it.optString("id") == "collections" }?.optJSONArray("list") ?: JSONArray()
            site = s.firstOrNull { it.optString("id") == "config" } ?: JSONObject()
            loaded = true; ver++
        } finally { loading = false }
    }

    private suspend fun upsert(table: String, id: String, data: JSONObject) {
        val tk = Store.token() ?: throw Exception("Tu sesión venció. Volvé a entrar.")
        val clean = JSONObject(data.toString()).apply { if (table != "titles" && table != "banners") remove("id") }
        val body = JSONObject().put("id", id).put("data", clean).put("updated_at", java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date()))
        Api.call("POST", "/rest/v1/$table", body.toString(), tk, "resolution=merge-duplicates,return=minimal")
    }

    /** Después de guardar: se actualiza el catálogo de la app para ver el cambio al instante. */
    private suspend fun after() { runCatching { Store.refreshCatalog() } }

    suspend fun saveTitle(t: JSONObject) {
        val isNew = t.optBoolean("_new")
        t.remove("_new")
        if (isNew) {
            t.put("code", (titles.maxOfOrNull { it.optInt("code", 0) } ?: 0) + 1)
            t.put("createdAt", System.currentTimeMillis())
        }
        t.put("updatedAt", System.currentTimeMillis())
        if (t.optString("type") != "serie") t.put("seasons", JSONArray())
        upsert("titles", t.getString("id"), t)
        val copy = JSONObject(t.toString())
        titles = (listOf(copy) + titles.filter { it.optString("id") != copy.optString("id") }).sortedByDescending { it.optInt("code", 0) }
        ver++; after()
    }

    suspend fun deleteTitle(id: String) {
        val tk = Store.token() ?: throw Exception("Tu sesión venció.")
        Api.call("DELETE", "/rest/v1/titles?id=eq.${enc(id)}", token = tk)
        titles = titles.filter { it.optString("id") != id }
        banners.filter { it.optString("titleId") == id }.forEach { b -> b.put("titleId", ""); runCatching { upsert("banners", b.getString("id"), b) } }
        ver++; after()
    }

    suspend fun saveBanner(b: JSONObject) {
        upsert("banners", b.getString("id"), b)
        val copy = JSONObject(b.toString())
        banners = (banners.filter { it.optString("id") != copy.optString("id") } + copy).sortedBy { it.optInt("order", 0) }
        ver++; after()
    }

    suspend fun deleteBanner(id: String) {
        val tk = Store.token() ?: throw Exception("Tu sesión venció.")
        Api.call("DELETE", "/rest/v1/banners?id=eq.${enc(id)}", token = tk)
        banners = banners.filter { it.optString("id") != id }
        ver++; after()
    }

    /** Reordena banners (1, 2, 3…) y guarda solo los que cambiaron. */
    suspend fun reorderBanners(list: List<JSONObject>) {
        list.forEachIndexed { i, b -> if (b.optInt("order", 0) != i + 1) { b.put("order", i + 1); upsert("banners", b.getString("id"), b) } }
        banners = list.map { JSONObject(it.toString()) }
        ver++; after()
    }

    suspend fun saveRows(a: JSONArray) { upsert("site", "rows", JSONObject().put("rows", a).put("updatedAt", System.currentTimeMillis())); rows = JSONArray(a.toString()); ver++; after() }
    suspend fun saveColls(a: JSONArray) { upsert("site", "collections", JSONObject().put("list", a).put("updatedAt", System.currentTimeMillis())); colls = JSONArray(a.toString()); ver++; after() }
    suspend fun saveSite(o: JSONObject) { o.put("updatedAt", System.currentTimeMillis()); upsert("site", "config", o); site = JSONObject(o.toString()); ver++; after() }

    fun titleById(id: String) = titles.firstOrNull { it.optString("id") == id }

    fun blankTitle(type: String): JSONObject = JSONObject()
        .put("id", uid("t")).put("type", type).put("title", "").put("tagline", "").put("description", "")
        .put("year", java.util.Calendar.getInstance().get(java.util.Calendar.YEAR).toString()).put("rating", "+13").put("duration", "")
        .put("genres", JSONArray()).put("tags", JSONArray()).put("cast", JSONArray()).put("director", "")
        .put("poster", "").put("backdrop", "").put("logo", "").put("bannerImage", "")
        .put("availability", "disponible").put("releaseDate", "").put("url", "").put("trailer", "")
        .put("featured", false).put("status", "borrador")
        .put("formats", JSONObject().put("uhd4k", false).put("hdr", false).put("atmos", false).put("surround51", false))
        .put("seasons", if (type == "serie") JSONArray().put(JSONObject().put("id", uid("s")).put("number", 1).put("title", "").put("episodes", JSONArray())) else JSONArray())
        .put("_new", true)

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")

    /* ---------------- imágenes ---------------- */

    /** [ancho máx, alto máx, transparencia] — los mismos tamaños que usa la web. */
    val SPECS = mapOf(
        "tv" to Triple(1920, 1080, false), "frow" to Triple(1000, 1480, false), "mobilebanner" to Triple(1350, 1700, false),
        "mobile" to Triple(1600, 1000, false), "sitelogo" to Triple(1400, 460, true), "poster" to Triple(1200, 1800, false),
        "backdrop" to Triple(2560, 1440, false), "logo" to Triple(1400, 560, true), "thumb" to Triple(1280, 720, false),
        "face" to Triple(400, 400, false), "banner" to Triple(3000, 1000, false), "coll" to Triple(3000, 1000, false)
    )
    /** Proporción de recorte de cada tipo (los logos no se recortan: conservan sus bordes transparentes). */
    val ASPECT = mapOf(
        "tv" to 16f / 9f, "frow" to 3f / 4.45f, "poster" to 2f / 3f, "backdrop" to 16f / 9f, "banner" to 3f, "coll" to 3f,
        "mobilebanner" to 4f / 5f, "mobile" to 16f / 10f, "thumb" to 16f / 9f, "face" to 1f
    )

    /** Abre la imagen elegida, achicada para no gastar memoria (y derecha según la foto). */
    suspend fun decode(ctx: Context, uri: Uri): Bitmap = withContext(Dispatchers.IO) {
        val cr = ctx.contentResolver
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
        var sample = 1
        while (o.outWidth / (sample * 2) >= 3000 || o.outHeight / (sample * 2) >= 3000) sample *= 2
        val bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            ?: throw Exception("No se pudo abrir la imagen.")
        val rot = runCatching {
            cr.openInputStream(uri)?.use { androidx.exifinterface.media.ExifInterface(it).rotationDegrees } ?: 0
        }.getOrDefault(0)
        if (rot == 0) bmp else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rot.toFloat()) }, true)
    }

    /** Baja una imagen de un enlace (un momento, solo para poder reencuadrarla). */
    suspend fun download(url: String): Bitmap = withContext(Dispatchers.IO) {
        val c = URL(url).openConnection() as HttpURLConnection
        val bytes = try {
            c.connectTimeout = 15000; c.readTimeout = 30000; c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", "RealityVault-Android")
            if (c.responseCode !in 200..299) throw Exception("No se pudo bajar la imagen (${c.responseCode}).")
            c.inputStream.use { it.readBytes() }
        } finally { c.disconnect() }
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        var sample = 1
        while (o.outWidth / (sample * 2) >= 3000 || o.outHeight / (sample * 2) >= 3000) sample *= 2
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw Exception("Ese enlace no es una imagen que se pueda abrir.")
    }

    /** Al cambiar de cuenta: se olvida todo lo del panel y se vuelve a comprobar. */
    fun reset() { isAdmin = false; loaded = false; titles = emptyList(); banners = emptyList(); rows = JSONArray(); colls = JSONArray(); site = JSONObject(); ver++ }

    /** Recorta (si hace falta), achica al tamaño del tipo, comprime en WebP y sube. Devuelve el enlace público. */
    suspend fun upload(src: Bitmap, kind: String, crop: android.graphics.Rect?): String = withContext(Dispatchers.IO) {
        val (mw, mh, alpha) = SPECS[kind] ?: SPECS.getValue("backdrop")
        val cut = if (crop != null) Bitmap.createBitmap(src, crop.left, crop.top, crop.width(), crop.height()) else src
        val scale = minOf(1f, mw.toFloat() / cut.width, mh.toFloat() / cut.height)
        val out = if (scale < 1f) Bitmap.createScaledBitmap(cut, (cut.width * scale).toInt().coerceAtLeast(1), (cut.height * scale).toInt().coerceAtLeast(1), true) else cut
        val bos = ByteArrayOutputStream()
        @Suppress("DEPRECATION")
        val fmt = if (Build.VERSION.SDK_INT >= 30) (if (alpha) Bitmap.CompressFormat.WEBP_LOSSLESS else Bitmap.CompressFormat.WEBP_LOSSY) else Bitmap.CompressFormat.WEBP
        out.compress(fmt, if (alpha && Build.VERSION.SDK_INT >= 30) 80 else 88, bos)
        val path = "$kind/${uid("m")}.webp"
        val tk = Store.token() ?: throw Exception("Tu sesión venció. Volvé a entrar.")
        val c = URL("${BuildConfig.SUPABASE_URL}/storage/v1/object/media/$path").openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"; c.doOutput = true
            c.connectTimeout = 15000; c.readTimeout = 60000
            c.setRequestProperty("apikey", BuildConfig.SUPABASE_KEY)
            c.setRequestProperty("Authorization", "Bearer $tk")
            c.setRequestProperty("Content-Type", "image/webp")
            c.setRequestProperty("cache-control", "max-age=31536000")
            c.setRequestProperty("x-upsert", "false")
            c.outputStream.use { it.write(bos.toByteArray()) }
            if (c.responseCode !in 200..299) throw Exception("No se pudo subir la imagen (${c.responseCode}).")
        } finally { c.disconnect() }
        "${BuildConfig.SUPABASE_URL}/storage/v1/object/public/media/$path"
    }
}
