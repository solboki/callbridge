"""Simule, sur un bus D-Bus de session, ce que Sailfish OS émet :
notifications de SMS et d'appels manqués (org.freedesktop.Notifications)
et signaux d'appel d'oFono. Utilisé par run-tests.sh."""
import asyncio
from dbus_next.aio import MessageBus
from dbus_next.service import ServiceInterface, method, signal
from dbus_next import Variant, BusType


class Notif(ServiceInterface):
    def __init__(self):
        super().__init__("org.freedesktop.Notifications")

    @method()
    def Notify(self, app: 's', rid: 'u', icon: 's', summary: 's', body: 's',
               actions: 'as', hints: 'a{sv}', timeout: 'i') -> 'u':
        return 1


class Mgr(ServiceInterface):
    def __init__(self):
        super().__init__("org.ofono.VoiceCallManager")

    @signal()
    def CallAdded(self, path, props) -> 'oa{sv}':
        return [path, props]

    @signal()
    def CallRemoved(self, path) -> 'o':
        return path


class Call(ServiceInterface):
    def __init__(self):
        super().__init__("org.ofono.VoiceCall")

    @signal()
    def PropertyChanged(self, name, val) -> 'sv':
        return [name, val]


async def main():
    bus = await MessageBus(bus_type=BusType.SESSION).connect()
    bus.export("/org/freedesktop/Notifications", Notif())
    await bus.request_name("org.freedesktop.Notifications")
    mgr, call = Mgr(), Call()
    bus.export("/ril_0", mgr)
    bus.export("/ril_0/voicecall01", call)
    await asyncio.sleep(2.5)
    from dbus_next import Message
    async def notify(app, summ, body, cat):
        await bus.call(Message(destination="org.freedesktop.Notifications",
            path="/org/freedesktop/Notifications",
            interface="org.freedesktop.Notifications", member="Notify",
            signature="susssasa{sv}i",
            body=[app, 0, "icon", summ, body, ["default", ""],
                  {"category": Variant("s", cat),
                   "x-nemo-priority": Variant("i", 120)}, -1]))
    msg = 'Salut, ça va ?\nOn dit "midi" ou "13h" ? (test) [ok] {x}'
    await notify("commhistoryd", 'Jean "JD" Dupont', msg, "x-nemo.messaging.sms")
    await notify("commhistoryd", 'Jean "JD" Dupont', msg, "x-nemo.messaging.sms")
    await notify("commhistoryd", "Marie", "", "x-nemo.call.missed")
    await notify("foo", "Ignore", "x", "x-other")
    await notify("Messages", "Alice Martin", "Essai3", "")
    await notify("Callbridge", "Alice Martin", "Essai3 echo", "")
    # appel entrant connu puis décroché ; appel masqué puis raccroché ;
    # appel sortant (doit être ignoré)
    mgr.CallAdded("/ril_0/voicecall01", {
        "LineIdentification": Variant("s", "+33612345678"),
        "Name": Variant("s", ""), "State": Variant("s", "incoming"),
        "Emergency": Variant("b", False)})
    await asyncio.sleep(0.5)
    call.PropertyChanged("State", Variant("s", "active"))
    await asyncio.sleep(0.5)
    mgr.CallAdded("/ril_0/voicecall02", {
        "LineIdentification": Variant("s", "withheld"),
        "State": Variant("s", "incoming")})
    await asyncio.sleep(0.5)
    mgr.CallRemoved("/ril_0/voicecall02")
    mgr.CallAdded("/ril_0/voicecall03", {
        "LineIdentification": Variant("s", "0611"),
        "State": Variant("s", "dialing")})
    await asyncio.sleep(6)

asyncio.run(main())
