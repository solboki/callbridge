#!/bin/bash
# Tests the Sailfish service on a temporary D-Bus bus, without a phone.
# Requirements: dbus-daemon, dbus-monitor, python3 and the dbus-next module
#   (pip install dbus-next).
set -u
HERE=$(cd "$(dirname "$0")" && pwd)
DAEMON="$HERE/../sailfish/callbridge-daemon"
WORK=$(mktemp -d)
FAKE=; DAEMON_PID=; BUS_PID=
trap 'kill $FAKE $DAEMON_PID $BUS_PID 2>/dev/null; rm -rf "$WORK"' EXIT

# Fake Sailfish address book holding one contact
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

# The user chose to forward "Mail", but not "Weather"
mkdir -p "$WORK/home/.config/callbridge"
echo '{"apps": ["Mail", "Relay"]}' > "$WORK/home/.config/callbridge/apps.json"

BUS=$(dbus-daemon --session --fork --print-address=1 --print-pid=1)
export DBUS_SESSION_BUS_ADDRESS=$(printf '%s\n' "$BUS" | sed -n 1p)
BUS_PID=$(printf '%s\n' "$BUS" | sed -n 2p)
CLOSED_LOG="$WORK/closed.log" python3 "$HERE/fake_bus.py" & FAKE=$!
sleep 0.8
HOME="$WORK/home" DROP_DIR="$WORK/drop" OFONO_BUS=session DEBUG=1 LANGUAGE=fr \
    python3 "$DAEMON" > "$WORK/daemon.log" 2>&1 & DAEMON_PID=$!
sleep 9
kill $DAEMON_PID 2>/dev/null

ALL=$(for f in "$WORK"/drop/*.json; do [ -f "$f" ] && { cat "$f"; echo; }; done)
FAIL=0
check() {  # check "description" "expected pattern" [expected count]
    local n
    n=$(printf '%s\n' "$ALL" | grep -cF -- "$2")
    if [ "$n" -eq "${3:-1}" ]; then
        echo "ok    $1"
    else
        echo "FAIL  $1 (found $n times: $2)"
        FAIL=1
    fi
}

check "SMS with category, quotes and line break" \
      '"text": "Salut, ça va ?\nOn dit \"midi\" ou \"13h\" ? (test) [ok] {x}"'
check "duplicate SMS ignored" '"title": "Jean \"JD\" Dupont"' 1
check "missed call with category" '"title": "Appel manqué", "text": "Marie"'
check "SMS without category posted as Messages" '"title": "Alice Martin", "text": "Essai3"'
check "Callbridge echo ignored (by name or origin package)" 'Essai3 echo' 0
check "notification of another category ignored" 'Ignore' 0
check "incoming call with name found in address book" \
      '"title": "Jean Dupont", "text": "Appel entrant · +33612345678"'
check "private number" '"text": "Numéro masqué"'
check "call removed when answered or ended" '"action": "cancel"' 2
check "outgoing call ignored" '0611' 0

check "chosen native app forwarded with its name" \
      '"title": "Mail · Bob", "text": "Meeting moved to 3 pm"'
check "native app not chosen is not forwarded" 'Rain expected' 0

SEEN=$(cat "$WORK/home/.config/callbridge/seen-apps.json" 2>/dev/null)
ALL="$SEEN"
check "app not chosen still listed for the settings app" '"Weather"'
check "SMS and phone apps flagged as built-in" '"builtin": true' 2
check "no notification content stored in the list" 'Rain expected' 0

ALL=$(cat "$WORK/closed.log" 2>/dev/null)
check "no notification is ever closed in Sailfish" 'Callbridge' 0
check "native notifications left alone" 'commhistoryd' 0

# AppSupport denylist shipped by the package: must stay byte-identical to the
# file users may have created by hand, so that rpm takes it over silently.
ALL=$(cat "$HERE/../sailfish/95-callbridge.conf")
check "denylist in the [Notifications] section" '[Notifications]'
check "denylist names the Callbridge package" 'NotifDenylist=org.callbridge.relay'

# QML: a page property named after a built-in one (data, children...) hides
# everything the page contains, and qmllint does not notice it.
ALL=$(grep -nE 'property [a-z]+ (data|children|resources|parent|state|states)\b' \
      "$HERE/../sailfish/app/qml/callbridge.qml")
check "no QML property shadowing a built-in one" 'property' 0

if [ $FAIL -ne 0 ]; then
    echo; echo "Service log:"; cat "$WORK/daemon.log"
    exit 1
fi
echo "All tests passed."
