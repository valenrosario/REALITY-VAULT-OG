' Todo lo que habla con Supabase (catálogo, cuenta, lista, progreso, activación por código). Corre en segundo plano
' y deja el resultado en m.global (cat / user / closed) para que las pantallas solo lo lean.

sub init()
    m.top.functionName = "go"
end sub

sub go()
    a = m.top.args
    if a = invalid then a = {}
    op = m.top.op
    res = { ok: true }
    if op = "catalog" then
        Catalog(Bo(a.cacheOnly))
    else if op = "closed" then
        CheckClosed()
    else if op = "refreshuser" then
        RefreshUser()
    else if op = "login" then
        res = Login(AsStr(a.email), AsStr(a.pass))
    else if op = "toggle" then
        ToggleList(AsStr(a.id))
    else if op = "watch" then
        RecordWatch(AsStr(a.id), a.season, a.episode)
    else if op = "ps" then
        SavePos(AsStr(a.id), a.season, a.episode, Num(a.t), Num(a.d))
    else if op = "delprog" then
        DelProgress(AsStr(a.id))
    else if op = "view" then
        TrackView(AsStr(a.id))
    else if op = "logout" then
        Logout()
    else if op = "activate" then
        res = Activate()
    else if op = "subkind" then
        res = { ok: true, kind: SubKind(AsStr(a.url)) }
    end if
    m.top.result = res
end sub

' ---------------- HTTP ----------------
function Http(method as String, path as String, body as Dynamic, tok as Dynamic, prefer as Dynamic) as Object
    c = Cfg()
    x = CreateObject("roUrlTransfer")
    x.setUrl(c.supa + path)
    x.setCertificatesFile("common:/certs/ca-bundle.crt")
    x.initClientCertificates()
    x.addHeader("apikey", c.key)
    if tok <> invalid then
        x.addHeader("Authorization", "Bearer " + tok)
    else
        x.addHeader("Authorization", "Bearer " + c.key)
    end if
    x.addHeader("Accept", "application/json")
    if prefer <> invalid then x.addHeader("Prefer", prefer)
    x.retainBodyOnError(true)
    port = CreateObject("roMessagePort")
    x.setMessagePort(port)
    if body <> invalid then
        x.addHeader("Content-Type", "application/json")
        x.setRequest(method)
        x.asyncPostFromString(body)
    else
        x.setRequest(method)
        x.asyncGetToString()
    end if
    ev = wait(20000, port)
    if type(ev) <> "roUrlEvent" then
        x.asyncCancel()
        return { code: -1, text: "" }
    end if
    return { code: ev.getResponseCode(), text: ev.getString() }
end function

function Ok(r as Object) as Boolean
    return r.code >= 200 and r.code < 300
end function

function Msg(r as Object) as String
    j = ParseJson(r.text)
    if type(j) = "roAssociativeArray" then
        for each k in ["msg", "error_description", "message"]
            if AsStr(j[k]) <> "" then return AsStr(j[k])
        end for
    end if
    return "Error " + r.code.toStr()
end function

' ---------------- sesión ----------------
function LoadSession() as Dynamic
    sec = Reg()
    if not sec.exists("session") then return invalid
    j = ParseJson(sec.read("session"))
    if type(j) <> "roAssociativeArray" then return invalid
    return j
end function

sub StoreSession(j as Object, prev as Dynamic)
    u = j.user
    s = { access: AsStr(j.access_token), refresh: AsStr(j.refresh_token), userId: "", email: "", avatar: "", expiresAt: 0 }
    if prev <> invalid then
        s.userId = AsStr(prev.userId)
        s.email = AsStr(prev.email)
        s.avatar = AsStr(prev.avatar)
    end if
    if type(u) = "roAssociativeArray" then
        s.userId = AsStr(u.id)
        s.email = AsStr(u.email)
        if type(u.user_metadata) = "roAssociativeArray" then s.avatar = AsStr(u.user_metadata.avatar)
    end if
    exp = j.expires_in
    if exp = invalid then exp = 3600
    s.expiresAt = CreateObject("roDateTime").asSeconds() + Num(exp, 3600)
    sec = Reg()
    sec.write("session", FormatJson(s))
    sec.flush()
end sub

sub Logout()
    sec = Reg()
    sec.delete("session")
    sec.flush()
    m.global.user = { signed: false, isOwner: false, myList: [], progress: {}, order: [], email: "", avatar: "", userId: "" }
end sub

function Token() as Dynamic
    s = LoadSession()
    if s = invalid then return invalid
    if s.expiresAt - 60 > CreateObject("roDateTime").asSeconds() then return s.access
    r = Http("POST", "/auth/v1/token?grant_type=refresh_token", FormatJson({ refresh_token: s.refresh }), invalid, invalid)
    if Ok(r) then
        j = ParseJson(r.text)
        if type(j) = "roAssociativeArray" then
            StoreSession(j, s)
            n = LoadSession()
            if n <> invalid then return n.access
        end if
    else if r.code = 400 or r.code = 401 or r.code = 403 then
        Logout()
    end if
    return invalid
end function

function Login(email as String, pass as String) as Object
    r = Http("POST", "/auth/v1/token?grant_type=password", FormatJson({ email: email, password: pass }), invalid, invalid)
    if not Ok(r) then
        t = Msg(r)
        if Instr(1, LCase(t), "invalid login") > 0 then t = "Email o contraseña incorrectos."
        return { ok: false, error: t }
    end if
    StoreSession(ParseJson(r.text), invalid)
    RefreshUser()
    return { ok: true }
end function

' ---------------- usuario ----------------
sub RefreshUser()
    s = LoadSession()
    tk = Token()
    if s = invalid or tk = invalid then
        Logout()
        return
    end if
    s = LoadSession()
    u = { signed: true, userId: AsStr(s.userId), email: AsStr(s.email), avatar: AsStr(s.avatar), isOwner: false, myList: [], progress: {}, order: [] }
    r = Http("POST", "/rest/v1/rpc/is_admin", "{}", tk, invalid)
    if Ok(r) then u.isOwner = (r.text.trim() = "true")
    r = Http("GET", "/auth/v1/user", invalid, tk, invalid)
    if Ok(r) then
        j = ParseJson(r.text)
        if type(j) = "roAssociativeArray" and type(j.user_metadata) = "roAssociativeArray" then u.avatar = AsStr(j.user_metadata.avatar)
    end if
    r = Http("GET", "/rest/v1/user_lists?select=title_id&order=created_at.desc", invalid, tk, invalid)
    if Ok(r) then
        for each o in Lst(ParseJson(r.text))
            if type(o) = "roAssociativeArray" then u.myList.push(AsStr(o.title_id))
        end for
    end if
    r = Http("GET", "/rest/v1/watch_progress?select=*&order=updated_at.desc", invalid, tk, invalid)
    if Ok(r) then
        for each o in Lst(ParseJson(r.text))
            if type(o) = "roAssociativeArray" then
                ps = {}
                if type(o.positions) = "roAssociativeArray" then
                    for each k in o.positions
                        v = o.positions[k]
                        if type(v) = "roAssociativeArray" then ps[k] = { t: Num(v.t), d: Num(v.d) }
                    end for
                end if
                w = []
                for each x in Lst(o.watched)
                    w.push(AsStr(x))
                end for
                se = invalid
                ep = invalid
                if AsStr(o.last_season) <> "" then se = Num(o.last_season)
                if AsStr(o.last_episode) <> "" then ep = Num(o.last_episode)
                id = AsStr(o.title_id)
                u.progress[id] = { season: se, episode: ep, watched: w, positions: ps, updated: AsStr(o.updated_at) }
                u.order.push(id)
            end if
        end for
    end if
    m.global.user = u
end sub

sub ToggleList(id as String)
    u = m.global.user
    if not u.signed then return
    on1 = false
    for each x in u.myList
        if x = id then on1 = true
    end for
    nl = []
    if on1 then
        for each x in u.myList
            if x <> id then nl.push(x)
        end for
    else
        nl.push(id)
        for each x in u.myList
            nl.push(x)
        end for
    end if
    u.myList = nl
    m.global.user = u
    tk = Token()
    if tk = invalid then return
    if on1 then
        Http("DELETE", "/rest/v1/user_lists?user_id=eq." + u.userId + "&title_id=eq." + Esc(id), invalid, tk, invalid)
    else
        Http("POST", "/rest/v1/user_lists", FormatJson({ user_id: u.userId, title_id: id }), tk, "return=minimal")
    end if
end sub

function Esc(s1 as String) as String
    return CreateObject("roUrlTransfer").escape(s1)
end function

function NowIso() as String
    d = CreateObject("roDateTime")
    return d.toISOString()
end function

sub RecordWatch(id as String, season as Dynamic, episode as Dynamic)
    u = m.global.user
    if not u.signed then return
    prev = u.progress[id]
    w = []
    ps = {}
    if prev <> invalid then
        w = prev.watched
        ps = prev.positions
    end if
    now = NowIso()
    u.progress[id] = { season: season, episode: episode, watched: w, positions: ps, updated: now }
    no = [id]
    for each x in u.order
        if x <> id then no.push(x)
    end for
    u.order = no
    m.global.user = u
    tk = Token()
    if tk = invalid then return
    row = { user_id: u.userId, title_id: id, last_season: season, last_episode: episode, watched: w, updated_at: now }
    Http("POST", "/rest/v1/watch_progress", FormatJson(row), tk, "resolution=merge-duplicates,return=minimal")
end sub

sub SavePos(id as String, season as Dynamic, episode as Dynamic, t as Integer, d as Integer)
    if d <= 0 then return
    u = m.global.user
    if not u.signed then return
    row = u.progress[id]
    if row = invalid then return
    k = PosKey(season, episode)
    done = PosDone(t, d)
    if done then
        row.positions.delete(k)
    else
        row.positions[k] = { t: t, d: d }
    end if
    if done and season <> invalid and episode <> invalid then
        wk = AsStr(season) + "-" + AsStr(episode)
        has = false
        for each x in row.watched
            if x = wk then has = true
        end for
        if not has then row.watched.push(wk)
    end if
    u.progress[id] = row
    m.global.user = u
    tk = Token()
    if tk = invalid then return
    jo = {}
    for each kk in row.positions
        jo[kk] = { t: row.positions[kk].t, d: row.positions[kk].d }
    end for
    body = { user_id: u.userId, title_id: id, positions: jo }
    if done then body.watched = row.watched
    Http("POST", "/rest/v1/watch_progress", FormatJson(body), tk, "resolution=merge-duplicates,return=minimal")
end sub

sub DelProgress(id as String)
    u = m.global.user
    if not u.signed then return
    u.progress.delete(id)
    no = []
    for each x in u.order
        if x <> id then no.push(x)
    end for
    u.order = no
    m.global.user = u
    tk = Token()
    if tk = invalid then return
    Http("DELETE", "/rest/v1/watch_progress?user_id=eq." + u.userId + "&title_id=eq." + Esc(id), invalid, tk, invalid)
end sub

sub TrackView(id as String)
    u = m.global.user
    viewer = "roku-"
    sec = Reg()
    if u.signed then
        viewer = u.userId
    else
        if sec.exists("viewer") then
            viewer = sec.read("viewer")
        else
            viewer = "roku-" + Left(CreateObject("roDeviceInfo").getRandomUUID(), 12)
            sec.write("viewer", viewer)
            sec.flush()
        end if
    end if
    Http("POST", "/rest/v1/title_views", FormatJson({ title_id: id, viewer: viewer }), invalid, "return=minimal")
end sub

' ---------------- sitio cerrado ----------------
sub ApplyClosed(d as Object)
    m.global.closed = Bo(d.closed)
    m.global.closedMsg = AsStr(d.closedMsg)
end sub

sub CheckClosed()
    r = Http("GET", "/rest/v1/site?select=data&id=eq.config", invalid, invalid, invalid)
    if Ok(r) then
        a = Lst(ParseJson(r.text))
        if a.count() > 0 and type(a[0].data) = "roAssociativeArray" then ApplyClosed(a[0].data)
    end if
    if m.global.closed and not m.global.user.isOwner and m.global.user.signed then
        tk = Token()
        if tk <> invalid then
            r = Http("POST", "/rest/v1/rpc/is_admin", "{}", tk, invalid)
            if Ok(r) and r.text.trim() = "true" then
                u = m.global.user
                u.isOwner = true
                m.global.user = u
            end if
        end if
    end if
end sub

' ---------------- catálogo ----------------
sub Catalog(cacheOnly as Boolean)
    cachePath = "cachefs:/catalog.json"
    if cacheOnly then
        txt = ReadAsciiFile(cachePath)
        if txt <> "" then
            j = ParseJson(txt)
            if type(j) = "roAssociativeArray" then
                m.global.cat = BuildCatalog(j)
                m.global.loaded = true
            end if
        end if
        return
    end if
    ' si hay sesión, primero confirma si es la cuenta admin para que nunca le aparezca el cartel de cerrado
    if m.global.user.signed and not m.global.user.isOwner then RefreshUser()
    r1 = Http("GET", "/rest/v1/titles?select=id,data", invalid, invalid, invalid)
    r2 = Http("GET", "/rest/v1/banners?select=id,data", invalid, invalid, invalid)
    r3 = Http("GET", "/rest/v1/site?select=id,data", invalid, invalid, invalid)
    if Ok(r1) and Ok(r2) and Ok(r3) then
        j = { titles: ParseJson(r1.text), banners: ParseJson(r2.text), site: ParseJson(r3.text) }
        if type(j.titles) = "roArray" then
            cat = BuildCatalog(j)
            r4 = Http("POST", "/rest/v1/rpc/top10", "{}", invalid, invalid)
            if Ok(r4) then
                for each o in Lst(ParseJson(r4.text))
                    if type(o) = "roAssociativeArray" then cat.top10.push(AsStr(o.title_id))
                end for
            end if
            m.global.cat = cat
            m.global.offline = false
            m.global.loaded = true
            WriteAsciiFile(cachePath, FormatJson(j))
            return
        end if
    end if
    m.global.offline = true
    m.global.loaded = true
end sub

function BuildCatalog(j as Object) as Object
    use4k = m.global.use4k
    cat = { titles: [], byId: {}, banners: [], rows: [], colls: [], logo: "", top10: [], seed: Rnd(1000000) }
    for each r in Lst(j.titles)
        if type(r) = "roAssociativeArray" and type(r.data) = "roAssociativeArray" then
            d = r.data
            if AsStr(d.id) = "" then d.id = AsStr(r.id)
            t = ParseTitle(d, use4k)
            if t.id <> "" then
                cat.titles.push(t)
                cat.byId[t.id] = t
            end if
        end if
    end for
    bs = []
    for each r in Lst(j.banners)
        if type(r) = "roAssociativeArray" and type(r.data) = "roAssociativeArray" then
            d = r.data
            act = true
            if type(d.active) = "roBoolean" and not d.active then act = false
            if act then bs.push({ titleId: AsStr(d.titleId), image: AsStr(d.image), headline: AsStr(d.headline), text: AsStr(d.text), order: Num(d.order) })
        end if
    end for
    if bs.count() > 1 then bs.sortBy("order")
    cat.banners = Shuffle(bs, cat.seed)
    for each r in Lst(j.site)
        if type(r) = "roAssociativeArray" and type(r.data) = "roAssociativeArray" then
            d = r.data
            id = AsStr(r.id)
            if id = "config" then
                cat.logo = AsStr(d.logo)
                ApplyClosed(d)
            else if id = "collections" then
                for each co in Lst(d.list)
                    if type(co) = "roAssociativeArray" then cat.colls.push({ id: AsStr(co.id), name: AsStr(co.name), tagline: AsStr(co.tagline).trim(), items: StrList(co.items) })
                end for
            else if id = "rows" then
                for each ro in Lst(d.rows)
                    if type(ro) = "roAssociativeArray" then
                        its = []
                        for each it in Lst(ro.items)
                            if type(it) = "roAssociativeArray" then its.push({ titleId: AsStr(it.titleId), label: AsStr(it.label), image: AsStr(it.image) })
                        end for
                        cat.rows.push({ id: AsStr(ro.id), title: AsStr(ro.title), items: its })
                    end if
                end for
            end if
        end if
    end for
    return cat
end function

' ---------------- activar la tele desde el celular ----------------
function Activate() as Object
    chars = "ABCDEFGHJKLMNPRSTUVWXYZ23456789"
    low = "abcdefghijklmnopqrstuvwxyz0123456789"
    while true
        code = ""
        for i = 1 to 6
            code = code + Mid(chars, Rnd(Len(chars)), 1)
        end for
        secret = ""
        for i = 1 to 40
            secret = secret + Mid(low, Rnd(Len(low)), 1)
        end for
        m.top.status = "Generando código…"
        r = Http("POST", "/rest/v1/rpc/tv_code_create", FormatJson({ p_code: code, p_secret: secret }), invalid, invalid)
        if r.code = 404 or r.code = 400 then return { ok: false, error: "código: error " + r.code.toStr() + " · " + Msg(r) }
        if r.text.trim().replace(Chr(34), "") <> "true" then
            m.top.status = "Sin conexión. Reintentando…"
            sleep(4000)
        else
            started = CreateObject("roDateTime").asSeconds()
            m.top.code = code
            m.top.status = "Esperando que actives desde el celular…"
            while true
                sleep(2000)
                secs = 900 - (CreateObject("roDateTime").asSeconds() - started)
                m.top.left = secs
                if secs <= 0 then exit while
                r = Http("POST", "/rest/v1/rpc/tv_code_status", FormatJson({ p_code: code, p_secret: secret }), invalid, invalid)
                st = r.text.trim().replace(Chr(34), "")
                if st = "vencido" then exit while
                if st = "listo" then
                    m.top.status = "¡Listo! Entrando…"
                    f = Http("POST", "/functions/v1/tv-login", FormatJson({ code: code, secret: secret }), invalid, invalid)
                    if Ok(f) then
                        StoreSession(ParseJson(f.text), invalid)
                        RefreshUser()
                        return { ok: true }
                    end if
                    if f.code = 404 or f.code = 401 or f.code = 403 then return { ok: false, error: "función tv-login: error " + f.code.toStr() + " · " + Msg(f) }
                    m.top.status = "No se pudo entrar. Generando otro código…"
                    sleep(2500)
                    exit while
                end if
            end while
        end if
    end while
    return { ok: false }
end function

' ---------------- subtítulos ----------------
' Roku decide el formato del subtítulo por la extensión del archivo; si el enlace no la tiene, se mira el contenido.
function SubKind(url as String) as String
    p = LCase(url.split("?")[0])
    if Right(p, 4) = ".vtt" then return "vtt"
    if Right(p, 4) = ".srt" then return "srt"
    x = CreateObject("roUrlTransfer")
    x.setUrl(url)
    x.setCertificatesFile("common:/certs/ca-bundle.crt")
    x.initClientCertificates()
    x.addHeader("Range", "bytes=0-63")
    port = CreateObject("roMessagePort")
    x.setMessagePort(port)
    x.asyncGetToString()
    ev = wait(8000, port)
    if type(ev) = "roUrlEvent" then
        if Instr(1, ev.getString(), "WEBVTT") > 0 then return "vtt"
    end if
    return "srt"
end function
