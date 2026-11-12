sub init()
    m.top.findNode("heading").font = Fnt("bold", 52)
    m.top.findNode("email").font = Fnt("bold", 44)
    m.top.findNode("role").font = Fnt("book", 30)
    m.top.findNode("ver").font = Fnt("book", 26)
    m.b = [m.top.findNode("b0"), m.top.findNode("b1")]
    m.i = 0
    m.top.observeField("focusedChild", "focusPaint")
    m.global.observeField("user", "paint")
    ai = CreateObject("roAppInfo")
    m.top.findNode("ver").text = "REALITY VAULT para Roku · versión " + ai.getVersion()
    paint()
end sub

sub paint()
    u = m.global.user
    av = m.top.findNode("av")
    if u.signed then
        a = u.avatar
        if a <> "a1" and a <> "a2" then a = "a1"
        av.uri = "pkg:/images/av_" + a + ".png"
        av.visible = true
        m.top.findNode("email").text = u.email
        if u.isOwner then
            m.top.findNode("role").text = "Cuenta de administrador"
        else
            m.top.findNode("role").text = "Sesión iniciada"
        end if
        m.b[0].text = "Cerrar sesión"
        m.b[0].look = "primary"
        m.b[1].visible = false
    else
        av.visible = false
        m.top.findNode("email").text = "No iniciaste sesión"
        m.top.findNode("role").text = "Entrá para guardar tu lista y seguir viendo donde lo dejaste."
        m.b[0].text = "Activar con el celular"
        m.b[0].look = "primary"
        m.b[1].text = "Entrar con email"
        m.b[1].visible = true
    end if
    m.i = 0
    focusPaint()
end sub

sub focusPaint()
    on1 = m.top.isInFocusChain()
    for k = 0 to 1
        m.b[k].focused = on1 and m.i = k
    end for
end sub

sub takeFocus()
    m.top.setFocus(true)
    focusPaint()
end sub

function onKeyEvent(key as String, press as Boolean) as Boolean
    if not press then return false
    u = m.global.user
    if key = "up" then
        if m.i > 0 then
            m.i = m.i - 1
            focusPaint()
        else
            m.top.nav = { action: "focusNav" }
        end if
        return true
    else if key = "down" then
        if not u.signed and m.i = 0 then
            m.i = 1
            focusPaint()
        end if
        return true
    else if key = "OK" then
        if u.signed then
            tk = CreateObject("roSGNode", "DataTask")
            tk.op = "logout"
            m.tk = tk
            tk.control = "RUN"
        else
            m.top.nav = { action: "push", kind: "login", args: { email: (m.i = 1) } }
        end if
        return true
    end if
    return false
end function
