<p align="center"><img src="icons/callbridge-172.png" width="120" alt="Callbridge icon"></p>

# Callbridge

Callbridge brings Sailfish OS phone calls and text messages to your smartwatch, when the watch relies on an Android companion app such as Garmin Connect.

> **Vibe-coded project.** Callbridge was written by the AI model Claude Opus 5.5 (Anthropic), in a conversation with its author, who defined the need, tested every version on his own phone and reported what went wrong. The code has not been reviewed line by line as professional software would be. It is published as is, without any warranty, in the hope that it will be useful. Feedback and fixes are welcome.

## The problem it solves

On a Sailfish OS phone, Android apps run in a separate compartment called AppSupport. Garmin Connect works well there, but it only sees notifications created inside that compartment. Calls and SMS, however, are handled by the native Sailfish apps, on the other side of the wall. Garmin Connect never sees them, so the watch stays silent when the phone rings.

Callbridge carries the messages across the wall. It does not talk to the watch and does not touch its pairing. It copies native calls and SMS as Android notifications, which Garmin Connect then forwards as usual.

In other words, the watch keeps talking to Garmin Connect exactly as before. Callbridge simply gives Garmin Connect something to say.

## How it works

Callbridge is made of three parts.

```
 Sailfish OS                                  Android container (AppSupport)
┌───────────────────────────┐                ┌──────────────────────────────┐
│ SMS, missed calls         │                │                              │
│ incoming calls (oFono)    │                │  Callbridge Android app      │
│          │                │   JSON file    │          │                   │
│          ▼                │  ────────────► │          ▼                   │
│  callbridge service       │  ~/android_    │  Android notification        │
│                           │  storage/      │          │                   │
│  Callbridge settings app  │  Callbridge    │          ▼                   │
│  (on/off, install, icon)  │                │  Garmin Connect ──► watch    │
└───────────────────────────┘                └──────────────────────────────┘
```

The first part is a small background service on Sailfish. It listens to the system message bus (D-Bus, the channel apps use to talk to each other). There it spots SMS and missed-call notifications, as well as incoming calls announced by oFono, the part of the system that handles telephony.

For each event, the service writes a small text file in JSON format (a structured data format) into the folder `~/android_storage/Callbridge`. Android sees this shared folder as `/sdcard/Callbridge`.

The second part is a small Android app. It watches that folder, turns each file into an Android notification, then deletes it.

The third part is a native Sailfish settings app, called Callbridge, which lets you control everything without a terminal. None of the three parts needs root access.

## What reaches the watch

**Text messages** arrive with the sender's name and the message text.

**Incoming calls** arrive while the phone rings. If the number is in your contacts, the watch shows the contact's name, then "Incoming call" and the number. Otherwise it shows the number alone, or "Private number". The notification disappears from the watch as soon as you answer or the call ends.

**Missed calls** arrive as Sailfish shows them in its notification area.

**Other native apps** can be added in the settings app, under "Forwarded apps" (see below). Their notifications reach the watch with the app's name in front, for example "Mail · Bob".

Texts shown on the watch follow the phone's language: English, or French on a phone set to French.

## Requirements

You need a phone running Sailfish OS with Android AppSupport. Callbridge was developed on a Jolla phone running Sailfish OS 5, with an Android 15 container and a Garmin watch. It should work on other recent versions, but this has not been checked.

You also need your watch's companion app, installed in AppSupport and already paired. Callbridge was tested with Garmin Connect. Nothing in it is specific to Garmin: any companion app that forwards Android notifications should work.

Finally, developer mode must be enabled (Settings, then Developer tools), and so must the installation of untrusted software.

## Installation

Download the latest Sailfish package, `callbridge-X.Y.Z-1.noarch.rpm`, from the [Releases page](https://github.com/solboki/callbridge/releases/latest). X.Y.Z stands for the version number. The Android app is included in this package.

**1. Install the Sailfish package.** In the terminal:

```
devel-su pkcon install-local callbridge-X.Y.Z-1.noarch.rpm
```

Tapping the file in the file manager works too. The service starts on its own, and a Callbridge icon appears among your apps.

**2. Install the Android app.** Open the Callbridge settings app and tap "Install Android app". Sailfish asks you to confirm the installation. The Android app is also available as a separate file, `Callbridge-X.Y.Z.apk`, on the "Releases" page.

**3. Set up the Android app.** In the Callbridge settings app, tap "Open Android app". In the Android app, tap "Grant permissions". Android asks for access to storage, to read the shared folder, and to contacts, to find callers' names.

Then check the line "Notifications allowed". If it says "NO", tap "Notification settings" and enable them. This is the most important setting: on Android 13 and later, an app whose notifications are blocked cannot forward anything, and Android does not warn you.

Finally, tap "Don't optimise battery" and exempt Callbridge, so that Android does not put it to sleep.

**4. Set up Garmin Connect.** In Garmin Connect's notification settings, allow the "Callbridge" app.

**5. Check.** Back in the Callbridge settings app, the "Android relay" section should show "Active" and "Notifications allowed: yes". Tap "Send test notification": it should reach your watch within a few seconds. Then send yourself a text message from another phone.

**6. Hide the Android icon (optional).** Once everything works, you can turn on "Hide Android app icon" in the settings app. The Android app keeps running; only its "Callbridge Android" icon disappears from the launcher. "Open Android app" in the settings app still opens it.

## The settings app

The Callbridge settings app gathers everything in one screen.

The **Sailfish service** switch turns forwarding on or off. When it is off, the service stays stopped, even after a reboot, until you turn it back on.

The **Android relay** section shows whether the Android app is running and correctly set up. It reads a small status file that the Android app updates every 30 seconds. "Not responding" means the Android app has stopped, or AppSupport is not running. The buttons below install or update the bundled Android app, open it, hide its icon and uninstall it.

Hiding the icon works on the Sailfish side. AppSupport creates a launcher file (a `.desktop` file) for each Android app. Callbridge adds the standard line `NoDisplay=true` to it, which tells the home screen not to show it. If that file cannot be edited by the user, Callbridge places a marked copy in `~/.local/share/applications` instead. As AppSupport may recreate its launcher files, the service re-applies the setting every minute while it is on.

Uninstalling goes through Android's own dialog. Android only lets an open app ask for an uninstall, so the settings app opens the Android app, which immediately shows the uninstall dialog for you to confirm.

The **Test** button sends a notification through the whole chain, from Sailfish to the watch.

The **Forwarded apps** page, opened from the main screen, chooses what reaches the watch. Its "Phone" section turns incoming calls, missed calls and SMS on or off. Its "Native apps" section lists the Sailfish apps that have sent at least one notification since the service started, with a switch for each. An app therefore appears only after its first notification: leave the service running, and the list fills up on its own. Only the app's name, the time of its last notification and how often it notifies are kept, never the content of notifications.

Android apps are listed apart, without a switch: they run in AppSupport, so your watch app already sees their notifications, and you choose them in the watch app itself. Forwarding them again would only create duplicates.

The choices are saved in `~/.config/callbridge/apps.json` and take effect at once, without restarting the service.

## After a restart

When AppSupport stops, Android force-stops all its apps. An app stopped this way no longer receives Android's start-up signal until it is opened by hand. Without help, the relay would stay silent after every restart of the phone or of AppSupport.

The Sailfish service therefore watches the status file of the Android relay. If Android is running but the relay has stopped answering, it opens the Android app with an "autostart" mark: the app starts its relay and closes at once. You may see a window flash briefly. The service tries at most three times per Android start, five minutes apart, so that a missing permission cannot cause endless attempts. This can be turned off with `AUTOSTART_ANDROID=0` in the settings file.

## Duplicates in the notification area

AppSupport copies every Android notification into the Sailfish notification area. Each SMS or call forwarded by Callbridge therefore appears there twice: once from the native app, once from Callbridge.

Callbridge can remove these copies (`HIDE_ECHO=1` in the settings file), but this is off by default. AppSupport passes the removal on to Android, which cancels the original notification. Removed at once, the notification never reached the watch. The service therefore waits `ECHO_DELAY` seconds (20 by default) before removing the copy. Even then, some watch apps also remove the notification from the watch when it is cancelled on the phone. Try it, and turn it back off if notifications vanish from your watch.

## Advanced settings

Advanced settings live in a text file. To create it, copy the bundled template:

```
mkdir -p ~/.config/callbridge
cp /usr/share/callbridge/callbridge.conf.example ~/.config/callbridge/callbridge.conf
```

Edit it, then restart the service with `systemctl --user restart callbridge`, or by turning the switch off and on in the settings app. Each setting is explained in the file.

The first three settings turn each kind of event on or off: SMS, missed calls and incoming calls.

The `EXTRA_CATEGORIES` and `EXTRA_APPS` settings add other native notifications without going through the settings app, which is easier for most people. For instance, `EXTRA_CATEGORIES=x-nemo.email` forwards e-mails from the Sailfish Mail app. To find the exact name of a notification, stop the service, run it by hand in verbose mode, then trigger the notification you want:

```
systemctl --user stop callbridge
callbridge-daemon --debug
```

Each notification shows up as a line `Notify app=… cat=…`. The `cat` value goes into `EXTRA_CATEGORIES`, the `app` value into `EXTRA_APPS`. Then restart the service with `systemctl --user start callbridge`.

The `SMS_APPS` and `MISSED_CALL_APPS` settings are used when Sailfish posts a notification without a category. This is the case for SMS on Sailfish OS 5, posted under the name "Messages". On a phone set to another language, this name may differ: verbose mode will tell you.

The `LANGUAGE` setting forces the language of the texts shown on the watch (`en` or `fr`). Left empty, it follows the phone.

## Troubleshooting

The service log can be read with:

```
journalctl --user -u callbridge -f
```

The easiest way is to follow a message step by step.

**Nothing appears in `~/android_storage/Callbridge` when an SMS arrives.** The Sailfish service does not spot the notification. Run `callbridge-daemon --debug` as shown above and check that the SMS shows up. If it shows up under an unknown app name, add that name to `SMS_APPS`.

**Files pile up in that folder without being picked up.** The Android app does not read them. Open it: the line "Readable" should say "yes" and the line "Relay" should say "running". Otherwise, grant the permissions and tap "Restart relay".

**The Android app's "Last event" line changes, but the watch receives nothing.** The message crossed the wall, but Android or Garmin Connect blocks it. Check "Notifications allowed", then the permission given to Callbridge in Garmin Connect.

**A call or a message did not reach the watch.** Open the settings app: the "Recent events" list shows each event the Android relay handled, with the delay between Sailfish and Android. If the event is listed, the message reached Android, and the issue lies with Garmin Connect or the watch. If it is missing, check the service log: each incoming call leaves a line starting with `oFono:`.

**SMS arrive, but not incoming calls.** During a call, run:

```
dbus-monitor --system "type='signal',interface='org.ofono.VoiceCallManager'"
```

If this command shows nothing, your Sailfish version restricts call signals to privileged accounts. Missed calls are still forwarded in any case.

**To test the Android app alone**, without the Sailfish service:

```
devel-su appsupport-attach am broadcast -a org.callbridge.relay.POST -n org.callbridge.relay/.CommandReceiver -e title "Test" -e text "Hello"
```

## Known limitations

You see the call on the watch, but you cannot answer or decline it from your wrist.

The watch shows a message icon, even for a call. Garmin Connect picks the icon itself, following rules it does not document and which seem to rely on a fixed list of known apps. A presentation imitating Android's own phone apps was tried without success, then removed.

Hiding the Android icon relies on the Sailfish home screen honouring `NoDisplay=true`. When the launcher file cannot be edited, it also relies on the home screen reading `~/.local/share/applications`. This has not yet been confirmed on every Sailfish version.

The name under which Sailfish posts missed calls without a category has only been partly checked. If missed calls do not arrive, verbose mode will tell you which name to add to `MISSED_CALL_APPS`.

## Uninstalling

Uninstall the Android app first, with the "Uninstall Android app" button of the settings app, then the Sailfish package:

```
devel-su pkcon remove callbridge
```

Finally, delete the folder `~/android_storage/Callbridge` and `~/.config/callbridge`.

## Building from source

Building is done on a Debian or Ubuntu computer, without Android Studio or Gradle:

```
sudo apt install openjdk-17-jdk aapt dalvik-exchange zipalign apksigner \
    android-sdk-platform-23 rpm python3-pil
./make-dist.sh
```

The script produces in `dist/` the APK and the RPM, both numbered, plus a source archive.

The APK is signed with a key created on the first run in `android/callbridge.keystore`. This key is deliberately not published in the repository. Keep it safe: Android refuses to update an app with an APK signed by a different key, and the old version must then be uninstalled first.

To release a new version, change the version number in four places: `V=` in `make-dist.sh`, `Version:` in `rpm/callbridge.spec`, `VERSION` in `sailfish/callbridge-daemon`, and `versionCode` and `versionName` in `android/AndroidManifest.xml`. Add an entry to `CHANGELOG.md` and to the `%changelog` of the `.spec` file.

## Tests

The Sailfish service can be tested on a Linux computer, without a phone. The test script starts a temporary D-Bus bus, simulates SMS, missed calls and incoming calls on it, then checks the files produced:

```
pip install dbus-next
./tests/run-tests.sh
```

## Source layout

| Folder | Content |
|---|---|
| `sailfish/` | The service (Python, needing only `python3` and `dbus-monitor`), its systemd unit and the settings template. |
| `sailfish/app/` | The settings app: Silica interface (QML) and Python backend (PyOtherSide). |
| `android/` | The Android app, in Java, without external libraries, and its build script. |
| `icons/` | The project icon in the Sailfish OS shape, in every size used. |
| `rpm/` | The Sailfish package description. |
| `tests/` | Tests of the service on a simulated D-Bus bus. |

## Related projects

[Vapaamin](https://forum.sailfishos.org/t/vapaamin-garmin-notifications-bridge/33097) takes another route: it replaces Garmin Connect and talks to the watch directly, which lets it forward every native notification. Callbridge keeps Garmin Connect and its features, such as activity sync. [Amazfish](https://github.com/piggz/harbour-amazfish) plays the same role for other watch brands.

## License

Callbridge is free software, released under the GNU GPL version 3 or later. The full text is in the `LICENSE` file.
