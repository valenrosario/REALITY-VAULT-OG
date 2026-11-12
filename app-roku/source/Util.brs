' Utilidades compartidas: lectura segura de JSON, modelos y reglas de "seguir viendo" (copia de Data.kt de la app de la tele).

function AsStr(v as Dynamic) as String
    if v = invalid then return ""
    t = type(v)
    if t = "roString" or t = "String" then return v
    if t = "roInt" or t = "Integer" or t = "roLongInteger" or t = "LongInteger" then return v.toStr()
    if t = "roFloat" or t = "Float" or t = "roDouble" or t = "Double" then return Str(v).trim()
    if t = "roBoolean" or t = "Boolean" then
        if v then return "true"
        return "false"
    end if
    return ""
end function

function Num(v as Dynamic, d = 0 as Integer) as Integer
    t = AsStr(v).trim()
    if t = "" then return d
    r = CreateObject("roRegex", "^-?\d+(\.\d+)?$", "")
    if not r.isMatch(t) then return d
    return Int(Val(t))
end function

function NumOr0(v as Dynamic) as Dynamic
    t = type(v)
    if t = "roInt" or t = "Integer" or t = "roLongInteger" or t = "LongInteger" or t = "roFloat" or t = "Float" or t = "roDouble" or t = "Double" then return v
    return 0
end function

function Bo(v as Dynamic) as Boolean
    if type(v) = "roBoolean" or type(v) = "Boolean" then return v
    return AsStr(v) = "true"
end function

function Lst(v as Dynamic) as Object
    if type(v) = "roArray" then return v
    return []
end function

function StrList(v as Dynamic) as Object
    out = []
    for each x in Lst(v)
        s1 = AsStr(x).trim()
        if s1 <> "" then out.push(s1)
    end for
    return out
end function

function IsHttp(u as String) as Boolean
    l = LCase(u)
    return Left(l, 7) = "http://" or Left(l, 8) = "https://"
end function

' Roku solo reproduce archivos directos (mp4 / mkv / ts / m3u8); los enlaces de YouTube, Vimeo, Drive… no se pueden.
function Playable(u as String) as Boolean
    if not IsHttp(u) then return false
    l = LCase(u)
    for each h in ["youtube.com", "youtu.be", "youtube-nocookie.com", "vimeo.com", "drive.google.com", "dailymotion.com", "dai.ly", "ok.ru", "archive.org/details", "archive.org/embed"]
        if Instr(1, l, h) > 0 then return false
    end for
    return true
end function

function StreamFormat(u as String) as String
    p = LCase(u.split("?")[0])
    if Right(p, 5) = ".m3u8" then return "hls"
    if Right(p, 4) = ".mkv" or Right(p, 5) = ".webm" then return "mkv"
    if Right(p, 3) = ".ts" or Right(p, 5) = ".m2ts" or Right(p, 4) = ".mts" then return "ts"
    return "mp4"
end function

' "1:30" = 1 min 30 s · "1:02:03" · "2" = minuto 2. Devuelve segundos o -1.
function ParseSkipTime(v as String) as Integer
    t = v.trim().replace(",", ".")
    if t = "" then return -1
    r1 = CreateObject("roRegex", "^\d+(\.\d+)?$", "")
    if r1.isMatch(t) then return Int(Val(t) * 60 + 0.5)
    r2 = CreateObject("roRegex", "^\d{1,2}(:\d{1,2}){1,2}$", "")
    if not r2.isMatch(t) then return -1
    parts = t.split(":")
    total = 0
    for i = 0 to parts.count() - 1
        n = Int(Val(parts[i]))
        if i > 0 and n > 59 then return -1
        total = total * 60 + n
    end for
    return total
end function

function ParseSkips(o as Object) as Object
    out = []
    for each k in Lst(o.skips)
        if type(k) = "roAssociativeArray" then
            f = ParseSkipTime(AsStr(k["from"]))
            if f < 0 then f = 0
            e = ParseSkipTime(AsStr(k["to"]))
            if e > f then
                lb = AsStr(k.label).trim()
                if lb = "" then lb = "Saltear intro"
                out.push({ label: lb, fromMs: f * 1000, toMs: e * 1000 })
            end if
        end if
    end for
    if out.count() > 1 then out.sortBy("fromMs")
    return out
end function

function PickUrl(o as Object, use4k as Boolean) as String
    tv = AsStr(o.urlTv).trim()
    if use4k and IsHttp(tv) then return tv
    return AsStr(o.url).trim()
end function

function ParseEp(eo as Object, idx as Integer, use4k as Boolean) as Object
    url = PickUrl(eo, use4k)
    soon = AsStr(eo.availability) = "proximamente"
    return { number: Num(eo.number, idx + 1), title: AsStr(eo.title), description: AsStr(eo.description), duration: AsStr(eo.duration), url: url, thumb: AsStr(eo.thumb), subs: AsStr(eo.subs).trim(), skips: ParseSkips(eo), soon: soon, ready: (not soon) and Playable(url) }
end function

function ParseTitle(o as Object, use4k as Boolean) as Object
    seasons = []
    for each so in Lst(o.seasons)
        if type(so) = "roAssociativeArray" then
            eps = []
            j = 0
            for each eo in Lst(so.episodes)
                if type(eo) = "roAssociativeArray" then eps.push(ParseEp(eo, j, use4k))
                j = j + 1
            end for
            xs = []
            j = 0
            for each xo in Lst(so.extras)
                if type(xo) = "roAssociativeArray" then
                    x = ParseEp(xo, j, use4k)
                    x.soon = false
                    x.ready = Playable(x.url)
                    if x.ready then xs.push(x)
                end if
                j = j + 1
            end for
            nm = AsStr(so.name)
            if nm = "" then nm = AsStr(so.title)
            seasons.push({ number: Num(so.number, seasons.count() + 1), name: nm, episodes: eps, extras: xs, soon: AsStr(so.availability) = "proximamente" })
        end if
    end for
    if seasons.count() > 1 then seasons.sortBy("number")
    cast = []
    for each c in Lst(o.cast)
        if type(c) = "roAssociativeArray" and AsStr(c.name).trim() <> "" then cast.push({ name: AsStr(c.name), role: AsStr(c.role), photo: AsStr(c.photo) })
    end for
    fm = {}
    if type(o.formats) = "roAssociativeArray" then fm = o.formats
    return {
        id: AsStr(o.id), tp: AsStr(o["type"]), title: AsStr(o.title), tagline: AsStr(o.tagline), description: AsStr(o.description),
        year: AsStr(o.year), yearEnd: AsStr(o.yearEnd), rating: AsStr(o.rating), duration: AsStr(o.duration), genres: StrList(o.genres),
        poster: AsStr(o.poster), backdrop: AsStr(o.backdrop), logo: AsStr(o.logo), thumbWide: AsStr(o.thumbWide), bannerImage: AsStr(o.bannerImage),
        url: PickUrl(o, use4k), trailer: AsStr(o.trailer).trim(), featured: Bo(o.featured), status: AsStr(o.status),
        availability: AsStr(o.availability), releaseDate: AsStr(o.releaseDate), createdAt: NumOr0(o.createdAt),
        seasons: seasons, cast: cast, bannerTv: AsStr(o.bannerImageTv), backdropTv: AsStr(o.backdropTv), subs: AsStr(o.subs).trim(),
        skips: ParseSkips(o), hd: Bo(fm.hd), uhd4k: Bo(fm.uhd4k), hdr: Bo(fm.hdr), atmos: Bo(fm.atmos), s51: Bo(fm.surround51),
        isSerie: AsStr(o["type"]) = "serie", isSoon: AsStr(o.availability) = "proximamente"
    }
end function

' Imágenes (igual que en la tele)
function TvBg(t as Object) as String
    if t.backdropTv <> "" then return t.backdropTv
    return Bg(t)
end function
function Bg(t as Object) as String
    for each u in [t.backdrop, t.bannerImage, t.thumbWide, t.poster]
        if u <> "" then return u
    end for
    return ""
end function
function WideImg(t as Object) as String
    for each u in [t.thumbWide, t.backdrop, t.bannerImage, t.poster]
        if u <> "" then return u
    end for
    return ""
end function
function TvHero(t as Object) as String
    for each u in [t.bannerTv, t.backdropTv, t.backdrop, t.bannerImage, t.thumbWide, t.poster]
        if u <> "" then return u
    end for
    return ""
end function

function Meta(t as Object) as String
    p = []
    if t.year <> "" then
        if t.yearEnd <> "" then
            p.push(t.year + "–" + t.yearEnd)
        else
            p.push(t.year)
        end if
    end if
    if t.isSerie then
        n = t.seasons.count()
        if n > 0 then
            if n = 1 then
                p.push("1 temporada")
            else
                p.push(n.toStr() + " temporadas")
            end if
        end if
    else if t.duration <> "" then
        p.push(t.duration)
    end if
    if t.genres.count() > 0 then
        g = t.genres[0]
        if t.genres.count() > 1 then g = g + ", " + t.genres[1]
        p.push(g)
    end if
    out = ""
    for each x in p
        if out <> "" then out = out + "  •  "
        out = out + x
    end for
    return out
end function

function Join(a as Object, sep as String) as String
    out = ""
    for each x in a
        if out <> "" then out = out + sep
        out = out + x
    end for
    return out
end function

' ---------- progreso ("seguir viendo") ----------
function PosKey(season as Dynamic, episode as Dynamic) as String
    if season = invalid or episode = invalid then return "m"
    return AsStr(season) + "-" + AsStr(episode)
end function
function PosDone(t as Integer, d as Integer) as Boolean
    return d > 0 and (d - t <= 45 or t / d >= 0.95)
end function
function PosResumable(p as Dynamic) as Boolean
    if p = invalid then return false
    return p.d > 0 and p.t >= 10 and not PosDone(p.t, p.d)
end function

function FlatPlayable(t as Object) as Object
    out = []
    for each s in t.seasons
        if not s.soon then
            for each e in s.episodes
                if e.ready then out.push({ s: s.number, e: e })
            end for
        end if
    end for
    return out
end function

' Lo que se reproduce al tocar "Ver" (mismo criterio que el sitio y la tele). Devuelve invalid si no hay nada para ver.
function PlayTarget(t as Object, user as Object) as Dynamic
    if t.isSoon then return invalid
    p = user.progress[t.id]
    if not t.isSerie then
        if not Playable(t.url) then return invalid
        res = false
        lb = "Ver"
        if p <> invalid then lb = "Volver a ver"
        if p <> invalid and p.positions <> invalid then
            if PosResumable(p.positions["m"]) then
                res = true
                lb = "Continuar"
            end if
        end if
        return { url: t.url, season: invalid, episode: invalid, label: lb, cont: res }
    end if
    eps = FlatPlayable(t)
    if eps.count() = 0 then return invalid
    i = 0
    cont = false
    if p <> invalid and p.season <> invalid then
        j = -1
        for k = 0 to eps.count() - 1
            if eps[k].s = p.season and eps[k].e.number = p.episode then j = k
        end for
        if j >= 0 then
            wk = AsStr(p.season) + "-" + AsStr(p.episode)
            seen = false
            for each w in p.watched
                if w = wk then seen = true
            end for
            res = false
            if p.positions <> invalid then res = PosResumable(p.positions[PosKey(p.season, p.episode)])
            if res or not seen or j + 1 >= eps.count() then
                i = j
            else
                i = j + 1
            end if
            cont = true
        end if
    end if
    x = eps[i]
    lb = "Ver"
    if cont then lb = "Continuar"
    return { url: x.e.url, season: x.s, episode: x.e.number, label: lb + " T" + x.s.toStr() + ":E" + x.e.number.toStr(), cont: cont }
end function

function CanRestart(t as Object, tg as Dynamic, user as Object) as Boolean
    if tg = invalid then return false
    p = user.progress[t.id]
    if p = invalid or p.positions = invalid then return false
    return PosResumable(p.positions[PosKey(tg.season, tg.episode)])
end function

function GetPos(user as Object, id as String, season as Dynamic, episode as Dynamic) as Dynamic
    p = user.progress[id]
    if p = invalid or p.positions = invalid then return invalid
    return p.positions[PosKey(season, episode)]
end function

function NextAfter(t as Object, season as Dynamic, episode as Dynamic) as Dynamic
    if season = invalid or episode = invalid then return invalid
    eps = FlatPlayable(t)
    for k = 0 to eps.count() - 2
        if eps[k].s = season and eps[k].e.number = episode then return eps[k + 1]
    end for
    return invalid
end function

' ---------- varios ----------
' Mezcla repetible (misma semilla = mismo orden) para que el inicio no salte mientras navegás.
function Shuffle(a as Object, seed as Integer) as Object
    out = []
    for each x in a
        out.push(x)
    end for
    st# = (Abs(seed) mod 2147483646) + 1
    for i = out.count() - 1 to 1 step -1
        st# = st# * 16807#
        st# = st# - Int(st# / 2147483647#) * 2147483647#
        n = i + 1
        j = Int(st# - Int(st# / n) * n)
        tmp = out[i]
        out[i] = out[j]
        out[j] = tmp
    end for
    return out
end function

' Las imágenes de TMDB vienen en tamaño "original" (enormes): se pide el tamaño justo para no frenar la tele.
function FitUrl(url as String, w as Dynamic) as String
    k = "image.tmdb.org/t/p/original/"
    if Instr(1, url, k) > 0 then
        sz = "w1280"
        if w <= 300 then
            sz = "w342"
        else if w <= 520 then
            sz = "w500"
        else if w <= 800 then
            sz = "w780"
        end if
        return url.replace(k, "image.tmdb.org/t/p/" + sz + "/")
    end if
    return url
end function

function SetImg(p as Object, url as String, w as Dynamic, h as Dynamic)
    p.loadWidth = w
    p.loadHeight = h
    p.loadDisplayMode = "scaleToZoom"
    p.uri = FitUrl(url, w)
end function

function Fnt(weight as String, size as Integer) as Object
    f = CreateObject("roSGNode", "Font")
    f.uri = "pkg:/fonts/gg_" + weight + ".ttf"
    f.size = size
    return f
end function

function Reg() as Object
    return CreateObject("roRegistrySection", "rv")
end function

' ---------- inicio ----------
function PubTitles(cat as Object) as Object
    out = []
    for each t in cat.titles
        if t.status <> "borrador" then out.push(t)
    end for
    return out
end function

function PubById(cat as Object, id as String) as Dynamic
    t = cat.byId[id]
    if t = invalid then return invalid
    if t.status = "borrador" then return invalid
    return t
end function

' Banners del inicio (si no hay, los títulos destacados), en el orden al azar fijo de esta sesión.
function Slides(cat as Object) as Object
    out = []
    for each b in cat.banners
        t = PubById(cat, b.titleId)
        if t <> invalid then
            img = ""
            for each u in [t.bannerTv, t.backdropTv, t.backdrop, b.image, TvHero(t)]
                if img = "" and u <> "" then img = u
            end for
            tx = b.text
            if tx = "" then tx = t.tagline
            if tx = "" then tx = t.description
            out.push({ t: t, image: img, headline: b.headline, text: tx })
        end if
    end for
    if out.count() > 0 then return out
    feats = []
    for each t in PubTitles(cat)
        if t.featured then feats.push(t)
    end for
    for each t in Shuffle(feats, cat.seed)
        if out.count() < 6 then
            tx = t.tagline
            if tx = "" then tx = t.description
            out.push({ t: t, image: TvHero(t), headline: "", text: tx })
        end if
    end for
    return out
end function

function MkItem(kind as String, t as Object, tag as String, prog as Integer, img as String, rank as String) as Object
    n = CreateObject("roSGNode", "ContentNode")
    if img = "" then
        if kind = "wide" or kind = "ep" then
            img = WideImg(t)
        else
            img = t.poster
            if img = "" then img = Bg(t)
        end if
    end if
    n.title = t.title
    n.addFields({ kind: kind, titleId: t.id, img: img, tag: tag, prog: prog, rank: rank, sub: "" })
    return n
end function

' Filas del inicio: seguir viendo, top 10, mi lista, destacados, novedades, series, películas, géneros, y las filas/colecciones del panel mezcladas.
function HomeRows(cat as Object, user as Object) as Object
    pub = PubTitles(cat)
    rows = []
    cont = []
    for each id in user.order
        t = PubById(cat, id)
        if t <> invalid then
            p = user.progress[id]
            tag = ""
            pr = 0
            if p <> invalid and p.season <> invalid then tag = "T" + AsStr(p.season) + ":E" + AsStr(p.episode)
            if p <> invalid and p.positions <> invalid then
                ps = p.positions[PosKey(p.season, p.episode)]
                if ps <> invalid and ps.d > 0 then pr = Int(ps.t * 100 / ps.d)
            end if
            cont.push({ t: t, tag: tag, prog: pr })
        end if
    end for
    if cont.count() > 0 then rows.push({ key: "cont", title: "Seguir viendo", kind: "wide", items: cont })
    top = []
    for each id in cat.top10
        t = PubById(cat, id)
        if t <> invalid and top.count() < 10 then top.push({ t: t, rank: (top.count() + 1).toStr() })
    end for
    if top.count() > 0 then rows.push({ key: "top", title: "Top 10 de la semana", kind: "top", items: top })
    ml = []
    for each id in user.myList
        t = PubById(cat, id)
        if t <> invalid then ml.push({ t: t })
    end for
    if ml.count() > 0 then rows.push({ key: "list", title: "Mi lista", kind: "wide", items: ml })
    rest = []
    feat = []
    for each t in pub
        if t.featured then feat.push({ t: t })
    end for
    if feat.count() > 0 then rest.push({ key: "feat", title: "Destacados", kind: "poster", items: feat })
    nw = []
    sorted = []
    for each t in pub
        sorted.push(t)
    end for
    sorted.sortBy("createdAt", "r")
    for each t in sorted
        if nw.count() < 20 then nw.push({ t: t })
    end for
    if nw.count() > 0 then rest.push({ key: "new", title: "Novedades", kind: "poster", items: nw })
    se = []
    mo = []
    for each t in pub
        if t.isSerie then
            se.push({ t: t })
        else
            mo.push({ t: t })
        end if
    end for
    if se.count() > 0 then rest.push({ key: "series", title: "Series", kind: "poster", items: se })
    if mo.count() > 0 then rest.push({ key: "movies", title: "Películas", kind: "poster", items: mo })
    ' géneros: los 8 con más títulos
    gk = []
    gm = {}
    for each t in pub
        for each g in t.genres
            if gm[g] = invalid then
                gm[g] = []
                gk.push(g)
            end if
            gm[g].push({ t: t })
        end for
    end for
    for i = 0 to gk.count() - 2
        for j = i + 1 to gk.count() - 1
            if gm[gk[j]].count() > gm[gk[i]].count() then
                tmp = gk[i]
                gk[i] = gk[j]
                gk[j] = tmp
            end if
        end for
    end for
    for i = 0 to gk.count() - 1
        if i < 8 then rest.push({ key: "g-" + gk[i], title: gk[i], kind: "poster", items: gm[gk[i]] })
    end for
    specials = []
    for each r in cat.rows
        its = []
        for each it in r.items
            t = PubById(cat, it.titleId)
            if t <> invalid then its.push({ t: t, img: it.image })
        end for
        if its.count() > 0 then specials.push({ key: "f-" + r.id, title: r.title, kind: "feat", items: its })
    end for
    for each c in cat.colls
        its = []
        for each id in c.items
            t = PubById(cat, id)
            if t <> invalid then its.push({ t: t })
        end for
        if its.count() > 0 then specials.push({ key: "c-" + c.id, title: c.name, kind: "poster", items: its, coll: c.id })
    end for
    ' las filas especiales se reparten entre las comunes, cada una después de una fila distinta (igual que en la tele y la web)
    if specials.count() > 0 and rest.count() > 0 then
        sp = Shuffle(specials, cat.seed + 7)
        idx = []
        for i = 0 to rest.count() - 1
            idx.push(i)
        end for
        idx = Shuffle(idx, cat.seed + 11)
        slots = []
        for i = 0 to sp.count() - 1
            if i < idx.count() then slots.push(idx[i])
        end for
        slots.sort()
        for i = 0 to rest.count() - 1
            rows.push(rest[i])
            for k = 0 to slots.count() - 1
                if slots[k] = i then rows.push(sp[k])
            end for
        end for
        for k = slots.count() to sp.count() - 1
            rows.push(sp[k])
        end for
    else
        for each r in rest
            rows.push(r)
        end for
        for each r in specials
            rows.push(r)
        end for
    end if
    return rows
end function

' Arma el contenido de un RowList (o la grilla) a partir de las filas y deja configuradas las medidas.
sub FillRowList(rl as Object, specs as Object, x0 as Integer)
    root = CreateObject("roSGNode", "ContentNode")
    hs = []
    sz = []
    sp = []
    lb = []
    fo = []
    sh = []
    for each r in specs
        row = root.createChild("ContentNode")
        row.title = r.title
        for each it in r.items
            tg = ""
            pr = 0
            rk = ""
            img = ""
            if it.tag <> invalid then tg = it.tag
            if it.prog <> invalid then pr = it.prog
            if it.rank <> invalid then rk = it.rank
            if it.img <> invalid then img = it.img
            node = MkItem(r.kind, it.t, tg, pr, img, rk)
            if it.sub <> invalid then node.sub = it.sub
            row.appendChild(node)
        end for
        if r.kind = "wide" then
            hs.push(216)
            sz.push([384, 216])
        else if r.kind = "top" then
            hs.push(384)
            sz.push([406, 384])
        else if r.kind = "feat" then
            hs.push(356)
            sz.push([240, 356])
        else if r.kind = "ep" then
            hs.push(330)
            sz.push([456, 330])
        else if r.kind = "cast" then
            hs.push(300)
            sz.push([190, 300])
        else if r.kind = "small" then
            hs.push(300)
            sz.push([200, 300])
        else
            hs.push(405)
            sz.push([270, 405])
        end if
        sp.push([24, 0])
        lb.push([x0, 14])
        fo.push(x0)
        sh.push(true)
    end for
    rl.rowHeights = hs
    rl.rowItemSize = sz
    rl.rowItemSpacing = sp
    rl.rowLabelOffset = lb
    rl.focusXOffset = fo
    rl.showRowLabel = sh
    rl.content = root
end sub
