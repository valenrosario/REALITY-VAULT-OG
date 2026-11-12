sub init()
    m.top.findNode("t").font = Fnt("bold", 80)
    m.top.findNode("m").font = Fnt("book", 36)
    m.b = [m.top.findNode("b0"), m.top.findNode("b1")]
    m.i = 0
    onMsg()
end sub

sub onMsg()
    t = m.top.msg
    if t = "" then t = "Estamos haciendo cambios. Volvé a intentar más tarde."
    m.top.findNode("m").text = t
end sub

sub takeFocus()
    m.top.setFocus(true)
    m.i = 0
    paint()
end sub

sub paint()
    for k = 0 to 1
        m.b[k].focused = (k = m.i)
    end for
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
            m.top.retry = true
        else
            m.top.quit = true
        end if
        return true
    else if key = "back" then
        m.top.quit = true
        return true
    end if
    return false
end function
