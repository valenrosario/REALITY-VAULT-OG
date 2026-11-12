' Tarjeta única para todas las filas y grillas: póster, ancha (seguir viendo), top 10, capítulo, reparto.
sub init()
    m.bg = m.top.findNode("bg")
    m.ph = m.top.findNode("ph")
    m.img = m.top.findNode("img")
    m.shade = m.top.findNode("shade")
    m.num = m.top.findNode("num")
    m.tag = m.top.findNode("tag")
    m.progBg = m.top.findNode("progBg")
    m.prog = m.top.findNode("prog")
    m.title = m.top.findNode("title")
    m.sub = m.top.findNode("sub")
    m.ph.font = Fnt("bold", 26)
    m.num.font = Fnt("bold", 150)
    m.tag.font = Fnt("bold", 22)
    m.title.font = Fnt("bold", 24)
    m.sub.font = Fnt("book", 21)
end sub

sub onSize()
    onContent()
end sub

sub onContent()
    c = m.top.itemContent
    w = m.top.width
    h = m.top.height
    if c = invalid or w = 0 or h = 0 then return
    kind = c.kind
    if kind = invalid then kind = "poster"
    m.num.visible = false
    m.tag.visible = false
    m.shade.visible = false
    m.progBg.visible = false
    m.prog.visible = false
    m.title.visible = false
    m.sub.visible = false
    ix = 0
    iw = w
    ih = h
    if kind = "top" then
        ix = 150
        iw = w - 150
        m.num.visible = true
        m.num.translation = [0, 0]
        m.num.width = 140
        m.num.height = h
        m.num.text = c.rank
    else if kind = "ep" then
        ih = Int(w * 9 / 16)
        m.title.visible = true
        m.title.translation = [0, ih + 10]
        m.title.width = w
        m.title.text = c.title
        m.sub.visible = true
        m.sub.translation = [0, ih + 42]
        m.sub.width = w
        m.sub.height = 56
        m.sub.text = c.sub
    else if kind = "cast" then
        ih = h - 74
        m.title.visible = true
        m.title.translation = [0, ih + 8]
        m.title.width = w
        m.title.text = c.title
        m.sub.visible = true
        m.sub.translation = [0, ih + 38]
        m.sub.width = w
        m.sub.height = 30
        m.sub.maxLines = 1
        m.sub.text = c.sub
    end if
    m.bg.translation = [ix, 0]
    m.bg.width = iw
    m.bg.height = ih
    m.ph.translation = [ix + 12, 0]
    m.ph.width = iw - 24
    m.ph.height = ih
    m.ph.text = c.title
    m.ph.visible = (c.img = "")
    m.img.translation = [ix, 0]
    m.img.width = iw
    m.img.height = ih
    if c.img <> "" then
        SetImg(m.img, c.img, Int(iw), Int(ih))
    else
        m.img.uri = ""
    end if
    if kind = "wide" and c.tag <> "" then
        m.shade.visible = true
        m.shade.translation = [0, ih - 90]
        m.shade.width = iw
        m.shade.height = 90
        m.tag.visible = true
        m.tag.translation = [16, ih - 44]
        m.tag.text = c.tag
    end if
    if (kind = "wide" or kind = "ep") and c.prog > 0 then
        m.progBg.visible = true
        m.progBg.translation = [0, ih - 6]
        m.progBg.width = iw
        m.progBg.height = 6
        m.prog.visible = true
        m.prog.translation = [0, ih - 6]
        m.prog.width = Int(iw * c.prog / 100)
        m.prog.height = 6
    end if
end sub
