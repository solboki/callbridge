Name:           callbridge
Version:        1.8.1
Release:        1
Summary:        Forwards native Sailfish OS calls and SMS to Android AppSupport
License:        GPL-3.0-or-later
URL:            https://github.com/solboki/callbridge
Source0:        %{name}-%{version}.tar.gz
BuildArch:      noarch
AutoReqProv:    no
Requires:       /usr/bin/python3
Requires:       /usr/bin/dbus-monitor
Requires:       /usr/bin/sailfish-qml
Requires:       sailfishsilica-qt5
Requires:       pyotherside-qml-plugin-python3-qt5
Requires:       systemd

# Payload readable by Sailfish OS rpm (no zstd compression)
%define _binary_payload w9.gzdio
%define _source_payload w9.gzdio
%define _build_id_links none

%description
Callbridge listens to native Sailfish OS notifications (SMS, missed calls)
and to incoming calls reported by oFono, and drops them into a folder shared
with the Android container. The Callbridge Android app, shipped in
/usr/share/callbridge/Callbridge-<version>.apk, re-posts them as Android
notifications, so that Garmin Connect (or any other watch companion app)
can forward them to the wrist.

The package also contains a settings app to switch the service on and off,
install, open and uninstall the Android app, and hide its launcher icon.

Vibe-coded with Claude Opus 5.5 (Anthropic).

%prep
%setup -q

%build

%install
install -D -m 0755 sailfish/callbridge-daemon %{buildroot}%{_bindir}/callbridge-daemon
install -D -m 0644 sailfish/callbridge.service %{buildroot}%{_prefix}/lib/systemd/user/callbridge.service
mkdir -p %{buildroot}%{_prefix}/lib/systemd/user/user-session.target.wants
ln -s ../callbridge.service %{buildroot}%{_prefix}/lib/systemd/user/user-session.target.wants/callbridge.service
install -D -m 0644 sailfish/callbridge.conf.example %{buildroot}%{_datadir}/callbridge/callbridge.conf.example
install -D -m 0644 android/Callbridge.apk %{buildroot}%{_datadir}/callbridge/Callbridge-%{version}.apk
install -D -m 0644 README.md %{buildroot}%{_datadir}/callbridge/README.md
# Settings app
install -D -m 0644 sailfish/app/qml/callbridge.qml %{buildroot}%{_datadir}/callbridge/qml/callbridge.qml
install -D -m 0644 sailfish/app/qml/backend.py %{buildroot}%{_datadir}/callbridge/qml/backend.py
install -D -m 0644 sailfish/app/callbridge.desktop %{buildroot}%{_datadir}/applications/callbridge.desktop
for s in 86 108 128 172; do
    install -D -m 0644 icons/callbridge-$s.png %{buildroot}%{_datadir}/icons/hicolor/${s}x${s}/apps/callbridge.png
done

%post
if [ -x /usr/bin/systemctl-user ]; then
    systemctl-user daemon-reload >/dev/null 2>&1 || :
    systemctl-user try-restart callbridge.service >/dev/null 2>&1 || :
    systemctl-user start callbridge.service >/dev/null 2>&1 || :
fi
exit 0

%preun
if [ "$1" -eq 0 ] && [ -x /usr/bin/systemctl-user ]; then
    systemctl-user stop callbridge.service >/dev/null 2>&1 || :
fi
exit 0

%postun
if [ -x /usr/bin/systemctl-user ]; then
    systemctl-user daemon-reload >/dev/null 2>&1 || :
fi
exit 0

%files
%{_bindir}/callbridge-daemon
%{_prefix}/lib/systemd/user/callbridge.service
%{_prefix}/lib/systemd/user/user-session.target.wants/callbridge.service
%dir %{_datadir}/callbridge
%{_datadir}/callbridge/callbridge.conf.example
%{_datadir}/callbridge/Callbridge-%{version}.apk
%{_datadir}/callbridge/README.md
%{_datadir}/callbridge/qml
%{_datadir}/applications/callbridge.desktop
%{_datadir}/icons/hicolor/*/apps/callbridge.png

%changelog
* Fri Nov 06 2026 Boris Solodki <solboki@solodki.fr> - 1.8.1-1
- Fix empty "Forwarded apps" page.
- Echo removal off by default (it cancelled notifications before the watch got them).

* Fri Nov 06 2026 Boris Solodki <solboki@solodki.fr> - 1.8.0-1
- "Forwarded apps" page: choose which native apps reach the watch.

* Fri Nov 06 2026 Boris Solodki <solboki@solodki.fr> - 1.7.0-1
- Copies of Callbridge notifications removed from the Sailfish notification area.

* Mon Nov 02 2026 Boris Solodki <solboki@solodki.fr> - 1.6.0-1
- Watchdog: wakes the Android relay after AppSupport restarts.
- Unique notification keys (oFono reuses call paths).
- Recent events shown in both apps; calls always logged.

* Mon Nov 02 2026 Boris Solodki <solboki@solodki.fr> - 1.5.0-1
- Android app opens on its own screen again (no activity-alias).
- Android icon hidden on the Sailfish side (NoDisplay=true), re-applied by the service.
- Settings app: uninstall button.

* Mon Nov 02 2026 Boris Solodki <solboki@solodki.fr> - 1.4.1-1
- Settings app: the Android app is installed through the system dialog (apkd-install requires privileges).

* Mon Nov 02 2026 Boris Solodki <solboki@solodki.fr> - 1.4.0-1
- Settings app: switch the service on and off, install and open the Android app, hide its icon.
- Project icon in the Sailfish OS shape; English and French texts.

* Mon Nov 02 2026 Boris Solodki <solboki@solodki.fr> - 1.3.0-1
- First public release; experimental "telephony" call style removed.

* Mon Nov 02 2026 Boris Solodki <solboki@solodki.fr> - 1.2.0-1
- Versioned APK file name; experimental "telephony" call style.

* Mon Nov 02 2026 Boris Solodki <solboki@solodki.fr> - 1.1.0-1
- Caller name looked up in the Sailfish address book, then in Android contacts.

* Mon Nov 02 2026 Boris Solodki <solboki@solodki.fr> - 1.0.3-1
- SMS and missed calls also recognised by app name (Sailfish OS 5 posts SMS without category).

* Mon Nov 02 2026 Boris Solodki <solboki@solodki.fr> - 1.0.2-1
- Android app shows whether Android blocks its notifications.

* Mon Nov 02 2026 Boris Solodki <solboki@solodki.fr> - 1.0.1-1
- Android app targets API 25 (Android 15 refuses targets below 24).

* Mon Nov 02 2026 Boris Solodki <solboki@solodki.fr> - 1.0.0-1
- First version: SMS, missed calls, incoming calls, optional categories.
