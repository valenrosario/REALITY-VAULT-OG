' Barra de arriba: logo, Inicio / Series / Películas / Mi lista, lupa y foto de perfil (igual que en la tele).
sub init()
    m.items = m.top.findNode("items")
    m.avImg = m.top.findNode("avatarImg")
    m.avRing = m.top.findNode("avRing")
    m.defs = [
        { id: "home", text: "Inicio", icon: "" },
        { id: "series", text: "Series", icon: "" },
        { id: "movies", text: "Películas", icon: "" },
        { id: "mylist", text: "Mi lista", icon: "" },
        { id: "search", text: "", icon: "search" }
    ]
    m.nodes = []
    x = 0
    for each d in m.defs
        b = CreateObject("roSGNode", "RvButton")
        b.look = "glass"
        b.h = 64
        b.text = d.text
        b.icon = d.icon
        if d.icon <> "" then
            b.look = "round"
        end if
        b.translation = [x, 0]
        b.opacity = 1
        m.items.appendChild(b)
        m.nodes.push(b)
        x = x + b.w + 12
    end for
    m.idx = 0
    m.hasFocus = false
    m.top.observeField("focusedChild", "onFocus")
    paint()
end sub

sub onAvatar()
    a = m.top.avatar
    if a = "a1" or a = "a2" then
        m.avImg.uri = "pkg:/images/av_" + a + ".png"
    else
        m.avImg.uri = "pkg:/images/av_a1.png"
    end if
end sub

sub onLogo()
    if m.top.logo <> "" then
        p = m.top.findNode("logoImg")
        p.uri = m.top.logo
    end if
end sub

sub takeFocus()
    sel = m.top.selected
    m.idx = 0
    for i = 0 to m.defs.count() - 1
        if m.defs[i].id = sel then m.idx = i
    end for
    if sel = "account" then m.idx = m.defs.count()
    m.top.setFocus(true)
    m.hasFocus = true
    paint()
end sub

sub onFocus()
    m.hasFocus = m.top.isInFocusChain()
    paint()
end sub

sub paint()
    if m.nodes = invalid then return
    sel = m.top.selected
    for i = 0 to m.nodes.count() - 1
        b = m.nodes[i]
        on1 = (m.defs[i].id = sel)
        foc = m.hasFocus and m.idx = i
        if on1 and m.defs[i].icon = "" then
            b.look = "primary"
        else if m.defs[i].icon <> "" then
            b.look = "round"
        else
            b.look = "glass"
        end if
        b.focused = foc
    end for
    m.avRing.visible = m.hasFocus and m.idx = m.defs.count()
end sub

function onKeyEvent(key as String, press as Boolean) as Boolean
    if not press then return false
    n = m.defs.count()
    if key = "right" then
        if m.idx < n then m.idx = m.idx + 1
        paint()
        return true
    else if key = "left" then
        if m.idx > 0 then m.idx = m.idx - 1
        paint()
        return true
    else if key = "down" then
        m.hasFocus = false
        paint()
        m.top.exitDown = true
        return true
    else if key = "OK" then
        if m.idx = n then
            m.top.choose = "account"
        else
            m.top.choose = m.defs[m.idx].id
        end if
        return true
    end if
    return false
end function
