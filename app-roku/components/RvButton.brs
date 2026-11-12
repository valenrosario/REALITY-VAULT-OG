' Botón en forma de píldora: "primary" (amarillo), "glass" (vidrio) y "round" (solo ícono, redondo).
sub init()
    m.bg = m.top.findNode("bg")
    m.ring = m.top.findNode("ring")
    m.ico = m.top.findNode("ico")
    m.lbl = m.top.findNode("lbl")
    m.lbl.font = Fnt("bold", 30)
    layout()
end sub

sub layout()
    h = m.top.h
    look = m.top.look
    txt = m.top.text
    ic = m.top.icon
    m.lbl.text = txt
    pad = 40
    iw = 0
    if ic <> "" then
        iw = 40
        if m.top.on and ic = "heart" then ic = "heart_on"
        m.ico.uri = "pkg:/images/ic_" + ic + ".png"
        if look = "primary" and ic = "play" then m.ico.uri = "pkg:/images/ic_play_dark.png"
    end if
    tw = 0
    if txt <> "" then
        m.lbl.width = 0
        m.lbl.height = 0
        tw = m.lbl.boundingRect().width
        ' piso por cantidad de letras: si la medida da de menos, el texto no se corta con "…"
        est = Int(Len(txt) * 30 * 0.5)
        if tw < est then tw = est
    end if
    gap = 0
    if txt <> "" and ic <> "" then gap = 14
    w = pad + iw + gap + tw + pad
    if look = "round" then w = h
    if w < m.top.minW then w = m.top.minW
    m.bg.width = w
    m.bg.height = h
    m.ring.translation = [-8, -8]
    m.ring.width = w + 16
    m.ring.height = h + 16
    m.ico.width = 40
    m.ico.height = 40
    if look = "round" then
        m.ico.translation = [(w - 40) / 2, (h - 40) / 2]
    else
        x0 = (w - (iw + gap + tw)) / 2
        m.ico.translation = [x0, (h - 40) / 2]
        m.lbl.translation = [x0 + iw + gap, 0]
    end if
    m.lbl.height = h
    m.lbl.width = tw + 30
    if look = "primary" then
        m.lbl.color = "0x000000FF"
        m.bg.blendColor = "0xEDEE3BFF"
    else
        m.lbl.color = "0xFFFFFFFF"
        m.bg.blendColor = "0xFFFFFFFF"
    end if
    m.top.w = w
    paint()
end sub

sub paint()
    f = m.top.focused
    m.ring.visible = f
    if m.top.look = "primary" then
        m.bg.opacity = 1
    else if f then
        m.bg.opacity = 0.38
    else
        m.bg.opacity = 0.2
    end if
end sub
