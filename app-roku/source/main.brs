sub Main(args as Object)
    screen = CreateObject("roSGScreen")
    port = CreateObject("roMessagePort")
    screen.setMessagePort(port)
    g = screen.getGlobalNode()
    g.addFields({ cat: {}, user: { signed: false, isOwner: false, myList: [], progress: {}, order: [] }, closed: false, closedMsg: "", loaded: false, offline: false, use4k: false })
    g.use4k = Is4K()
    scene = screen.createScene("MainScene")
    screen.show()
    scene.observeField("quit", port)
    scene.setFocus(true)
    while true
        msg = wait(0, port)
        if type(msg) = "roSGScreenEvent" then
            if msg.isScreenClosed() then return
        else if type(msg) = "roSGNodeEvent" then
            if msg.getField() = "quit" then return
        end if
    end while
end sub

function Is4K() as Boolean
    di = CreateObject("roDeviceInfo")
    m1 = LCase(di.getVideoMode())
    return Instr(1, m1, "2160") > 0
end function
