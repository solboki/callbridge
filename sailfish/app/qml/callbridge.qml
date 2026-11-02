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
                        text: t("Forward calls and SMS")
                        description: st.active ? t("Running") : t("Stopped")
                        checked: st.enabled === true
                        automaticCheck: false
                        onClicked: {
                            var want = !checked
                            py.call("backend.set_enabled", [want], function(r) { app.report(r) })
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
