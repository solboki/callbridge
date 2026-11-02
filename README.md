# Callbridge

Callbridge fait arriver sur votre montre connectée les appels et les SMS de Sailfish OS, quand la montre dépend d'une application Android comme Garmin Connect.

> **Projet réalisé en « vibe coding ».** Le code de Callbridge a été écrit par l'intelligence artificielle Claude Opus 5.5 (Anthropic), au fil d'une conversation avec son auteur, qui a fixé le besoin, testé chaque version sur son téléphone et signalé les erreurs. Personne n'a relu le code ligne à ligne comme on le ferait pour un logiciel professionnel. Il est publié tel quel, sans garantie, dans l'espoir qu'il rende service. Les retours et corrections sont les bienvenus.

*An English summary is available [at the end of this page](#in-english).*

## Le problème résolu

Sur un téléphone Sailfish OS, les applications Android tournent dans un compartiment séparé, appelé AppSupport. Garmin Connect y fonctionne bien, mais il ne voit que les notifications nées dans ce compartiment. Or les appels et les SMS sont gérés par les applications natives de Sailfish, de l'autre côté de la cloison. Garmin Connect ne les voit jamais, et la montre reste muette quand le téléphone sonne.

Callbridge fait traverser la cloison aux messages. Il ne parle pas à la montre et ne touche pas à son appairage : il recopie les appels et SMS natifs sous forme de notifications Android, que Garmin Connect transmet ensuite normalement.

Autrement dit, la montre continue de dialoguer avec Garmin Connect comme avant. Callbridge se contente de lui donner quelque chose à dire.

## Comment cela fonctionne

Callbridge se compose de deux morceaux qui se complètent.

```
 Sailfish OS                                   Conteneur Android (AppSupport)
┌───────────────────────────┐                 ┌──────────────────────────────┐
│ SMS, appels manqués       │                 │                              │
│ appels entrants (oFono)   │                 │  application Callbridge      │
│          │                │   fichier JSON  │          │                   │
│          ▼                │  ─────────────► │          ▼                   │
│  service callbridge       │ ~/android_      │  notification Android        │
│  (paquet RPM)             │ storage/        │          │                   │
│                           │ Callbridge      │          ▼                   │
└───────────────────────────┘                 │  Garmin Connect ──► montre   │
                                              └──────────────────────────────┘
```

Le premier morceau est un petit service Sailfish, installé par le paquet RPM. Il écoute le bus de messages du système (D-Bus, le canal par lequel les applications se parlent). Il y repère les notifications de SMS et d'appels manqués, ainsi que les appels entrants annoncés par oFono, la partie du système qui gère la téléphonie.

À chaque événement, le service écrit un petit fichier texte au format JSON (un format de données structuré) dans le dossier `~/android_storage/Callbridge`. Ce dossier est partagé avec Android, qui le voit sous le nom `/sdcard/Callbridge`.

Le second morceau est une petite application Android, installée par le fichier APK. Elle surveille ce dossier, transforme chaque fichier en notification Android, puis l'efface. Aucun des deux morceaux n'a besoin des droits d'administrateur pour fonctionner.

## Ce qui arrive sur la montre

**Les SMS** arrivent avec le nom de l'expéditeur et le texte du message.

**Les appels entrants** arrivent pendant la sonnerie. Si le numéro figure dans votre répertoire, la montre affiche le nom du contact, puis « Appel entrant » et le numéro. Sinon, elle affiche le numéro seul, ou « Numéro masqué ». La notification disparaît de la montre dès que vous décrochez ou que l'appel se termine.

**Les appels manqués** arrivent tels que Sailfish les affiche dans son centre de notifications.

D'autres notifications natives peuvent être ajoutées par réglage, par exemple les courriels (voir « Réglages »).

## Ce dont vous avez besoin

Il vous faut un téléphone sous Sailfish OS avec Android AppSupport. Callbridge a été mis au point sur un Jolla sous Sailfish OS 5, avec un conteneur Android 15 et une montre Garmin. Il devrait fonctionner avec les autres versions récentes, mais cela n'a pas été vérifié.

Il vous faut aussi l'application compagnon de votre montre, installée dans AppSupport et déjà appairée. Callbridge a été testé avec Garmin Connect. Rien n'est propre à Garmin dans son fonctionnement : toute application compagnon qui relaie les notifications Android devrait convenir.

Enfin, le mode développeur doit être activé (Paramètres, puis Outils de développement), ainsi que l'installation de logiciels non vérifiés.

## Installation

Téléchargez les deux fichiers de la dernière version dans la page « Releases » du dépôt : le paquet Sailfish `callbridge-X.Y.Z-1.noarch.rpm` et l'application Android `Callbridge-X.Y.Z.apk`. Les lettres X.Y.Z désignent le numéro de version.

**1. Installer le paquet Sailfish.** Dans le terminal :

```
devel-su pkcon install-local callbridge-X.Y.Z-1.noarch.rpm
```

Un appui sur le fichier dans le gestionnaire de fichiers fonctionne aussi. Le service démarre de lui-même. S'il ne démarre pas, lancez-le en utilisateur ordinaire, sans `devel-su` :

```
systemctl --user daemon-reload
systemctl --user restart callbridge
```

**2. Installer l'application Android.** Appuyez sur le fichier `Callbridge-X.Y.Z.apk` dans le gestionnaire de fichiers. Une copie est aussi installée par le paquet RPM dans `/usr/share/callbridge/`.

**3. Régler l'application.** Ouvrez Callbridge et appuyez sur « Accorder les autorisations ». Android demande l'accès au stockage, pour lire le dossier partagé, et aux contacts, pour trouver le nom des appelants. Vérifiez ensuite la ligne « Notifications autorisées ». Si elle indique « NON », appuyez sur « Réglages des notifications » et activez-les. C'est le réglage le plus important : sous Android 13 et au-delà, une application dont les notifications sont bloquées ne peut rien transmettre, et Android ne prévient pas.

Appuyez enfin sur « Ne pas optimiser la batterie » et exemptez Callbridge, pour qu'Android ne l'endorme pas.

**4. Régler Garmin Connect.** Dans les réglages de notifications de Garmin Connect, autorisez l'application Callbridge.

**5. Vérifier.** Appuyez sur « Notification de test » dans Callbridge : elle doit arriver sur la montre. Testez ensuite toute la chaîne depuis le terminal :

```
callbridge-daemon --test
```

Une notification « Test depuis Sailfish OS » doit arriver sur la montre en deux secondes environ. Envoyez-vous enfin un SMS depuis un autre téléphone.

## Réglages

Les réglages se trouvent dans un fichier texte. Pour le créer, copiez le modèle fourni :

```
mkdir -p ~/.config/callbridge
cp /usr/share/callbridge/callbridge.conf.example ~/.config/callbridge/callbridge.conf
```

Modifiez-le, puis relancez le service avec `systemctl --user restart callbridge`. Chaque réglage y est expliqué.

Les trois premiers réglages coupent ou rétablissent chaque type d'événement : SMS, appels manqués et appels entrants.

Les réglages `EXTRA_CATEGORIES` et `EXTRA_APPS` ajoutent d'autres notifications natives. Par exemple, `EXTRA_CATEGORIES=x-nemo.email` transmet les courriels de l'application Courriel de Sailfish. Pour connaître le nom exact d'une notification, arrêtez le service, lancez-le à la main en mode détaillé, puis déclenchez la notification voulue :

```
systemctl --user stop callbridge
callbridge-daemon --debug
```

Chaque notification apparaît sous la forme d'une ligne `Notify app=… cat=…`. La valeur `cat` se reporte dans `EXTRA_CATEGORIES`, la valeur `app` dans `EXTRA_APPS`. Relancez ensuite le service avec `systemctl --user start callbridge`.

Les réglages `SMS_APPS` et `MISSED_CALL_APPS` servent quand Sailfish publie une notification sans catégorie. C'est le cas des SMS sur Sailfish OS 5, publiés sous le nom « Messages ». Si votre téléphone est réglé dans une autre langue, ce nom peut différer : le mode détaillé vous l'indiquera.

## En cas de problème

Le journal du service se lit avec la commande suivante :

```
journalctl --user -u callbridge -f
```

Le plus simple est de suivre le trajet d'un message, étape par étape.

**Rien n'apparaît dans `~/android_storage/Callbridge` lors d'un SMS.** C'est le service Sailfish qui ne repère pas la notification. Lancez `callbridge-daemon --debug` comme indiqué plus haut et vérifiez que le SMS y figure. S'il figure sous un nom d'application inconnu, ajoutez ce nom à `SMS_APPS`.

**Les fichiers s'accumulent dans ce dossier sans être consommés.** C'est l'application Android qui ne les lit pas. Ouvrez-la : la ligne « Lisible » doit indiquer « oui » et la ligne « Relais » doit indiquer « actif ». Sinon, accordez les autorisations et appuyez sur « Redémarrer le relais ».

**La ligne « Dernier événement » bouge dans l'application, mais la montre ne reçoit rien.** Le message a traversé la cloison, mais Android ou Garmin Connect le bloquent. Vérifiez la ligne « Notifications autorisées », puis l'autorisation donnée à Callbridge dans Garmin Connect.

**Les SMS arrivent, mais pas les appels entrants.** Pendant un appel, lancez :

```
dbus-monitor --system "type='signal',interface='org.ofono.VoiceCallManager'"
```

Si cette commande n'affiche rien, votre version de Sailfish réserve les signaux d'appel à des comptes privilégiés. Les appels manqués restent transmis dans tous les cas.

**Pour tester l'application Android seule**, sans le service Sailfish :

```
devel-su appsupport-attach am broadcast -a org.callbridge.relay.POST -n org.callbridge.relay/.CommandReceiver -e title "Test" -e text "Bonjour"
```

## Limites connues

On voit l'appel sur la montre, mais on ne peut ni décrocher ni refuser depuis le poignet.

L'icône affichée sur la montre est celle d'un message, même pour un appel. C'est Garmin Connect qui choisit l'icône, selon des règles qu'il ne documente pas et qui semblent reposer sur une liste d'applications connues. Une présentation imitant les applications de téléphonie d'Android a été essayée sans succès, puis retirée.

Sailfish recopie les notifications Android dans son propre centre de notifications. Un SMS peut donc y apparaître deux fois.

Le nom sous lequel Sailfish publie les appels manqués sans catégorie n'a été vérifié que partiellement. Si les appels manqués n'arrivent pas, le mode détaillé vous indiquera le nom à ajouter dans `MISSED_CALL_APPS`.

## Désinstallation

```
devel-su pkcon remove callbridge
```

Désinstallez ensuite l'application Callbridge comme n'importe quelle application Android, puis supprimez le dossier `~/android_storage/Callbridge` et, si vous l'avez créé, `~/.config/callbridge`.

## Construire depuis les sources

La construction se fait sur un ordinateur Debian ou Ubuntu, sans Android Studio ni Gradle :

```
sudo apt install openjdk-17-jdk aapt dalvik-exchange zipalign apksigner \
    android-sdk-platform-23 rpm python3-pil
./make-dist.sh
```

Le script produit dans `dist/` l'APK et le RPM, tous deux numérotés, ainsi qu'une archive des sources.

L'APK est signé avec une clé créée au premier passage dans `android/callbridge.keystore`. Cette clé n'est volontairement pas publiée dans le dépôt. Conservez-la précieusement : Android refuse de mettre à jour une application avec un APK signé par une autre clé. Il faut alors désinstaller l'ancienne version d'abord.

Pour publier une nouvelle version, changez le numéro à quatre endroits : `V=` dans `make-dist.sh`, `Version:` dans `rpm/callbridge.spec`, `VERSION` dans `sailfish/callbridge-daemon`, et `versionCode` et `versionName` dans `android/AndroidManifest.xml`. Ajoutez une entrée dans `CHANGELOG.md` et dans le `%changelog` du fichier `.spec`.

## Tests

Le service Sailfish peut être testé sur un ordinateur Linux, sans téléphone. Le script de test crée un bus D-Bus temporaire, y simule des SMS, des appels manqués et des appels entrants, puis vérifie les fichiers produits :

```
pip install dbus-next
./tests/run-tests.sh
```

## Organisation des sources

| Dossier | Contenu |
|---|---|
| `sailfish/` | Le service (Python, sans dépendance autre que `python3` et `dbus-monitor`), son fichier de démarrage systemd et le modèle de réglages. |
| `android/` | L'application Android, en Java, sans bibliothèque externe, et son script de construction. |
| `rpm/` | La description du paquet Sailfish. |
| `tests/` | Les tests du service sur un bus D-Bus simulé. |

## Projets voisins

[Vapaamin](https://forum.sailfishos.org/t/vapaamin-garmin-notifications-bridge/33097) suit une autre voie : il remplace Garmin Connect et parle directement à la montre, ce qui lui permet de transmettre toutes les notifications natives. Callbridge, lui, garde Garmin Connect et ses fonctions, comme la synchronisation des activités. [Amazfish](https://github.com/piggz/harbour-amazfish) joue le même rôle pour d'autres marques de montres.

## Licence

Callbridge est un logiciel libre, distribué sous licence GNU GPL version 3 ou ultérieure. Le texte complet figure dans le fichier `LICENSE`.

## In English

Callbridge forwards native Sailfish OS phone calls and SMS to a smartwatch whose companion app (such as Garmin Connect) runs inside Android AppSupport. That app only sees Android notifications, so native calls and texts never reached the watch.

A small Sailfish service (RPM) listens on D-Bus for native SMS and missed-call notifications and for incoming calls reported by oFono. It writes each event as a JSON file into `~/android_storage/Callbridge`. A small Android app (APK) watches that folder and re-posts each event as an Android notification, which the companion app forwards to the watch. Incoming calls show the caller's name when the number is in your contacts. No root access is needed.

To install, run `devel-su pkcon install-local callbridge-X.Y.Z-1.noarch.rpm`, install `Callbridge-X.Y.Z.apk`, open it, grant permissions, and make sure the line "Notifications autorisées" reads "oui". Then allow Callbridge in your watch app's notification settings and run `callbridge-daemon --test`. The user interface and the documentation are in French.

This project was vibe-coded with Claude Opus 5.5 (Anthropic) and tested by its author on a single device. It comes with no warranty. Licensed under GPL-3.0-or-later.
