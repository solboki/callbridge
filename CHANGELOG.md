# Changelog

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
