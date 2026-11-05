package com.realityvault.mobile

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.realityvault.tv.CastMember
import com.realityvault.tv.Episode
import com.realityvault.tv.Progress
import com.realityvault.tv.Season
import com.realityvault.tv.Store
import com.realityvault.tv.Title
import com.realityvault.tv.isHttp
import com.realityvault.tv.mediaFor
import com.realityvault.tv.posKey

@Composable
fun DetailScreen(id: String, nav: Nav) {
    val t = Store.byId(id)
    if (t == null) { Box(Modifier.fillMaxSize()) { Empty("Este título ya no está disponible."); BackFab(nav) }; return }
    LaunchedEffect(id) { Store.trackView(id) }
    val target = Store.playTarget(t)
    val prog = Store.progress.firstOrNull { it.titleId == id }
    var season by remember(id) {
        mutableIntStateOf(t.seasons.indexOfFirst { it.number == prog?.season }.coerceAtLeast(0))
    }
    var more by remember(id) { mutableStateOf(false) }
    val heroImg = t.backdropMobile.ifBlank { t.backdrop.ifBlank { t.bannerImage.ifBlank { t.poster } } }
    val tint = rememberTint(heroImg)
    val related = remember(id, Store.titles) {
        Store.published().filter { o -> o.id != t.id && o.genres.any { it in t.genres } }
            .sortedByDescending { o -> o.genres.count { it in t.genres } }.take(14)
    }
    val cfg = LocalConfiguration.current
    val heroH = (cfg.screenWidthDp * 0.78f).dp
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to tint.lighten(.08f), .5f to tint, 1f to tint.darken(.6f)))) {
        LazyColumn(state = nav.memory.list("detail-$id"), modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottom + 40.dp)) {
            // portada
            item(key = "hero") {
                Box(Modifier.fillMaxWidth().height(heroH)) {
                    if (heroImg.isNotBlank()) Img(heroImg, Modifier.fillMaxSize(), alignment = Alignment.TopCenter) else Placeholder(t, Modifier.fillMaxSize(), big = true)
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = .35f), .3f to Color.Transparent, .62f to Color.Transparent, 1f to tint)))
                }
            }
            // título, datos y botones
            item(key = "info") {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    TitleLogo(t, 260.dp, 84.dp, 30)
                    Spacer(Modifier.height(10.dp))
                    KindLine(t, center = true)
                    val facts = listOfNotNull(
                        t.year.takeIf { it.isNotBlank() }?.let { if (t.yearEnd.isNotBlank()) "$it – ${t.yearEnd}" else it },
                        if (t.isSerie && t.seasons.isNotEmpty()) "${t.seasons.size} temporada" + (if (t.seasons.size == 1) "" else "s") else t.duration.ifBlank { null }
                    ).joinToString(" · ")
                    if (facts.isNotBlank()) Text(facts, style = ts(12.5f, color = Color.White.copy(alpha = .8f)), modifier = Modifier.padding(top = 6.dp))
                    Spacer(Modifier.height(16.dp))
                    when {
                        t.isSoon -> PillButton(if (t.releaseDate.isNotBlank()) "Próximamente · ${t.releaseDate}" else "Próximamente", {}, Modifier.fillMaxWidth(), icon = null, dark = true)
                        target != null -> PillButton(target.label, { nav.play(t, target.url, target.season, target.episode) }, Modifier.fillMaxWidth())
                        else -> PillButton("No disponible por el momento", {}, Modifier.fillMaxWidth(), icon = null, dark = true)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        ListToggle(t, nav)
                        if (mediaFor(t.trailer) != null) GlassButton(Ic.clapper, "Ver tráiler", { nav.go(Screen.Player(t.id, t.trailer, null, null, trailer = true)) }, size = 46.dp)
                    }
                    if (t.description.isNotBlank()) {
                        Text(
                            t.description, style = ts(14, FontWeight.Light, Color.White.copy(alpha = .88f)).copy(lineHeight = 20.sp),
                            maxLines = if (more) Int.MAX_VALUE else 4, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 16.dp).fillMaxWidth().animateContentSize().tap({ more = !more }, .99f)
                        )
                        if (!more && t.description.length > 190) Text("Ver más", style = ts(12.5f, FontWeight.Bold, C.accent), modifier = Modifier.padding(top = 4.dp).tap({ more = true }))
                    }
                }
            }
            // temporadas y capítulos
            if (t.isSerie && t.seasons.isNotEmpty()) {
                val s = t.seasons.getOrNull(season) ?: t.seasons.first()
                item(key = "seasons") {
                    Column(Modifier.padding(top = 26.dp)) {
                        if (t.seasons.size > 1) LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(t.seasons) { i, ss -> Chip(ss.name.ifBlank { "Temporada ${ss.number}" }, i == season) { season = i } }
                        } else Text(s.name.ifBlank { "Temporada ${s.number}" }, style = ts(16, FontWeight.Bold, Color.White), modifier = Modifier.padding(horizontal = 20.dp))
                    }
                }
                itemsIndexed(s.episodes, key = { i, e -> "ep-${s.number}-${e.number}-$i" }) { _, e ->
                    EpisodeRow(t, s, e, prog) { if (!t.isSoon && isHttp(e.url)) nav.play(t, e.url, s.number, e.number) }
                }
                if (s.extras.isNotEmpty()) item(key = "extras-${s.number}") { ExtrasShowcase(t, s, nav) }
            }
            if (related.isNotEmpty()) item(key = "rel") {
                Column(Modifier.padding(top = 28.dp)) {
                    Text("También te podría gustar", style = ts(15, FontWeight.Bold, Color.White.copy(alpha = .94f)), modifier = Modifier.padding(start = 20.dp, bottom = 10.dp))
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                        items(related, key = { it.id }) { o ->
                            PosterCard(o, Modifier.width(108.dp).aspectRatio(2f / 3f).tap({ nav.go(Screen.Detail(o.id)) }).clip(RoundedCornerShape(9.dp)))
                        }
                    }
                }
            }
            if (t.cast.isNotEmpty()) item(key = "cast") {
                Column(Modifier.padding(top = 26.dp)) {
                    Text("Reparto", style = ts(15, FontWeight.Bold, Color.White.copy(alpha = .94f)), modifier = Modifier.padding(start = 20.dp, bottom = 10.dp))
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(t.cast) { c -> CastCard(c) { nav.go(Screen.Person(c.name)) } }
                    }
                }
            }
        }
        BackFab(nav)
    }
}

@Composable
private fun BackFab(nav: Nav) {
    Box(Modifier.statusBarsPadding().padding(start = 12.dp, top = 8.dp)) { GlassButton(Ic.back, "Volver", { nav.back() }, size = 40.dp) }
}

/* ---------------- capítulo ---------------- */

@Composable
private fun EpisodeRow(t: Title, s: Season, e: Episode, prog: Progress?, onClick: () -> Unit) {
    val seen = prog?.watched?.let { "${s.number}-${e.number}" in it || "${s.number}:${e.number}" in it } == true
    val hasVideo = isHttp(e.url)
    val pos = prog?.positions?.get(posKey(s.number, e.number))
    val frac = when {
        pos != null && pos.second > 0 -> (pos.first.toFloat() / pos.second).coerceIn(0f, 1f)
        seen -> 1f
        else -> 0f
    }
    // Igual que en las plataformas de streaming: miniatura grande con el título al lado y la sinopsis abajo, a todo el ancho
    Column(Modifier.fillMaxWidth().tap(onClick, .98f).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.fillMaxWidth(.5f).widthIn(max = 300.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(C.surface)) {
                val img = e.thumb.ifBlank { t.wide }
                if (img.isNotBlank()) Img(img, Modifier.fillMaxSize()) else Placeholder(t, Modifier.fillMaxSize())
                if (!hasVideo) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .45f)), contentAlignment = Alignment.Center) { Text("Sin video", style = ts(12, FontWeight.Bold, Color.White.copy(alpha = .85f))) }
                else Box(
                    Modifier.align(Alignment.Center).size(42.dp).background(Color.Black.copy(alpha = .42f), CircleShape).border(1.5.dp, Color.White.copy(alpha = .9f), CircleShape),
                    contentAlignment = Alignment.Center
                ) { Icon(Ic.play, null, tint = Color.White, modifier = Modifier.size(17.dp)) }
                if (frac > 0f) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color.White.copy(alpha = .28f))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(frac).background(C.accent))
                }
            }
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text("${e.number}. ${e.title.ifBlank { "Episodio ${e.number}" }}", style = ts(15.5f, FontWeight.Bold, Color.White), maxLines = 3, overflow = TextOverflow.Ellipsis)
                val meta = listOfNotNull(e.duration.ifBlank { null }, if (seen && (pos == null || frac >= 1f)) "Visto" else null).joinToString(" · ")
                if (meta.isNotBlank()) Text(meta, style = ts(12.5f, color = Color.White.copy(alpha = .6f)), modifier = Modifier.padding(top = 4.dp))
            }
        }
        if (e.description.isNotBlank()) Text(
            e.description, style = ts(13.5f, color = Color.White.copy(alpha = .7f)).copy(lineHeight = 19.sp),
            maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp)
        )
    }
}

/* ---------------- extras de la temporada: vitrina (igual que en la web y la TV) ---------------- */

@Composable
private fun ExtrasShowcase(t: Title, s: Season, nav: Nav) {
    val xs = s.extras
    val f = xs.first()
    val tint = rememberTint(f.thumb)
    val mins = xs.sumOf { it.duration.filter { c -> c.isDigit() }.take(3).toIntOrNull() ?: 0 }
    val shape = RoundedCornerShape(18.dp)
    Box(
        Modifier.padding(start = 16.dp, end = 16.dp, top = 26.dp).fillMaxWidth().clip(shape)
            .background(Color(0xF20E080B)).border(1.dp, Color.White.copy(alpha = .10f), shape)
    ) {
        Box(Modifier.matchParentSize().background(Brush.radialGradient(listOf(tint.copy(alpha = .38f), Color.Transparent), center = androidx.compose.ui.geometry.Offset(900f, 0f), radius = 900f)))
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 16.dp)) {
            Text("★  CONTENIDO EXTRA", style = ts(10, FontWeight.Bold, C.accent).copy(letterSpacing = 2.sp))
            Text("Más de la temporada ${s.number}", style = ts(21, FontWeight.Bold, Color.White), modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).tap({ nav.playExtra(t, f.url) }, .98f).clip(RoundedCornerShape(13.dp)).background(C.surface)) {
                if (f.thumb.isNotBlank()) Img(f.thumb, Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, .4f to Color.Black.copy(alpha = .2f), 1f to Color.Black.copy(alpha = .9f))))
                Column(Modifier.align(Alignment.BottomStart).padding(start = 14.dp, end = 14.dp, bottom = 12.dp)) {
                    XPill()
                    Text(f.title.ifBlank { "Extra" }, style = ts(17, FontWeight.Bold, Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                    if (f.description.isNotBlank()) Text(f.description, style = ts(11, color = Color.White.copy(alpha = .82f)), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                    Row(Modifier.padding(top = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.background(Color.White, RoundedCornerShape(99.dp)).padding(horizontal = 13.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Ic.play, null, tint = Color.Black, modifier = Modifier.size(11.dp)); Spacer(Modifier.width(6.dp))
                            Text("Ver extra", style = ts(12, FontWeight.Bold, Color.Black))
                        }
                        if (f.duration.isNotBlank()) Text(f.duration, style = ts(11, color = Color.White.copy(alpha = .75f)), modifier = Modifier.padding(start = 10.dp))
                    }
                }
            }
            if (xs.size > 1) {
                Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
                    Text("Todos los extras", style = ts(14, FontWeight.Bold, Color.White), modifier = Modifier.weight(1f))
                    Text("${xs.size} videos" + (if (mins > 0) " · $mins min" else ""), style = ts(10.5f, color = Color.White.copy(alpha = .55f)))
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = .1f)))
                xs.forEach { x ->
                    Row(Modifier.fillMaxWidth().tap({ nav.playExtra(t, x.url) }, .98f).padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(96.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(7.dp)).background(C.surface)) {
                            if (x.thumb.isNotBlank()) Img(x.thumb, Modifier.fillMaxSize())
                            if (x.duration.isNotBlank()) Text(x.duration, style = ts(8.5f, FontWeight.Bold, Color.White),
                                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).background(Color.Black.copy(alpha = .78f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 1.dp))
                        }
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(x.title.ifBlank { "Extra" }, style = ts(13, FontWeight.Bold, Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (x.description.isNotBlank()) Text(x.description, style = ts(10.5f, color = Color.White.copy(alpha = .55f)), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun XPill() {
    Text(
        "★ EXTRA", style = ts(9, FontWeight.Bold, Color(0xFF3B0E22)).copy(letterSpacing = 1.6.sp),
        modifier = Modifier.background(C.accent, RoundedCornerShape(99.dp)).padding(horizontal = 9.dp, vertical = 4.dp)
    )
}

@Composable
private fun CastCard(c: CastMember, onClick: () -> Unit) {
    Column(Modifier.width(78.dp).tap(onClick, .94f), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(Color.White.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
            if (c.photo.isNotBlank()) Img(c.photo, Modifier.fillMaxSize())
            else Text(c.name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }, style = ts(22, FontWeight.Bold, Color.White.copy(alpha = .75f)))
        }
        Spacer(Modifier.height(6.dp))
        Text(c.name, style = ts(10.5f, color = Color.White.copy(alpha = .88f)), maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        if (c.role.isNotBlank()) Text(c.role, style = ts(9, color = Color.White.copy(alpha = .55f)), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}
