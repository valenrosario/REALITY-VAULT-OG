' Búsqueda por título, reparto, género o año (en el catálogo ya descargado: responde al instante).

sub init()
    m.kb = m.top.findNode("kb")
    m.grid = m.top.findNode("grid")
    m.empty = m.top.findNode("empty")
    m.top.findNode("heading").font = Fnt("bold", 52)
    m.empty.font = Fnt("book", 32)
    m.kb.observeField("text", "onText")
    m.grid.observeField("itemSelected", "onSelect")
    m.ids = []
    m.inGrid = false
    onText()
end sub

sub takeFocus()
    if m.inGrid and m.ids.count() > 0 then
        m.grid.setFocus(true)
    else
        m.inGrid = false
        m.kb.setFocus(true)
    end if
end sub

sub onText()
    q = LCase(m.kb.text.trim())
    cat = m.global.cat
    m.ids = []
    root = CreateObject("roSGNode", "ContentNode")
    if q <> "" and cat <> invalid and cat.titles <> invalid then
        for each t in PubTitles(cat)
            hit = Instr(1, LCase(t.title), q) > 0 or Instr(1, t.year, q) > 0
            if not hit then
                for each g in t.genres
                    if Instr(1, LCase(g), q) > 0 then hit = true
                end for
            end if
            if not hit then
                for each c in t.cast
                    if Instr(1, LCase(c.name), q) > 0 then hit = true
                end for
            end if
            if hit then
                m.ids.push(t.id)
                root.appendChild(MkItem("poster", t, "", 0, "", ""))
            end if
        end for
    end if
    m.grid.content = root
    m.grid.visible = m.ids.count() > 0
    if q = "" then
        m.empty.text = "Escribí el nombre de una serie, película o actor."
    else if m.ids.count() = 0 then
        m.empty.text = "No encontramos nada para " + Chr(34) + m.kb.text + Chr(34) + "."
    else
        m.empty.text = ""
    end if
end sub

sub onSelect()
    i = m.grid.itemSelected
    if i >= 0 and i < m.ids.count() then m.top.nav = { action: "push", kind: "detail", args: { id: m.ids[i] } }
end sub

function onKeyEvent(key as String, press as Boolean) as Boolean
    if not press then return false
    if key = "right" and not m.inGrid and m.ids.count() > 0 then
        m.inGrid = true
        m.grid.setFocus(true)
        return true
    else if key = "left" and m.inGrid then
        if m.grid.itemFocused mod 5 = 0 then
            m.inGrid = false
            m.kb.setFocus(true)
            return true
        end if
    else if key = "up" then
        if m.inGrid and m.grid.itemFocused < 5 then
            m.top.nav = { action: "focusNav" }
            return true
        else if not m.inGrid then
            m.top.nav = { action: "focusNav" }
            return true
        end if
    end if
    return false
end function
