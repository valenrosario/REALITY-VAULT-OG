' Inicio: banner grande arriba (con Ver / me gusta / siguiente) y las filas debajo; al bajar, el banner se va y quedan solo las filas.

sub init()
    m.hero = m.top.findNode("hero")
    m.rows = m.top.findNode("rows")
    m.heroImg = m.top.findNode("heroImg")
    m.logo = m.top.findNode("logo")
    m.headline = m.top.findNode("headline")
    m.kind = m.top.findNode("kind")
    m.text = m.top.findNode("text")
    m.btn = [m.top.findNode("bPlay"), m.top.findNode("bLike"), m.top.findNode("bNext")]
    m.dots = m.top.findNode("dots")
    m.empty = m.top.findNode("empty")
    m.headline.font = Fnt("bold", 76)
    m.kind.font = Fnt("book", 30)
    m.text.font = Fnt("light", 30)
    m.empty.font = Fnt("book", 34)
    m.rows.rowLabelFont = Fnt("bold", 30)
    m.rows.observeField("rowItemSelected", "onSelect")
    m.top.findNode("rot").observeField("fire", "onRot")
    m.global.observeField("cat", "onData")
    m.global.observeField("user", "onUser")
    m.top.observeField("focusedChild", "paintBtn")
    m.mode = "hero"
    m.bi = 0
    m.idx = 0
    m.slides = []
    m.specs = []
    m.built = false
    rebuild()
end sub

sub onData()
    m.idx = 0
    rebuild()
end sub

sub onUser()
    m.dirty = true
    if m.mode = "hero" then rebuild()
end sub

sub rebuild()
    cat = m.global.cat
    user = m.global.user
    m.dirty = false
    if cat = invalid or cat.titles = invalid then return
    m.slides = Slides(cat)
    m.specs = HomeRows(cat, user)
    if m.slides.count() = 0 and m.specs.count() = 0 then
        m.hero.visible = false
        m.rows.visible = false
        m.empty.visible = true
        if m.global.offline then
            m.empty.text = "No se pudo cargar el catálogo. Revisá tu conexión y apretá OK para reintentar."
        else
            m.empty.text = "Todavía no hay títulos publicados."
        end if
        m.mode = "empty"
        return
    end if
    m.empty.visible = false
    m.rows.visible = true
    m.hero.visible = m.slides.count() > 0
    FillRowList(m.rows, m.specs, 96)
    if m.slides.count() = 0 then
        m.mode = "rows"
        m.rows.translation = [0, 130]
    end if
    if m.idx >= m.slides.count() then m.idx = 0
    paintHero()
    m.built = true
end sub

sub paintHero()
    if m.slides.count() = 0 then return
    s = m.slides[m.idx]
    t = s.t
    user = m.global.user
    SetImg(m.heroImg, s.image, 1920, 1080)
    m.top.findNode("aFade").control = "start"
    if s.headline <> "" then
        m.logo.visible = false
        m.headline.visible = true
        m.headline.text = s.headline
    else if t.logo <> "" then
        m.headline.visible = false
        m.logo.visible = true
        m.logo.uri = t.logo
    else
        m.logo.visible = false
        m.headline.visible = true
        m.headline.text = t.title
    end if
    k = "Película"
    if t.isSerie then k = "Serie"
    ln = k
    ms = Meta(t)
    if ms <> "" then ln = ln + "  •  " + ms
    if t.rating <> "" then ln = ln + "  •  " + t.rating
    m.kind.text = ln
    m.text.text = s.text
    tg = PlayTarget(t, user)
    if t.isSoon then
        pt = "Próximamente"
        if t.releaseDate <> "" then pt = pt + " · " + t.releaseDate
        m.btn[0].icon = ""
        m.btn[0].text = pt
    else if tg <> invalid then
        m.btn[0].icon = "play"
        m.btn[0].text = tg.label
    else
        m.btn[0].icon = ""
        m.btn[0].text = "Ver detalles"
    end if
    m.btn[1].translation = [96 + m.btn[0].w + 20, 696]
    m.btn[2].translation = [96 + m.btn[0].w + 20 + 92, 696]
    on1 = false
    for each id in user.myList
        if id = t.id then on1 = true
    end for
    m.btn[1].on = on1
    m.btn[2].visible = m.slides.count() > 1
    m.dots.removeChildrenIndex(m.dots.getChildCount(), 0)
    x = 0
    for i = 0 to m.slides.count() - 1
        d = CreateObject("roSGNode", "Rectangle")
        w = 12
        d.color = "0xFFFFFF66"
        if i = m.idx then
            w = 36
            d.color = "0xF5A3C4FF"
        end if
        d.width = w
        d.height = 12
        d.translation = [x, 0]
        m.dots.appendChild(d)
        x = x + w + 10
    end for
    m.dots.translation = [1824 - x, 744]
    paintBtn()
end sub

sub paintBtn()
    for i = 0 to 2
        m.btn[i].focused = (m.mode = "hero" and m.top.isInFocusChain() and m.bi = i)
    end for
end sub

sub takeFocus()
    if m.dirty then rebuild()
    if m.mode = "rows" then
        m.rows.setFocus(true)
    else
        m.top.setFocus(true)
    end if
    m.top.findNode("rot").control = "start"
    paintBtn()
end sub

sub setMode(md as String)
    if md = m.mode then return
    m.mode = md
    if m.slides.count() = 0 then return
    a = m.top.findNode("aMode")
    reverse = (md = "hero")
    m.top.findNode("iRows").reverse = reverse
    m.top.findNode("iHero").reverse = reverse
    a.control = "start"
    if md = "rows" then
        m.rows.setFocus(true)
    else
        m.rows.jumpToRow = 0
        m.top.setFocus(true)
    end if
    paintBtn()
end sub

sub onRot()
    if m.mode = "hero" and m.slides.count() > 1 then
        m.idx = (m.idx + 1) mod m.slides.count()
        paintHero()
    end if
end sub

sub onSelect()
    p = m.rows.rowItemSelected
    row = m.rows.content.getChild(p[0])
    if row = invalid then return
    it = row.getChild(p[1])
    if it = invalid then return
    key = m.specs[p[0]].key
    cat = m.global.cat
    t = cat.byId[it.titleId]
    if t = invalid then return
    if key = "cont" then
        tg = PlayTarget(t, m.global.user)
        if tg <> invalid then
            m.top.nav = { action: "push", kind: "player", args: { id: t.id, url: tg.url, season: tg.season, episode: tg.episode, restart: false } }
            return
        end if
    end if
    m.top.nav = { action: "push", kind: "detail", args: { id: t.id } }
end sub

sub act()
    if m.slides.count() = 0 then return
    t = m.slides[m.idx].t
    user = m.global.user
    if m.bi = 0 then
        tg = PlayTarget(t, user)
        if tg <> invalid and not t.isSoon then
            m.top.nav = { action: "push", kind: "player", args: { id: t.id, url: tg.url, season: tg.season, episode: tg.episode, restart: false } }
        else
            m.top.nav = { action: "push", kind: "detail", args: { id: t.id } }
        end if
    else if m.bi = 1 then
        if not user.signed then
            m.top.nav = { action: "push", kind: "login", args: {} }
        else
            tk = CreateObject("roSGNode", "DataTask")
            tk.op = "toggle"
            tk.args = { id: t.id }
            m.tk = tk
            tk.control = "RUN"
        end if
    else
        m.idx = (m.idx + 1) mod m.slides.count()
        paintHero()
    end if
end sub

function onKeyEvent(key as String, press as Boolean) as Boolean
    if not press then return false
    m.top.findNode("rot").control = "start"
    if m.mode = "empty" then
        if key = "OK" then
            tk = CreateObject("roSGNode", "DataTask")
            tk.op = "catalog"
            tk.args = {}
            m.tk = tk
            tk.control = "RUN"
            return true
        else if key = "up" then
            m.top.nav = { action: "focusNav" }
            return true
        end if
        return false
    end if
    if m.mode = "hero" then
        n = 2
        if m.slides.count() < 2 then n = 1
        if key = "right" then
            if m.bi < n then m.bi = m.bi + 1
            paintBtn()
            return true
        else if key = "left" then
            if m.bi > 0 then m.bi = m.bi - 1
            paintBtn()
            return true
        else if key = "OK" then
            act()
            return true
        else if key = "down" then
            setMode("rows")
            return true
        else if key = "up" then
            m.top.nav = { action: "focusNav" }
            return true
        end if
        return false
    end if
    ' modo filas
    if key = "up" then
        r = m.rows.rowItemFocused
        if r <> invalid and r[0] = 0 then
            if m.slides.count() > 0 then
                setMode("hero")
            else
                m.top.nav = { action: "focusNav" }
            end if
            return true
        end if
    else if key = "back" then
        if m.slides.count() > 0 then
            setMode("hero")
            return true
        end if
    end if
    return false
end function
