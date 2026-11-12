' Ficha del título: portada con Ver / de nuevo / me gusta / tráiler; al bajar, temporadas, capítulos, extras, parecidos y reparto.

sub init()
    m.hero = m.top.findNode("hero")
    m.rows = m.top.findNode("rows")
    m.chipsG = m.top.findNode("chips")
    m.btnAll = [m.top.findNode("b0"), m.top.findNode("b1"), m.top.findNode("b2"), m.top.findNode("b3")]
    m.top.findNode("ttl").font = Fnt("bold", 80)
    m.top.findNode("kind").font = Fnt("book", 30)
    m.top.findNode("desc").font = Fnt("light", 30)
    m.top.findNode("facts").font = Fnt("book", 28)
    m.top.findNode("cast").font = Fnt("book", 22)
    m.rows.rowLabelFont = Fnt("bold", 30)
    m.rows.observeField("rowItemSelected", "onSelect")
    m.global.observeField("user", "onUser")
    m.top.observeField("focusedChild", "paintBtn")
    m.mode = "hero"
    m.area = "rows"
    m.bi = 0
    m.si = 0
    m.ci = 0
    m.hasBody = false
    m.dirty = false
end sub

sub onUser()
    m.dirty = true
    if m.mode = "hero" then paintHero()
end sub

sub build()
    a = m.top.args
    cat = m.global.cat
    if a = invalid or cat = invalid then return
    m.t = cat.byId[a.id]
    if m.t = invalid then return
    t = m.t
    SetImg(m.top.findNode("bg"), TvBg(t), 1920, 1080)
    if t.logo <> "" then
        m.top.findNode("logo").uri = t.logo
        m.top.findNode("logo").visible = true
        m.top.findNode("ttl").visible = false
        m.top.findNode("logoTop").uri = t.logo
    else
        m.top.findNode("logo").visible = false
        m.top.findNode("ttl").visible = true
        m.top.findNode("ttl").text = t.title
    end if
    k = "Película"
    if t.isSerie then k = "Serie"
    ln = k
    ms = Meta(t)
    if ms <> "" then ln = ln + "  •  " + ms
    if t.rating <> "" then ln = ln + "  •  " + t.rating
    fm = []
    if t.uhd4k then fm.push("4K")
    if t.hdr then fm.push("HDR")
    if t.atmos then fm.push("Dolby Atmos")
    if t.s51 then fm.push("5.1")
    if t.hd and not t.uhd4k then fm.push("HD")
    if fm.count() > 0 then ln = ln + "  •  " + Join(fm, " · ")
    m.top.findNode("kind").text = ln
    m.top.findNode("desc").text = t.description
    facts = []
    if t.year <> "" then
        if t.yearEnd <> "" then
            facts.push(t.year + " – " + t.yearEnd)
        else
            facts.push(t.year)
        end if
    end if
    if t.isSerie and t.seasons.count() > 0 then
        facts.push(Meta({ year: "", yearEnd: "", isSerie: true, seasons: t.seasons, duration: "", genres: [] }))
    else if t.duration <> "" then
        facts.push(t.duration)
    end if
    m.top.findNode("facts").text = Join(facts, " · ")
    if t.cast.count() > 0 then
        nm = []
        for i = 0 to t.cast.count() - 1
            if i < 4 then nm.push(t.cast[i].name)
        end for
        m.top.findNode("cast").text = "Reparto  " + Join(nm, ", ")
    end if
    buildBody()
    paintHero()
    runTrack()
end sub

sub runTrack()
    tk = CreateObject("roSGNode", "DataTask")
    tk.op = "view"
    tk.args = { id: m.t.id }
    m.tk = tk
    tk.control = "RUN"
end sub

sub paintHero()
    t = m.t
    if t = invalid then return
    user = m.global.user
    tg = PlayTarget(t, user)
    m.tg = tg
    b = m.btnAll
    ids = []
    if t.isSoon then
        pt = "Próximamente"
        if t.releaseDate <> "" then pt = pt + " · " + t.releaseDate
        b[0].icon = ""
        b[0].text = pt
        m.canPlay = false
    else if tg <> invalid then
        b[0].icon = "play"
        b[0].text = tg.label
        m.canPlay = true
    else
        b[0].icon = ""
        b[0].text = "No disponible por el momento"
        m.canPlay = false
    end if
    ids.push(0)
    showRestart = (not t.isSoon) and CanRestart(t, tg, user)
    b[1].visible = showRestart
    if showRestart then ids.push(1)
    on1 = false
    for each id in user.myList
        if id = t.id then on1 = true
    end for
    b[2].on = on1
    ids.push(2)
    hasTr = Playable(t.trailer)
    b[3].visible = hasTr
    if hasTr then ids.push(3)
    x = 96
    for each i in ids
        b[i].translation = [x, 820]
        x = x + b[i].w + 16
    end for
    m.ids = ids
    if m.bi >= ids.count() then m.bi = 0
    paintBtn()
end sub

sub paintBtn()
    if m.ids = invalid then return
    on1 = (m.mode = "hero" and m.top.isInFocusChain())
    for k = 0 to 3
        m.btnAll[k].focused = false
    end for
    if on1 then m.btnAll[m.ids[m.bi]].focused = true
end sub

' ---------------- parte de abajo ----------------
sub buildBody()
    t = m.t
    cat = m.global.cat
    specs = []
    m.sel = []
    user = m.global.user
    if t.isSerie and t.seasons.count() > 0 then
        if m.si >= t.seasons.count() then m.si = 0
        s = t.seasons[m.si]
        nm = s.name
        if nm = "" then nm = "Temporada " + s.number.toStr()
        items = []
        sl = []
        p = user.progress[t.id]
        for each e in s.episodes
            ttl = e.title
            if ttl = "" then ttl = "Capítulo " + e.number.toStr()
            ttl = e.number.toStr() + ". " + ttl
            sb = e.duration
            if e.soon then
                sb = "Próximamente"
            else if e.description <> "" then
                if sb <> "" then sb = sb + " · "
                sb = sb + e.description
            end if
            pr = 0
            if p <> invalid then
                wk = s.number.toStr() + "-" + e.number.toStr()
                for each w in p.watched
                    if w = wk then pr = 100
                end for
                ps = p.positions[wk]
                if ps <> invalid and ps.d > 0 then pr = Int(ps.t * 100 / ps.d)
            end if
            items.push({ t: { title: ttl, poster: e.thumb, thumbWide: e.thumb, backdrop: "", bannerImage: "", id: t.id }, sub: sb, prog: pr })
            if e.ready then
                sl.push({ act: "play", url: e.url, season: s.number, episode: e.number })
            else
                sl.push({ act: "none" })
            end if
        end for
        if items.count() > 0 then
            specs.push({ title: nm, kind: "ep", items: items })
            m.sel.push(sl)
        end if
        if s.extras.count() > 0 then
            items = []
            sl = []
            for i = 0 to s.extras.count() - 1
                e = s.extras[i]
                ttl = e.title
                if ttl = "" then ttl = "Extra " + (i + 1).toStr()
                items.push({ t: { title: ttl, poster: e.thumb, thumbWide: e.thumb, backdrop: "", bannerImage: "", id: t.id }, sub: e.duration, prog: 0 })
                sl.push({ act: "play", url: e.url, season: s.number, episode: 1000 + i })
            end for
            specs.push({ title: "Extras", kind: "ep", items: items })
            m.sel.push(sl)
        end if
    end if
    rel = []
    for each o in PubTitles(cat)
        if o.id <> t.id then
            n = 0
            for each g in o.genres
                for each g2 in t.genres
                    if g = g2 then n = n + 1
                end for
            end for
            if n > 0 then rel.push({ t: o, score: n })
        end if
    end for
    if rel.count() > 1 then rel.sortBy("score", "r")
    items = []
    sl = []
    for each r in rel
        if items.count() < 14 then
            items.push({ t: r.t })
            sl.push({ act: "detail", id: r.t.id })
        end if
    end for
    if items.count() > 0 then
        specs.push({ title: "También te podría gustar", kind: "small", items: items })
        m.sel.push(sl)
    end if
    if t.cast.count() > 0 then
        items = []
        sl = []
        for each c in t.cast
            items.push({ t: { title: c.name, poster: c.photo, thumbWide: c.photo, backdrop: "", bannerImage: "", id: t.id }, sub: c.role })
            sl.push({ act: "person", name: c.name })
        end for
        specs.push({ title: "Reparto", kind: "cast", items: items })
        m.sel.push(sl)
    end if
    m.hasBody = specs.count() > 0
    m.top.findNode("body").visible = m.hasBody
    if m.hasBody then FillRowList(m.rows, specs, 96)
    buildChips()
end sub

sub buildChips()
    t = m.t
    m.chipsG.removeChildrenIndex(m.chipsG.getChildCount(), 0)
    m.chips = []
    if not t.isSerie or t.seasons.count() < 2 then
        m.rows.translation = [0, 120]
        return
    end if
    m.rows.translation = [0, 190]
    x = 96
    for i = 0 to t.seasons.count() - 1
        s = t.seasons[i]
        b = CreateObject("roSGNode", "RvButton")
        b.h = 64
        b.text = s.name
        if s.name = "" then b.text = "Temporada " + s.number.toStr()
        b.look = "glass"
        b.translation = [x, 0]
        m.chipsG.appendChild(b)
        m.chips.push(b)
        x = x + b.w + 12
    end for
    paintChips()
end sub

sub paintChips()
    if m.chips = invalid then return
    for i = 0 to m.chips.count() - 1
        if i = m.si then
            m.chips[i].look = "primary"
        else
            m.chips[i].look = "glass"
        end if
        m.chips[i].focused = (m.mode = "body" and m.area = "chips" and i = m.ci)
    end for
    ' que el botón enfocado siempre se vea entero
    if m.chips.count() > 0 then
        b = m.chips[m.ci]
        right = b.translation[0] + b.w
        off = 0
        if right > 1824 then off = 1824 - right
        m.chipsG.translation = [off, 96]
    end if
end sub

sub setMode(md as String)
    if md = m.mode then return
    if md = "body" and not m.hasBody then return
    m.mode = md
    rev = (md = "hero")
    m.top.findNode("iBody").reverse = rev
    m.top.findNode("iHero").reverse = rev
    m.top.findNode("iDim").reverse = rev
    m.top.findNode("aMode").control = "start"
    if md = "body" then
        if m.chips <> invalid and m.chips.count() > 0 then
            m.area = "chips"
            m.ci = m.si
            m.top.setFocus(true)
        else
            m.area = "rows"
            m.rows.setFocus(true)
        end if
    else
        m.top.setFocus(true)
    end if
    paintChips()
    paintBtn()
end sub

sub takeFocus()
    if m.dirty and m.t <> invalid then
        m.dirty = false
        buildBody()
        paintHero()
    end if
    if m.mode = "body" and m.area = "rows" then
        m.rows.setFocus(true)
    else
        m.top.setFocus(true)
    end if
    paintChips()
    paintBtn()
end sub

sub onSelect()
    p = m.rows.rowItemSelected
    if p[0] >= m.sel.count() then return
    if p[1] >= m.sel[p[0]].count() then return
    s = m.sel[p[0]][p[1]]
    if s.act = "play" then
        m.top.nav = { action: "push", kind: "player", args: { id: m.t.id, url: s.url, season: s.season, episode: s.episode, restart: false } }
    else if s.act = "detail" then
        m.top.nav = { action: "push", kind: "detail", args: { id: s.id } }
    else if s.act = "person" then
        m.top.nav = { action: "push", kind: "person", args: { heading: s.name } }
    end if
end sub

sub actHero()
    t = m.t
    id = m.ids[m.bi]
    if id = 0 then
        if m.canPlay and m.tg <> invalid then m.top.nav = { action: "push", kind: "player", args: { id: t.id, url: m.tg.url, season: m.tg.season, episode: m.tg.episode, restart: false } }
    else if id = 1 then
        m.top.nav = { action: "push", kind: "player", args: { id: t.id, url: m.tg.url, season: m.tg.season, episode: m.tg.episode, restart: true } }
    else if id = 2 then
        if not m.global.user.signed then
            m.top.nav = { action: "push", kind: "login", args: {} }
        else
            tk = CreateObject("roSGNode", "DataTask")
            tk.op = "toggle"
            tk.args = { id: t.id }
            m.tk2 = tk
            tk.control = "RUN"
        end if
    else
        m.top.nav = { action: "push", kind: "player", args: { id: t.id, url: t.trailer, trailer: true } }
    end if
end sub

function onKeyEvent(key as String, press as Boolean) as Boolean
    if not press then return false
    if m.mode = "hero" then
        if key = "right" then
            if m.bi < m.ids.count() - 1 then m.bi = m.bi + 1
            paintBtn()
            return true
        else if key = "left" then
            if m.bi > 0 then m.bi = m.bi - 1
            paintBtn()
            return true
        else if key = "OK" then
            actHero()
            return true
        else if key = "down" then
            setMode("body")
            return true
        end if
        return false
    end if
    ' parte de abajo
    if m.area = "chips" then
        if key = "right" then
            if m.ci < m.chips.count() - 1 then m.ci = m.ci + 1
            paintChips()
            return true
        else if key = "left" then
            if m.ci > 0 then m.ci = m.ci - 1
            paintChips()
            return true
        else if key = "OK" then
            m.si = m.ci
            buildBody()
            paintChips()
            return true
        else if key = "down" then
            if m.rows.content <> invalid and m.rows.content.getChildCount() > 0 then
                m.area = "rows"
                m.rows.setFocus(true)
                paintChips()
            end if
            return true
        else if key = "up" or key = "back" then
            setMode("hero")
            return true
        end if
        return false
    end if
    if key = "up" then
        r = m.rows.rowItemFocused
        if r <> invalid and r[0] = 0 then
            if m.chips <> invalid and m.chips.count() > 0 then
                m.area = "chips"
                m.ci = m.si
                m.top.setFocus(true)
                paintChips()
            else
                setMode("hero")
            end if
            return true
        end if
    else if key = "back" then
        setMode("hero")
        return true
    end if
    return false
end function
