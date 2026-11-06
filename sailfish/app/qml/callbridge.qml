import QtQuick 2.0
import Sailfish.Silica 1.0
import io.thp.pyotherside 1.5

ApplicationWindow {
    id: app

    // Last status returned by backend.status()
    property var st: ({})
    property string message: ""

    // English texts, translated to French when the phone is in French.
    readonly property bool french: Qt.locale().name.indexOf("fr") === 0
    readonly property var fr: ({
        "Sailfish service": "Service Sailfish",
        "Forward calls and SMS": "Transmettre appels et SMS",
        "Running": "En marche",
        "Stopped": "Arrêté",
        "Android relay": "Relais Android",
        "Status": "État",
        "Active": "Actif",
        "Not responding": "Ne répond pas",
        "Not detected": "Non détecté",
        "Notifications allowed": "Notifications autorisées",
        "Contacts access": "Accès aux contacts",
        "Version": "Version",
        "yes": "oui",
        "NO": "NON",
        "Install Android app": "Installer l'application Android",
        "Update Android app": "Mettre à jour l'application Android",
        "Open Android app": "Ouvrir l'application Android",
        "Hide Android app icon": "Masquer l'icône Android",
        "The Android app stays installed and running; only its launcher icon disappears.":
            "L'application Android reste installée et active ; seule son icône disparaît.",
        "Test": "Test",
        "Send test notification": "Envoyer une notification de test",
        "Test from the Callbridge settings app": "Test depuis l'application Callbridge",
        "Refresh": "Actualiser",
        "Choose apps": "Choisir les applications",
        "Forward notifications to the watch": "Transmettre les notifications à la montre",
        "Forwarded apps": "Applications transmises",
        "Calls, SMS and the native apps of your choice": "Appels, SMS et les applications natives de votre choix",
        "Clear the list of apps": "Vider la liste des applications",
        "Clearing the list": "Vidage de la liste",
        "Phone": "Téléphone",
        "Incoming calls": "Appels entrants",
        "Missed calls": "Appels manqués",
        "SMS": "SMS",
        "Native apps": "Applications natives",
        "An app appears here after its first notification, while the service is running. Turn it on to send its notifications to the watch.":
            "Une application apparaît ici après sa première notification, tant que le service fonctionne. Activez-la pour envoyer ses notifications à la montre.",
        "No app yet": "Aucune application pour l'instant",
        "Leave the service running: apps will show up as they send notifications.":
            "Laissez le service fonctionner : les applications apparaîtront à mesure qu'elles notifient.",
        "Android apps": "Applications Android",
        "These apps run in AppSupport: your watch app already sees their notifications. Choose them in the watch app itself.":
            "Ces applications tournent dans AppSupport : l'application de la montre voit déjà leurs notifications. Choisissez-les dans l'application de la montre elle-même.",
        "Last notification: ": "Dernière notification : ",
        "Not seen since the list was cleared": "Pas vue depuis le vidage de la liste",
        "Test sent: it should reach your watch within a few seconds.":
            "Test envoyé : il doit arriver sur la montre en quelques secondes.",
        "Confirm the installation in the dialog, then open the Android app to grant its permissions.":
            "Confirmez l'installation dans la fenêtre, puis ouvrez l'application Android pour accorder ses autorisations.",
        "Installation failed: ": "Échec de l'installation : ",
        "The Android app does not seem to be installed.":
            "L'application Android ne semble pas installée.",
        "Opening the Android app…": "Ouverture de l'application Android…",
        "Could not open the Android app: ": "Impossible d'ouvrir l'application Android : ",
        "Done. If the icon does not change within a few seconds, restart the home screen or the phone.":
            "C'est fait. Si l'icône ne change pas d'ici quelques secondes, redémarrez l'écran d'accueil ou le téléphone.",
        "Could not change the icon: ": "Impossible de modifier l'icône : ",
        "Uninstall Android app": "Désinstaller l'application Android",
        "Recent events (delay):": "Derniers événements (délai) :",
        "Uninstalling Android app": "Désinstallation de l'application Android",
        "Confirm the uninstallation in the Android dialog.":
            "Confirmez la désinstallation dans la fenêtre d'Android.",
        "Android blocks the notifications of the Callbridge app: open it and allow them, or nothing will reach your watch.":
            "Android bloque les notifications de l'application Callbridge : ouvrez-la et autorisez-les, sinon rien n'arrivera sur la montre.",
        "Vibe-coded with Claude Opus 5.5": "Réalisé en vibe coding avec Claude Opus 5.5"
    })
    function t(s) { return (french && fr[s] !== undefined) ? fr[s] : s }
    function yesNo(b) { return b ? t("yes") : t("NO") }

    property bool relayAlive: st.relay !== undefined && st.relay.alive === true

    function refresh() {
        py.call("backend.status", [], function(r) { st = r })
    }

    function report(r, okText) {
        message = r.ok ? (okText || "") : r.message
        refresh()
    }

    Python {
        id: py
        Component.onCompleted: {
            addImportPath(Qt.resolvedUrl("."))
            importModule("backend", function() { app.refresh() })
        }
        onError: app.message = traceback
    }

    Timer {
        interval: 5000
        repeat: true
        running: Qt.application.active
        triggeredOnStart: true
        onTriggered: app.refresh()
    }

    initialPage: Component {
        Page {
            SilicaFlickable {
                anchors.fill: parent
                contentHeight: column.height + Theme.paddingLarge

                PullDownMenu {
                    MenuItem {
                        text: t("Choose apps")
                        onClicked: pageStack.push(appsPage)
                    }
                    MenuItem {
                        text: t("Refresh")
                        onClicked: app.refresh()
                    }
                }

                Column {
                    id: column
                    width: parent.width

                    PageHeader {
                        title: "Callbridge"
                        description: t("Vibe-coded with Claude Opus 5.5")
                    }

                    SectionHeader { text: t("Sailfish service") }

                    TextSwitch {
                        id: serviceSwitch
                        text: t("Forward notifications to the watch")
                        description: st.active ? t("Running") : t("Stopped")
                        checked: st.enabled === true
                        automaticCheck: false
                        onClicked: {
                            var want = !checked
                            py.call("backend.set_enabled", [want], function(r) { app.report(r) })
                        }
                    }

                    BackgroundItem {
                        width: parent.width
                        height: Theme.itemSizeMedium
                        onClicked: pageStack.push(appsPage)
                        Column {
                            x: Theme.horizontalPageMargin
                            width: parent.width - 2 * x - forwardIcon.width
                            anchors.verticalCenter: parent.verticalCenter
                            Label {
                                text: t("Forwarded apps")
                                color: parent.parent.highlighted ? Theme.highlightColor : Theme.primaryColor
                            }
                            Label {
                                width: parent.width
                                truncationMode: TruncationMode.Fade
                                font.pixelSize: Theme.fontSizeExtraSmall
                                color: Theme.secondaryColor
                                text: t("Calls, SMS and the native apps of your choice")
                            }
                        }
                        Icon {
                            id: forwardIcon
                            anchors.right: parent.right
                            anchors.rightMargin: Theme.horizontalPageMargin
                            anchors.verticalCenter: parent.verticalCenter
                            source: "image://theme/icon-m-right"
                        }
                    }

                    SectionHeader { text: t("Android relay") }

                    DetailItem {
                        label: t("Status")
                        value: !st.relay || !st.relay.known ? t("Not detected")
                             : (app.relayAlive ? t("Active") : t("Not responding"))
                    }
                    DetailItem {
                        visible: app.relayAlive
                        label: t("Notifications allowed")
                        value: st.relay ? yesNo(st.relay.notifications) : ""
                    }
                    DetailItem {
                        visible: app.relayAlive
                        label: t("Contacts access")
                        value: st.relay ? yesNo(st.relay.contacts) : ""
                    }
                    DetailItem {
                        visible: st.relay !== undefined && st.relay.known === true
                        label: t("Version")
                        value: st.relay && st.relay.version ? st.relay.version : ""
                    }

                    Label {
                        visible: st.relay !== undefined && st.relay.recent !== undefined
                                 && st.relay.recent.length > 0
                        x: Theme.horizontalPageMargin
                        width: parent.width - 2 * x
                        color: Theme.secondaryHighlightColor
                        font.pixelSize: Theme.fontSizeExtraSmall
                        wrapMode: Text.Wrap
                        text: visible ? t("Recent events (delay):") + "\n"
                                        + st.relay.recent.join("\n") : ""
                    }

                    Label {
                        visible: app.relayAlive && st.relay.notifications === false
                        x: Theme.horizontalPageMargin
                        width: parent.width - 2 * x
                        wrapMode: Text.Wrap
                        color: Theme.errorColor
                        font.pixelSize: Theme.fontSizeSmall
                        text: t("Android blocks the notifications of the Callbridge app: open it and allow them, or nothing will reach your watch.")
                    }

                    Item { width: 1; height: Theme.paddingMedium }

                    ButtonLayout {
                        Button {
                            enabled: st.apk !== undefined && st.apk !== ""
                            text: (st.relay && st.relay.known ? t("Update Android app")
                                                              : t("Install Android app"))
                                  + (st.apkVersion ? " " + st.apkVersion : "")
                            onClicked: {
                                // Same route as tapping the APK in the file manager:
                                // Sailfish shows its own confirmation dialog. Installing
                                // directly (apkd-install) needs privileges this app lacks.
                                if (Qt.openUrlExternally("file://" + st.apk)) {
                                    app.message = t("Confirm the installation in the dialog, then open the Android app to grant its permissions.")
                                } else {
                                    py.call("backend.install_apk", [], function(r) {
                                        app.message = r.ok
                                            ? t("Confirm the installation in the dialog, then open the Android app to grant its permissions.")
                                            : t("Installation failed: ") + r.message
                                    })
                                }
                            }
                        }
                        Button {
                            text: t("Open Android app")
                            onClicked: {
                                app.message = t("Opening the Android app…")
                                py.call("backend.open_android", [], function(r) {
                                    if (r.ok) app.message = ""
                                    else if (r.message === "not-installed")
                                        app.message = t("The Android app does not seem to be installed.")
                                    else
                                        app.message = t("Could not open the Android app: ") + r.message
                                    app.refresh()
                                })
                            }
                        }
                    }

                    TextSwitch {
                        enabled: st.androidLauncher !== undefined && st.androidLauncher !== ""
                        text: t("Hide Android app icon")
                        description: t("The Android app stays installed and running; only its launcher icon disappears.")
                        checked: st.iconHidden === true
                        automaticCheck: false
                        onClicked: py.call("backend.set_icon_hidden", [!checked], function(r) {
                            if (r.ok)
                                app.message = t("Done. If the icon does not change within a few seconds, restart the home screen or the phone.")
                            else if (r.message === "not-installed")
                                app.message = t("The Android app does not seem to be installed.")
                            else
                                app.message = t("Could not change the icon: ") + r.message
                            app.refresh()
                        })
                    }

                    Button {
                        anchors.horizontalCenter: parent.horizontalCenter
                        enabled: st.androidLauncher !== undefined && st.androidLauncher !== ""
                        text: t("Uninstall Android app")
                        onClicked: remorse.execute(t("Uninstalling Android app"), function() {
                            app.message = t("Opening the Android app…")
                            py.call("backend.uninstall_android", [], function(r) {
                                app.message = r.ok
                                    ? t("Confirm the uninstallation in the Android dialog.")
                                    : t("Could not open the Android app: ") + r.message
                                app.refresh()
                            })
                        })
                    }

                    RemorsePopup { id: remorse }

                    Item { width: 1; height: Theme.paddingMedium }

                    SectionHeader { text: t("Test") }

                    Button {
                        anchors.horizontalCenter: parent.horizontalCenter
                        text: t("Send test notification")
                        onClicked: py.call("backend.send_test",
                                           [t("Test from the Callbridge settings app")],
                                           function(r) {
                            app.message = t("Test sent: it should reach your watch within a few seconds.")
                        })
                    }

                    Item { width: 1; height: Theme.paddingLarge; visible: app.message !== "" }

                    Label {
                        visible: app.message !== ""
                        x: Theme.horizontalPageMargin
                        width: parent.width - 2 * x
                        wrapMode: Text.Wrap
                        color: Theme.highlightColor
                        font.pixelSize: Theme.fontSizeSmall
                        text: app.message
                    }
                }

                VerticalScrollDecorator {}
            }
        }
    }

    Component {
        id: appsPage
        Page {
            id: page
            property var info: ({ phone: {}, native: [], android: [] })
            function load() { py.call("backend.apps", [], function(r) { page.info = r }) }
            Component.onCompleted: load()
            onStatusChanged: if (status === PageStatus.Active) load()

            function lastSeen(ts) {
                if (!ts) return t("Not seen since the list was cleared")
                return t("Last notification: ") + Qt.formatDateTime(new Date(ts * 1000), "d MMM, hh:mm")
            }

            RemorsePopup { id: clearRemorse }

            SilicaFlickable {
                anchors.fill: parent
                contentHeight: appsColumn.height + Theme.paddingLarge

                PullDownMenu {
                    MenuItem {
                        text: t("Clear the list of apps")
                        onClicked: clearRemorse.execute(t("Clearing the list"), function() {
                            py.call("backend.forget_seen", [], function() { page.load() })
                        })
                    }
                    MenuItem {
                        text: t("Refresh")
                        onClicked: page.load()
                    }
                }

                Column {
                    id: appsColumn
                    width: parent.width

                    PageHeader { title: t("Forwarded apps") }

                    SectionHeader { text: t("Phone") }

                    Repeater {
                        model: [
                            { key: "calls",  label: t("Incoming calls") },
                            { key: "missed", label: t("Missed calls") },
                            { key: "sms",    label: t("SMS") }
                        ]
                        TextSwitch {
                            text: modelData.label
                            checked: page.info.phone[modelData.key] !== false
                            automaticCheck: false
                            onClicked: py.call("backend.set_phone", [modelData.key, !checked],
                                               function() { page.load() })
                        }
                    }

                    SectionHeader { text: t("Native apps") }

                    Label {
                        x: Theme.horizontalPageMargin
                        width: parent.width - 2 * x
                        wrapMode: Text.Wrap
                        font.pixelSize: Theme.fontSizeExtraSmall
                        color: Theme.secondaryHighlightColor
                        text: t("An app appears here after its first notification, while the service is running. Turn it on to send its notifications to the watch.")
                    }

                    Item { width: 1; height: Theme.paddingMedium }

                    ViewPlaceholder {
                        enabled: page.info.native.length === 0
                        text: t("No app yet")
                        hintText: t("Leave the service running: apps will show up as they send notifications.")
                    }

                    Repeater {
                        model: page.info.native
                        TextSwitch {
                            text: modelData.name
                            description: page.lastSeen(modelData.last)
                            checked: modelData.enabled
                            automaticCheck: false
                            onClicked: py.call("backend.set_app", [modelData.name, !checked],
                                               function() { page.load() })
                        }
                    }

                    SectionHeader {
                        visible: page.info.android.length > 0
                        text: t("Android apps")
                    }

                    Label {
                        visible: page.info.android.length > 0
                        x: Theme.horizontalPageMargin
                        width: parent.width - 2 * x
                        wrapMode: Text.Wrap
                        font.pixelSize: Theme.fontSizeExtraSmall
                        color: Theme.secondaryHighlightColor
                        text: t("These apps run in AppSupport: your watch app already sees their notifications. Choose them in the watch app itself.")
                    }

                    Repeater {
                        model: page.info.android
                        Label {
                            x: Theme.horizontalPageMargin
                            width: parent.width - 2 * x
                            height: implicitHeight + Theme.paddingSmall
                            verticalAlignment: Text.AlignBottom
                            truncationMode: TruncationMode.Fade
                            color: Theme.secondaryColor
                            text: modelData.name
                        }
                    }
                }

                VerticalScrollDecorator {}
            }
        }
    }

    cover: Component {
        CoverBackground {
            Image {
                anchors.centerIn: parent
                anchors.verticalCenterOffset: -Theme.paddingLarge * 2
                width: parent.width * 0.5
                height: width
                source: "/usr/share/icons/hicolor/172x172/apps/callbridge.png"
                sourceSize.width: width
                sourceSize.height: height
            }
            Label {
                anchors.horizontalCenter: parent.horizontalCenter
                anchors.bottom: parent.bottom
                anchors.bottomMargin: Theme.paddingLarge * 2
                text: st.active ? t("Running") : t("Stopped")
                color: Theme.highlightColor
            }
            CoverActionList {
                CoverAction {
                    iconSource: "image://theme/icon-cover-refresh"
                    onTriggered: app.refresh()
                }
            }
        }
    }
}
