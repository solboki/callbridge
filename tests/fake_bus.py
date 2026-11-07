"""Simulates, on a D-Bus session bus, what Sailfish OS emits: SMS and
missed-call notifications (org.freedesktop.Notifications) and oFono call
signals. Used by run-tests.sh."""
import asyncio
import os
from dbus_next.aio import MessageBus
from dbus_next.service import ServiceInterface, method, signal
from dbus_next import Variant, BusType


class Notif(ServiceInterface):
    def __init__(self):
        super().__init__("org.freedesktop.Notifications")
        self.next_id = 100
        self.apps = {}

    @method()
    def Notify(self, app: 's', rid: 'u', icon: 's', summary: 's', body: 's',
               actions: 'as', hints: 'a{sv}', timeout: 'i') -> 'u':
        self.next_id += 1
        self.apps[self.next_id] = app
        return self.next_id

    @method()
    def CloseNotification(self, nid: 'u'):
        # Records which notifications the service closed, for run-tests.sh
        log = os.environ.get("CLOSED_LOG")
        if log:
            with open(log, "a") as fh:
                fh.write("%d %s\n" % (nid, self.apps.get(nid, "?")))


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
    async def notify(app, summ, body, cat, extra=None):
        hints = {"category": Variant("s", cat),
                 "x-nemo-priority": Variant("i", 120)}
        hints.update(extra or {})
        await bus.call(Message(destination="org.freedesktop.Notifications",
            path="/org/freedesktop/Notifications",
            interface="org.freedesktop.Notifications", member="Notify",
            signature="susssasa{sv}i",
            body=[app, 0, "icon", summ, body, ["default", ""], hints, -1]))
    msg = 'Salut, ça va ?\nOn dit "midi" ou "13h" ? (test) [ok] {x}'
    await notify("commhistoryd", 'Jean "JD" Dupont', msg, "x-nemo.messaging.sms")
    await notify("commhistoryd", 'Jean "JD" Dupont', msg, "x-nemo.messaging.sms")
    await notify("commhistoryd", "Marie", "", "x-nemo.call.missed")
    await notify("foo", "Ignore", "x", "x-other")
    await notify("Messages", "Alice Martin", "Essai3", "")
    await notify("Callbridge", "Alice Martin", "Essai3 echo", "")
    await notify("Callbridge Android", "Alice Martin", "Essai3 echo 2", "")
    # Copy as apkd-bridge posts it, under an app label the service does not know
    await notify("Relay", "Alice Martin", "Essai3 echo 3", "chat",
                 {"x-nemo-origin-package": Variant("s", "org.callbridge.relay"),
                  "x-nemo-owner": Variant("s", "apkd-bridge")})
    await notify("Mail", "Bob", "Meeting moved to 3 pm", "x-nemo.email")
    await notify("Weather", "Rain", "Rain expected at 5 pm", "")
    # known incoming call then answered; private call then hung up;
    # outgoing call (must be ignored)
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
