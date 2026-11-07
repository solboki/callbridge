# Changelog

## 1.9.1 (7 November 2026)

Fixes the settings app of 1.9.0, whose main screen could fail to load: the handler of the new "On the phone" selector was attached to the selector instead of its menu.

## 1.9.0 (7 November 2026)

Callbridge notifications are now silent on the phone. They were posted with a high priority, which AppSupport turned into a sound and a banner in Sailfish. A new "On the phone" setting chooses between "Silent" (default), "As discreet as possible" and "Sound and banner".

The permanent "Callbridge is running" notification of the Android relay is removed from the Sailfish events view. It could not be dismissed, since Android marks it as ongoing.

## 1.8.1 (6 November 2026)

Fixes the "Forwarded apps" page, which stayed empty: one of its properties was named after a built-in QML property and hid the whole page.

Calls and SMS reach the watch again. Removing duplicate copies in Sailfish (1.7.0) also cancelled the notification on the Android side, before the watch app had forwarded it. This is now off by default, and when turned on, it waits 20 seconds (`ECHO_DELAY`).

The list of apps can no longer block forwarding if it fails to update.

## 1.8.0 (6 November 2026)

Any native Sailfish app can now send its notifications to the watch. A new "Forwarded apps" page in the settings app lists the apps that have notified since the service started, with a switch for each; their notifications reach the watch with the app's name in front. The same page turns incoming calls, missed calls and SMS on or off. Choices take effect at once.

Android apps are recognised and listed apart, since the watch app already sees them. The list keeps only app names and dates, never notification content.

## 1.7.0 (6 November 2026)

No more duplicates in the Sailfish notification area. AppSupport copies each Callbridge notification into Sailfish, so every SMS and call appeared twice. The service now closes these copies as soon as Sailfish registers them; the watch still receives the notification. Can be turned off with `HIDE_ECHO=0`.

## 1.6.0 (2 November 2026)

The Android relay restarts on its own after a restart of the phone or of AppSupport. Android force-stops its apps when AppSupport stops, and a force-stopped app no longer receives the start-up signal until opened by hand. The Sailfish service now notices when the relay stops answering while Android runs, and opens the Android app silently so that it restarts its relay (at most three attempts per Android start).

Every notification now gets a unique key. oFono reuses the same call path for each call, so successive calls carried the same key, and a companion app could take a new call for one it had already forwarded. The same applied to two identical SMS.

The settings app and the Android app show the last events handled, with the delay between Sailfish and Android. Incoming and ended calls are always written to the service log.

## 1.5.0 (2 November 2026)

The Android app opens on its own screen again. Version 1.4 had added a separate launcher entry (an activity-alias) so that the icon could be hidden; AppSupport cannot open such entries and showed the app info page instead.

Hiding the Android icon now works on the Sailfish side, by marking AppSupport's launcher file with `NoDisplay=true`, or by placing a marked copy in `~/.local/share/applications`. The service re-applies it every minute.

New "Uninstall Android app" button in the settings app, which opens Android's uninstall dialog.

## 1.4.1 (2 November 2026)

The settings app installs the Android app through the same system dialog as the file manager. Calling `apkd-install` directly failed, as it requires the "privileged" group. Clearer error message when the Android app cannot be opened.

## 1.4.0 (2 November 2026)

New native Sailfish settings app: switch the service on and off, install or update the bundled Android app, open it, hide its launcher icon, see whether the Android relay is alive and correctly set up, and send a test notification.

New project icon in the Sailfish OS shape, used by both apps. The Android launcher entry is now called "Callbridge Android", to tell it apart from the settings app.

Texts in English, or in French on a phone set to French, in both apps and on the watch. README in English.

## 1.3.0

First public release. The experimental "telephony" style of version 1.2.0 is removed: Garmin Connect did not use it to choose the icon.

## 1.2.0

The APK file name includes its version number. Trial of a call presentation imitating Android's phone apps, removed in the next version.

## 1.1.0

The caller's name is shown for incoming calls. It is looked up first in the Sailfish address book, then in the contacts of the Android container.

## 1.0.3

The service also recognises SMS and missed calls by the name of the app posting them. Sailfish OS 5 posts SMS without a category, under the name "Messages".

## 1.0.2

The Android app shows whether Android blocks its notifications, and opens the matching setting directly. Each button confirms its action.

## 1.0.1

The Android app targets API 25 instead of 23, as Android 15 refuses to install apps targeting a version below API 24.

## 1.0.0

First version: SMS, missed calls, incoming calls and optional notification categories.
