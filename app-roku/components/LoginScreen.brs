' Entrar con el celular (QR + código de 6 letras, igual que la app de la tele) o con email y contraseña.

sub init()
    m.top.findNode("h1").font = Fnt("bold", 72)
    m.top.findNode("sub").font = Fnt("light", 28)
    for each k in ["s1", "s2", "s3"]
        m.top.findNode(k).font = Fnt("book", 30)
    end for
    m.top.findNode("exp").font = Fnt("book", 24)
    m.top.findNode("err").font = Fnt("book", 28)
    m.top.findNode("qrTxt").font = Fnt("book", 28)
    m.top.findNode("status").font = Fnt("book", 24)
    site = Cfg().site
    host = site.replace("https://", "").replace("http://", "")
    m.top.findNode("s1").text = "1   Escaneá el código QR con la cámara del celular, o entrá a " + host + "/activar"
    m.b = [m.top.findNode("b0"), m.top.findNode("b1")]
    m.i = 0
    m.boxes = m.top.findNode("boxes")
    m.labels = []
    for k = 0 to 5
        r = CreateObject("roSGNode", "Rectangle")
        r.color = "0xFFFFFF22"
        r.width = 64
        r.height = 80
        x = k * 72
        if k > 2 then x = x + 16
        r.translation = [x, 0]
        l = CreateObject("roSGNode", "Label")
        l.font = Fnt("bold", 46)
        l.width = 64
        l.height = 80
        l.horizAlign = "center"
        l.vertAlign = "center"
        l.color = "0xFFFFFFFF"
        r.appendChild(l)
        m.boxes.appendChild(r)
        m.labels.push(l)
    end for
    m.mode = "code"
    startActivate()
    if m.top.args <> invalid and m.top.args.email = true then askEmail()
end sub

sub startActivate()
    t = CreateObject("roSGNode", "DataTask")
    t.op = "activate"
    t.args = {}
    t.observeField("code", "onCode")
    t.observeField("left", "onLeft")
    t.observeField("status", "onStatus")
    t.observeField("result", "onResult")
    m.act = t
    t.control = "RUN"
end sub

sub onCode()
    c = m.act.code
    for k = 0 to 5
        if k < Len(c) then
            m.labels[k].text = Mid(c, k + 1, 1)
        else
            m.labels[k].text = ""
        end if
    end for
    url = Cfg().site + "/activar?c=" + c
    m.top.findNode("qr").uri = "https://api.qrserver.com/v1/create-qr-code/?size=408x408&margin=0&data=" + CreateObject("roUrlTransfer").escape(url)
end sub

sub onLeft()
    l = m.act.left
    if l < 0 then l = 0
    sec = l mod 60
    t = "Vence en " + Int(l / 60).toStr() + ":"
    if sec < 10 then t = t + "0"
    m.top.findNode("exp").text = t + sec.toStr()
end sub

sub onStatus()
    m.top.findNode("status").text = m.act.status
end sub

sub onResult()
    r = m.act.result
    if r <> invalid and r.ok = true then
        m.top.nav = { action: "pop" }
    else if r <> invalid and r.error <> invalid then
        m.top.findNode("err").text = "La activación con el celular no funcionó (" + r.error + "). Entrá con tu email y contraseña."
    end if
end sub

sub takeFocus()
    m.top.setFocus(true)
    paint()
end sub

sub onClose()
    if m.act <> invalid then m.act.control = "STOP"
end sub

sub paint()
    for k = 0 to 1
        m.b[k].focused = (m.i = k)
    end for
end sub

' ---------------- email y contraseña ----------------
sub askEmail()
    d = CreateObject("roSGNode", "StandardKeyboardDialog")
    d.title = "Tu email"
    d.message = ["Escribí el email de tu cuenta."]
    d.buttons = ["Siguiente", "Cancelar"]
    d.observeFieldScoped("buttonSelected", "onEmailBtn")
    m.dlg = d
    m.top.getScene().dialog = d
end sub

sub onEmailBtn()
    i = m.dlg.buttonSelected
    m.email = m.dlg.text.trim()
    m.top.getScene().dialog = invalid
    if i = 0 and m.email <> "" then askPass()
end sub

sub askPass()
    d = CreateObject("roSGNode", "StandardKeyboardDialog")
    d.title = "Tu contraseña"
    d.message = [m.email]
    d.buttons = ["Entrar", "Cancelar"]
    d.textEditBox.secureMode = true
    d.observeFieldScoped("buttonSelected", "onPassBtn")
    m.dlg = d
    m.top.getScene().dialog = d
end sub

sub onPassBtn()
    i = m.dlg.buttonSelected
    pass = m.dlg.text
    m.top.getScene().dialog = invalid
    if i = 0 and pass <> "" then
        m.top.findNode("err").text = ""
        m.top.findNode("status").text = "Entrando…"
        t = CreateObject("roSGNode", "DataTask")
        t.op = "login"
        t.args = { email: m.email, pass: pass }
        t.observeField("result", "onLoginResult")
        m.lt = t
        t.control = "RUN"
    end if
end sub

sub onLoginResult()
    r = m.lt.result
    if r <> invalid and r.ok = true then
        m.top.nav = { action: "pop" }
    else
        m.top.findNode("status").text = ""
        m.top.findNode("err").text = r.error
        takeFocus()
    end if
end sub

function onKeyEvent(key as String, press as Boolean) as Boolean
    if not press then return false
    if key = "right" and m.i = 0 then
        m.i = 1
        paint()
        return true
    else if key = "left" and m.i = 1 then
        m.i = 0
        paint()
        return true
    else if key = "OK" then
        if m.i = 0 then
            askEmail()
        else
            m.top.nav = { action: "pop" }
        end if
        return true
    end if
    return false
end function
