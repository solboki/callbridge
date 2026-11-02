#!/bin/bash
# Teste le service Sailfish sur un bus D-Bus temporaire, sans téléphone.
# Prérequis : dbus-daemon, dbus-monitor, python3 et le module dbus-next
#   (pip install dbus-next).
set -u
HERE=$(cd "$(dirname "$0")" && pwd)
DAEMON="$HERE/../sailfish/callbridge-daemon"
WORK=$(mktemp -d)
FAKE=; DAEMON_PID=; BUS_PID=
trap 'kill $FAKE $DAEMON_PID $BUS_PID 2>/dev/null; rm -rf "$WORK"' EXIT

# Faux carnet d'adresses Sailfish contenant un contact
DB="$WORK/home/.local/share/system/privileged/Contacts/qtcontacts-sqlite"
mkdir -p "$DB"
python3 - "$DB/contacts.db" <<'EOF'
import sqlite3, sys
db = sqlite3.connect(sys.argv[1])
db.execute("create table Contacts(contactId integer, displayLabel text)")
db.execute("create table PhoneNumbers(detailId integer, contactId integer, "
           "phoneNumber text, normalizedNumber text)")
db.execute("insert into Contacts values (1, 'Jean Dupont')")
db.execute("insert into PhoneNumbers values (1, 1, '06 12 34 56 78', '')")
db.commit()
EOF

BUS=$(dbus-daemon --session --fork --print-address=1 --print-pid=1)
export DBUS_SESSION_BUS_ADDRESS=$(printf '%s\n' "$BUS" | sed -n 1p)
BUS_PID=$(printf '%s\n' "$BUS" | sed -n 2p)
python3 "$HERE/fake_bus.py" & FAKE=$!
sleep 0.8
HOME="$WORK/home" DROP_DIR="$WORK/drop" OFONO_BUS=session DEBUG=1 \
    python3 "$DAEMON" > "$WORK/daemon.log" 2>&1 & DAEMON_PID=$!
sleep 9
kill $DAEMON_PID 2>/dev/null

ALL=$(for f in "$WORK"/drop/*.json; do [ -f "$f" ] && { cat "$f"; echo; }; done)
FAIL=0
check() {  # check "description" "motif attendu" [nombre attendu]
    local n
    n=$(printf '%s\n' "$ALL" | grep -cF -- "$2")
    if [ "$n" -eq "${3:-1}" ]; then
        echo "ok    $1"
    else
        echo "ÉCHEC $1 (trouvé $n fois : $2)"
        FAIL=1
    fi
}

check "SMS avec catégorie, guillemets et retour à la ligne" \
      '"text": "Salut, ça va ?\nOn dit \"midi\" ou \"13h\" ? (test) [ok] {x}"'
check "doublon de SMS ignoré" '"title": "Jean \"JD\" Dupont"' 1
check "appel manqué avec catégorie" '"title": "Appel manqué", "text": "Marie"'
check "SMS sans catégorie publié sous « Messages »" '"title": "Alice Martin", "text": "Essai3"'
check "écho de Callbridge ignoré" 'Essai3 echo' 0
check "notification d'une autre catégorie ignorée" 'Ignore' 0
check "appel entrant avec nom trouvé dans le carnet" \
      '"title": "Jean Dupont", "text": "Appel entrant · +33612345678"'
check "appel masqué" '"text": "Numéro masqué"'
check "appel retiré au décroché ou à la fin" '"action": "cancel"' 2
check "appel sortant ignoré" '0611' 0

if [ $FAIL -ne 0 ]; then
    echo; echo "Journal du service :"; cat "$WORK/daemon.log"
    exit 1
fi
echo "Tous les tests sont passés."
