' Escena principal: arma la pila de pantallas, la barra de arriba, la carga inicial y el cartel de "sitio cerrado".

sub init()
    m.content = m.top.findNode("content")
    m.nav = m.top.findNode("nav")
    m.loading = m.top.findNode("loading")
    m.closedScr = m.top.findNode("closedScr")
    m.top.findNode("loadTxt").font = Fnt("book", 30)
    m.stack = []
    m.section = ""
    m.live = []
    m.userReady = false
    m.nav.observeField("choose", "onNavChoose")
    m.nav.observeField("exitDown", "onNavDown")
    m.closedScr.observeField("retry", "onRetryClosed")
    m.closedScr.observeField("quit", "onQuit")
    m.global.observeField("loaded", "onLoaded")
    m.global.observeField("closed", "updateClosed")
    m.global.observeField("closedMsg", "updateClosed")
    m.global.observeField("user", "onUser")
    m.global.observeField("cat", "onCat")
    m.poll = m.top.findNode("poll")
    m.poll.observeField("fire", "onPoll")
    m.top.setFocus(true)
    if Reg().exists("session") then
        runTask("refreshuser", {}, "onBoot1")
    else
        onBoot1()
    end if
end sub

function runTask(op as String, args as Object, cb as String) as Object
    t = CreateObject("roSGNode", "DataTask")
    t.op = op
    t.args = args
    if cb <> "" then t.observeField("result", cb)
    keep = []
    for each x in m.live
        if x.state <> "stop" then keep.push(x)
    end for
    keep.push(t)
    m.live = keep
    t.control = "RUN"
    return t
end function

sub onBoot1()
    m.userReady = true
    runTask("catalog", { cacheOnly: true }, "onBoot2")
end sub

sub onBoot2()
    runTask("catalog", {}, "onBoot3")
end sub

sub onBoot3()
    m.poll.control = "start"
    updateClosed()
end sub

sub onPoll()
    runTask("closed", {}, "")
end sub

sub onCat()
    c = m.global.cat
    if c <> invalid and c.logo <> invalid then m.nav.logo = c.logo
end sub

sub onUser()
    u = m.global.user
    m.nav.avatar = AsStr(u.avatar)
    updateClosed()
end sub

sub onLoaded()
    if m.global.loaded and m.stack.count() = 0 then
        m.loading.visible = false
        showSection("home")
    end if
    updateClosed()
end sub

sub onRetryClosed()
    runTask("closed", {}, "")
end sub

sub onQuit()
    m.top.quit = true
end sub

sub updateClosed()
    u = m.global.user
    locked = m.userReady and m.global.closed and not u.isOwner
    if locked and not m.closedScr.visible then
        m.closedScr.msg = m.global.closedMsg
        m.closedScr.visible = true
        m.closedScr.callFunc("takeFocus")
    else if not locked and m.closedScr.visible then
        m.closedScr.visible = false
        focusCurrent()
    else if locked then
        m.closedScr.msg = m.global.closedMsg
    end if
end sub

' ---------------- pantallas ----------------
function compFor(kind as String) as String
    if kind = "home" then return "HomeScreen"
    if kind = "search" then return "SearchScreen"
    if kind = "account" then return "AccountScreen"
    if kind = "detail" then return "DetailScreen"
    if kind = "player" then return "PlayerScreen"
    if kind = "login" then return "LoginScreen"
    return "GridScreen"
end function

function isSection(kind as String) as Boolean
    for each k in ["home", "series", "movies", "mylist", "search", "account"]
        if k = kind then return true
    end for
    return false
end function

function makeScreen(kind as String, args as Object) as Object
    if args = invalid then args = {}
    if kind = "series" or kind = "movies" or kind = "mylist" then args.mode = kind
    n = CreateObject("roSGNode", compFor(kind))
    n.args = args
    n.observeField("nav", "onScreenNav")
    return n
end function

sub clearStack()
    for each e in m.stack
        m.content.removeChild(e.node)
    end for
    m.stack = []
end sub

sub showSection(kind as String)
    clearStack()
    m.section = kind
    n = makeScreen(kind, {})
    m.content.appendChild(n)
    m.stack.push({ node: n, kind: kind })
    m.nav.selected = kind
    m.nav.visible = true
    n.callFunc("takeFocus")
end sub

sub pushScreen(kind as String, args as Object)
    if m.stack.count() > 0 then m.content.removeChild(m.stack.peek().node)
    n = makeScreen(kind, args)
    m.content.appendChild(n)
    m.stack.push({ node: n, kind: kind })
    m.nav.visible = isSection(kind)
    n.callFunc("takeFocus")
end sub

sub popScreen()
    if m.stack.count() < 2 then return
    e = m.stack.pop()
    if e.kind = "player" or e.kind = "login" then e.node.callFunc("onClose")
    m.content.removeChild(e.node)
    t = m.stack.peek()
    m.content.appendChild(t.node)
    m.nav.visible = isSection(t.kind)
    t.node.callFunc("takeFocus")
end sub

sub focusCurrent()
    if m.stack.count() = 0 then return
    m.stack.peek().node.callFunc("takeFocus")
end sub

sub onScreenNav(ev as Object)
    a = ev.getData()
    if a = invalid then return
    act = a.action
    if act = "push" then
        pushScreen(a.kind, a.args)
    else if act = "pop" then
        popScreen()
    else if act = "replace" then
        e = m.stack.pop()
        if e.kind = "player" or e.kind = "login" then e.node.callFunc("onClose")
        m.content.removeChild(e.node)
        pushScreen(a.kind, a.args)
    else if act = "section" then
        showSection(a.kind)
    else if act = "focusNav" then
        m.nav.callFunc("takeFocus")
    else if act = "quit" then
        m.top.quit = true
    end if
end sub

sub onNavChoose(ev as Object)
    k = ev.getData()
    if k = "account" then
        pushSectionLike("account")
    else if k = m.section and m.stack.count() = 1 then
        focusCurrent()
    else
        showSection(k)
    end if
end sub

sub pushSectionLike(kind as String)
    showSection(kind)
end sub

sub onNavDown()
    focusCurrent()
end sub

function onKeyEvent(key as String, press as Boolean) as Boolean
    if not press or key <> "back" then return false
    if m.closedScr.visible then return false
    if m.stack.count() > 1 then
        popScreen()
        return true
    end if
    if m.section <> "home" and m.section <> "" then
        showSection("home")
        return true
    end if
    return false
end function
