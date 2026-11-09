package com.realityvault.mobile

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.mutableStateListOf
import com.realityvault.tv.Store
import com.realityvault.tv.Title

/* ---------------- navegación ---------------- */

sealed class Screen {
    data object Splash : Screen()
    data object Home : Screen()
    data object Series : Screen()
    data object Movies : Screen()
    data object MyList : Screen()
    data object Search : Screen()
    data object Account : Screen()
    data class Detail(val id: String) : Screen()
    data class Player(val titleId: String, val url: String, val season: Int?, val episode: Int?, val trailer: Boolean = false, val extra: Boolean = false, val restart: Boolean = false) : Screen()
    /** required = true: es la puerta de entrada obligatoria, no se puede saltear. */
    data class Login(val then: Screen? = null, val required: Boolean = false) : Screen()
    data class Person(val name: String) : Screen()
    data class Collection(val id: String) : Screen()
    /** Categoría del inicio (pestañas de arriba): todos los títulos de un género. */
    data class Genre(val name: String) : Screen()
    data object Notifications : Screen()
    /** Pestaña "Mi Vault": perfil, Mi lista, seguir viendo, avisos y (si sos admin) el panel. */
    data object Mine : Screen()
    /** Panel de control (solo cuenta de administrador). page: inicio, catalogo, titulo, banners, banner, colecciones, coleccion, filas, fila, generos, sitio. */
    data class Admin(val page: String = "inicio", val id: String = "") : Screen()

    /** Las pantallas que muestran la barra de abajo. */
    val isTab get() = this is Home || this is Search || this is Mine
}

/** Guarda dónde estaba el scroll de cada pantalla para volver justo ahí. */
class Memory {
    private val lists = HashMap<String, LazyListState>()
    private val grids = HashMap<String, LazyGridState>()
    val text = HashMap<String, String>()
    fun list(k: String) = lists.getOrPut(k) { LazyListState() }
    fun grid(k: String) = grids.getOrPut(k) { LazyGridState() }
    /** Al entrar con otra cuenta (o una nueva) todo arranca desde arriba. */
    fun clear() { lists.clear(); grids.clear(); text.clear() }
}

class Nav {
    val stack = mutableStateListOf<Screen>(Screen.Splash)
    val top get() = stack.last()
    val memory = Memory()
    /** 1 = se abrió una pantalla encima, -1 = se volvió, 0 = cambio de sección (para animar distinto). */
    var dir = 0; private set

    fun go(s: Screen) { dir = 1; stack.add(s) }
    fun section(s: Screen) { dir = 0; stack.clear(); stack.add(s) }
    fun back(): Boolean { if (stack.size > 1) { dir = -1; stack.removeAt(stack.lastIndex); return true }; return false }
    fun replace(s: Screen) { dir = 0; stack[stack.lastIndex] = s }

    /** Ver algo: pide cuenta igual que el sitio. */
    fun play(t: Title, url: String, season: Int?, episode: Int?, restart: Boolean = false) {
        val p = Screen.Player(t.id, url, season, episode, restart = restart)
        if (Store.session == null) go(Screen.Login(p)) else go(p)
    }

    /** Ver un extra de temporada: no cuenta para "Seguir viendo" ni para el siguiente capítulo. */
    fun playExtra(t: Title, url: String) {
        val p = Screen.Player(t.id, url, null, null, extra = true)
        if (Store.session == null) go(Screen.Login(p)) else go(p)
    }
}
