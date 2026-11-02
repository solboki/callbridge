Name:           callbridge
Version:        1.3.0
Release:        1
Summary:        Relais des appels et SMS natifs de Sailfish OS vers Android AppSupport
License:        GPL-3.0-or-later
URL:            https://forum.sailfishos.org/
Source0:        %{name}-%{version}.tar.gz
BuildArch:      noarch
AutoReqProv:    no
Requires:       /usr/bin/python3
Requires:       /usr/bin/dbus-monitor
Requires:       systemd

# Paquet lisible par le rpm de Sailfish OS (pas de compression zstd)
%define _binary_payload w9.gzdio
%define _source_payload w9.gzdio
%define _build_id_links none

%description
Callbridge écoute les notifications natives de Sailfish OS (SMS, appels
manqués) et les appels entrants signalés par oFono, puis les dépose dans un
dossier partagé avec le conteneur Android. L'application Android Callbridge,
fournie dans /usr/share/callbridge/Callbridge-<version>.apk, les republie comme
notifications Android : Garmin Connect (ou toute autre application compagnon
de montre) peut alors les transmettre au poignet.

Projet réalisé en « vibe coding » avec Claude Opus 5.5 (Anthropic).

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

%post
if [ -x /usr/bin/systemctl-user ]; then
    systemctl-user daemon-reload >/dev/null 2>&1 || :
    systemctl-user restart callbridge.service >/dev/null 2>&1 || :
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

%changelog
* Mon Nov 02 2026 Boris Solodki - 1.3.0-1
- Première version publiée ; retrait du style « téléphonie » expérimental.

* Mon Nov 02 2026 Boris Solodki - 1.2.0-1
- APK : nom de fichier numéroté ; style « téléphonie » expérimental pour les appels (CallStyle, catégorie missed_call) ; bouton de test d'appel.

* Mon Nov 02 2026 Boris Solodki - 1.1.0-1
- Appel entrant : nom de l'appelant cherché dans le carnet Sailfish, puis dans les contacts du conteneur Android.

* Mon Nov 02 2026 Boris Solodki - 1.0.3-1
- Service : SMS et appels manqués reconnus aussi par nom d'application (Sailfish OS 5 publie les SMS sans catégorie, sous « Messages »).

* Mon Nov 02 2026 Boris Solodki - 1.0.2-1
- APK : affiche si Android bloque les notifications, bouton vers leurs réglages, messages de confirmation.

* Mon Nov 02 2026 Boris Solodki - 1.0.1-1
- APK : cible relevée à l'API 25 (Android 15 refuse les cibles inférieures à 24).

* Mon Nov 02 2026 Boris Solodki - 1.0.0-1
- Première version : SMS, appels manqués, appels entrants, catégories au choix.
