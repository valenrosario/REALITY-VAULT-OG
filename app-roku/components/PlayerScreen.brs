' Reproductor: continúa donde lo dejaste, subtítulos, "Saltear intro", pasa solo al capítulo siguiente y guarda el minuto.

sub init()
    m.video = m.top.findNode("video")
    m.skipG = m.top.findNode("skipG")
    m.toast = m.top.findNode("toast")
    m.err = m.top.findNode("err")
    m.skipBtn = m.top.findNode("skipBtn")
    m.top.findNode("skipHint").font = Fnt("book", 24)
    m.toast.font = Fnt("bold", 36)
    m.err.font = Fnt("bold", 40)
    m.video.observeField("state", "onState")
    m.video.observeField("position", "onPos")
    m.top.findNode("saveT").observeField("fire", "onSave")
    m.started = false
    m.skips = []
    m.skipOn = false
    m.skipFocus = false
    m.done = false
    m.live = []
end sub

sub takeFocus()
    if not m.started then
        m.started = true
        start()
    end if
    m.video.setFocus(true)
end sub

function findEp(t as Object, season as Dynamic, episode as Dynamic) as Dynamic
    if season = invalid or episode = invalid then return invalid
    for each s in t.seasons
        if s.number = season then
            if episode >= 1000 then
                i = episode - 1000
                if i >= 0 and i < s.extras.count() then return s.extras[i]
                return invalid
            end if
            for each e in s.episodes
                if e.number = episode then return e
            end for
        end if
    end for
    return invalid
end function

sub start()
    a = m.top.args
    cat = m.global.cat
    m.t = cat.byId[a.id]
    m.season = a.season
    m.episode = a.episode
    m.trailer = (a.trailer = true)
    play(a.url, Bo(a.restart))
    if not m.trailer then
        runTask("watch", { id: a.id, season: m.season, episode: m.episode })
        runTask("view", { id: a.id })
    end if
end sub

sub runTask(op as String, args as Object)
    tk = CreateObject("roSGNode", "DataTask")
    tk.op = op
    tk.args = args
    keep = []
    for each x in m.live
        if x.state <> "stop" then keep.push(x)
    end for
    keep.push(tk)
    m.live = keep
    tk.control = "RUN"
end sub

sub play(url as String, restart as Boolean)
    t = m.t
    if not Playable(url) then
        showError("Este enlace no se puede reproducir en Roku.")
        return
    end if
    ep = invalid
    if t <> invalid then ep = findEp(t, m.season, m.episode)
    m.skips = []
    subs = ""
    ttl = ""
    if t <> invalid then
        ttl = t.title
        subs = t.subs
        m.skips = t.skips
    end if
    if ep <> invalid then
        if ep.subs <> "" then subs = ep.subs
        if ep.skips.count() > 0 then m.skips = ep.skips
        if m.episode >= 1000 then
            ttl = ttl + "  ·  Extras"
        else
            ttl = ttl + "  ·  T" + m.season.toStr() + ":E" + m.episode.toStr()
        end if
        if ep.title <> "" then ttl = ttl + "  " + ep.title
    end if
    c = CreateObject("roSGNode", "ContentNode")
    c.url = url
    c.streamFormat = StreamFormat(url)
    c.title = ttl
    if not m.trailer and not restart and t <> invalid then
        p = GetPos(m.global.user, t.id, m.season, m.episode)
        if PosResumable(p) then c.playStart = p.t
    end if
    m.content = c
    m.skipOn = false
    m.skipG.visible = false
    m.err.visible = false
    if subs <> "" and IsHttp(subs) then
        ' Roku decide el formato del subtítulo por la extensión; si no la tiene se mira el archivo
        m.pendingSubs = subs
        tk = CreateObject("roSGNode", "DataTask")
        tk.op = "subkind"
        tk.args = { url: subs }
        tk.observeField("result", "onSubKind")
        m.subTask = tk
        tk.control = "RUN"
        m.subTimer = CreateObject("roSGNode", "Timer")
        m.subTimer.duration = 6
        m.subTimer.observeField("fire", "onSubTimeout")
        m.subTimer.control = "start"
    else
        launch()
    end if
end sub

sub onSubKind()
    if m.pendingSubs = invalid then return
    r = m.subTask.result
    kind = "srt"
    if r <> invalid and r.kind <> invalid then kind = r.kind
    u = m.pendingSubs
    p = LCase(u.split("?")[0])
    if Right(p, 4) <> ".vtt" and Right(p, 4) <> ".srt" then u = u + "#.sub." + kind
    m.content.subtitleTracks = [{ Language: "spa", TrackName: u, Description: "Español" }]
    m.pendingSubs = invalid
    if m.subTimer <> invalid then m.subTimer.control = "stop"
    launch()
end sub

sub onSubTimeout()
    if m.pendingSubs <> invalid then
        m.pendingSubs = invalid
        launch()
    end if
end sub

sub launch()
    m.video.content = m.content
    m.video.globalCaptionMode = "On"
    m.video.control = "play"
    m.video.setFocus(true)
    m.top.findNode("saveT").control = "start"
end sub

sub showError(msg as String)
    m.err.text = msg + Chr(10) + "Apretá atrás para volver."
    m.err.visible = true
end sub

sub onState()
    st = m.video.state
    if st = "error" then
        showError("No se pudo reproducir este video.")
    else if st = "finished" then
        finishNow()
    else if st = "paused" then
        savePos()
    end if
end sub

sub finishNow()
    if m.trailer then
        m.top.nav = { action: "pop" }
        return
    end if
    ' lo termina: se marca como visto y pasa al siguiente
    d = Int(m.video.duration)
    if d > 0 then runTask("pos", { id: m.top.args.id, season: m.season, episode: m.episode, t: d, d: d })
    nx = invalid
    if m.t <> invalid then nx = NextAfter(m.t, m.season, m.episode)
    if nx <> invalid then
        m.season = nx.s
        m.episode = nx.e.number
        m.toast.text = "Siguiente capítulo  ·  T" + nx.s.toStr() + ":E" + nx.e.number.toStr()
        m.toast.visible = true
        runTask("watch", { id: m.top.args.id, season: m.season, episode: m.episode })
        play(nx.e.url, true)
        m.top.findNode("saveT").control = "start"
        tt = CreateObject("roSGNode", "Timer")
        tt.duration = 3
        tt.observeField("fire", "hideToast")
        m.tt = tt
        tt.control = "start"
    else
        m.top.nav = { action: "pop" }
    end if
end sub

sub hideToast()
    m.toast.visible = false
end sub

sub onPos()
    ms = Int(m.video.position * 1000)
    hit = invalid
    for each k in m.skips
        if ms >= k.fromMs and ms < k.toMs - 1500 then hit = k
    end for
    if hit <> invalid and not m.skipOn then
        m.skipOn = true
        m.cur = hit
        m.skipBtn.text = hit.label
        m.skipG.visible = true
        m.skipFocus = true
        m.skipG.setFocus(true)
    else if hit = invalid and m.skipOn then
        m.skipOn = false
        m.skipG.visible = false
        if m.skipFocus then m.video.setFocus(true)
        m.skipFocus = false
    end if
end sub

sub savePos()
    if m.trailer then return
    d = Int(m.video.duration)
    p = Int(m.video.position)
    if d > 0 and p > 0 then runTask("pos", { id: m.top.args.id, season: m.season, episode: m.episode, t: p, d: d })
end sub

sub onSave()
    if m.video.state = "playing" then savePos()
end sub

sub onClose()
    savePos()
    m.video.control = "stop"
    m.top.findNode("saveT").control = "stop"
end sub

function onKeyEvent(key as String, press as Boolean) as Boolean
    if not press then return false
    if m.skipOn and m.skipFocus then
        if key = "OK" then
            m.video.seek = m.cur.toMs / 1000
            m.skipOn = false
            m.skipFocus = false
            m.skipG.visible = false
            m.video.setFocus(true)
            return true
        else if key <> "back" then
            ' cualquier otra tecla: el control vuelve al video (la primera vez solo oculta el botón)
            m.skipFocus = false
            m.skipG.visible = false
            m.video.setFocus(true)
            return true
        end if
    end if
    if m.err.visible and key = "OK" then
        m.top.nav = { action: "pop" }
        return true
    end if
    return false
end function
