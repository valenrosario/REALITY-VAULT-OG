' Grilla de póster: Series, Películas, Mi lista, una colección o las obras de un actor.

sub init()
    m.grid = m.top.findNode("grid")
    m.empty = m.top.findNode("empty")
    m.top.findNode("heading").font = Fnt("bold", 52)
    m.empty.font = Fnt("book", 34)
    m.grid.observeField("itemSelected", "onSelect")
    m.global.observeField("user", "onUser")
    m.mode = ""
end sub

sub onUser()
    if m.mode = "mylist" then build()
end sub

sub build()
    a = m.top.args
    if a = invalid then return
    m.mode = AsStr(a.mode)
    cat = m.global.cat
    user = m.global.user
    head = AsStr(a.heading)
    list = []
    msg = ""
    if cat = invalid or cat.titles = invalid then return
    if m.mode = "series" or m.mode = "movies" then
        head = "Series"
        if m.mode = "movies" then head = "Películas"
        for each t in PubTitles(cat)
            if (m.mode = "series") = t.isSerie then list.push(t)
        end for
        list.sortBy("createdAt", "r")
        msg = "Todavía no hay títulos acá."
    else if m.mode = "mylist" then
        head = "Mi lista"
        if not user.signed then
            msg = "Iniciá sesión para armar tu lista. Apretá OK para entrar."
        else
            for each id in user.myList
                t = PubById(cat, id)
                if t <> invalid then list.push(t)
            end for
            msg = "Tu lista está vacía. Agregá títulos con el corazón."
        end if
    else if m.mode = "person" then
        for each t in PubTitles(cat)
            for each c in t.cast
                if c.name = head then
                    list.push(t)
                    exit for
                end if
            end for
        end for
        msg = "No encontramos obras de esta persona."
    else
        for each id in Lst(a.ids)
            t = PubById(cat, id)
            if t <> invalid then list.push(t)
        end for
        msg = "Esta colección está vacía."
    end if
    m.top.findNode("heading").text = head
    root = CreateObject("roSGNode", "ContentNode")
    for each t in list
        root.appendChild(MkItem("poster", t, "", 0, "", ""))
    end for
    m.ids = []
    for each t in list
        m.ids.push(t.id)
    end for
    m.grid.content = root
    m.empty.text = msg
    m.empty.visible = (list.count() = 0)
    m.grid.visible = (list.count() > 0)
end sub

sub takeFocus()
    if m.grid.visible then
        m.grid.setFocus(true)
    else
        m.top.setFocus(true)
    end if
end sub

sub onSelect()
    i = m.grid.itemSelected
    if m.ids <> invalid and i >= 0 and i < m.ids.count() then
        m.top.nav = { action: "push", kind: "detail", args: { id: m.ids[i] } }
    end if
end sub

function onKeyEvent(key as String, press as Boolean) as Boolean
    if not press then return false
    if key = "up" then
        i = m.grid.itemFocused
        if not m.grid.visible or i < 6 then
            m.top.nav = { action: "focusNav" }
            return true
        end if
    else if key = "OK" and m.mode = "mylist" and not m.global.user.signed then
        m.top.nav = { action: "push", kind: "login", args: {} }
        return true
    end if
    return false
end function
