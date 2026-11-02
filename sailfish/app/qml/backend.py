# -*- coding: utf-8 -*-
"""
Backend of the Callbridge settings app (called from QML through PyOtherSide).

Every function returns plain dicts or strings so that QML can use them
directly. Nothing here needs root: the service runs as a user service, and the
Android side is driven through the folder shared with AppSupport.

License: GPL-3.0-or-later
"""

import glob
import json
import os
import shlex
import shutil
import subprocess
import time

SERVICE = "callbridge.service"
CONF_DIR = os.path.expanduser("~/.config/callbridge")
DISABLED_FLAG = os.path.join(CONF_DIR, "disabled")
APK_GLOB = "/usr/share/callbridge/Callbridge-*.apk"
ANDROID_PACKAGE = "org.callbridge.relay"
DESKTOP_DIRS = ["/usr/share/applications",
                os.path.expanduser("~/.local/share/applications")]
STATE_ALIVE_SECONDS = 75   # the Android relay rewrites its state every 30 s


def _drop_dir():
    """Shared folder, honouring DROP_DIR from the service settings."""
    path = os.path.join(CONF_DIR, "callbridge.conf")
    try:
        with open(path, encoding="utf-8") as fh:
            for line in fh:
                line = line.strip()
                if line.startswith("DROP_DIR="):
                    value = line.split("=", 1)[1].strip().strip('"').strip("'")
                    if value:
                        return os.path.expanduser(value)
    except OSError:
        pass
    return os.path.expanduser("~/android_storage/Callbridge")


def _run(*cmd, timeout=15):
    try:
        res = subprocess.run(list(cmd), stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                             timeout=timeout, universal_newlines=True)
        return res.returncode, res.stdout.strip()
    except (OSError, subprocess.SubprocessError) as exc:
        return 127, str(exc)


def _systemctl(*args):
    return _run("systemctl", "--user", *args)


# ---------------------------------------------------------------------------
# Events and commands for the Android relay
# ---------------------------------------------------------------------------

_seq = 0


def _drop(event):
    """Writes one event file, the same way the service does."""
    global _seq
    _seq += 1
    d = _drop_dir()
    os.makedirs(d, exist_ok=True)
    event["ts"] = int(time.time() * 1000)
    name = "%d-gui%03d.json" % (event["ts"], _seq % 1000)
    tmp = os.path.join(d, "." + name + ".tmp")
    with open(tmp, "w", encoding="utf-8") as fh:
        json.dump(event, fh, ensure_ascii=False)
    os.chmod(tmp, 0o666)
    os.rename(tmp, os.path.join(d, name))


def _relay_state():
    try:
        with open(os.path.join(_drop_dir(), "relay.state"), encoding="utf-8") as fh:
            st = json.load(fh)
    except (OSError, ValueError):
        return {"known": False, "alive": False}
    age = time.time() - st.get("ts", 0) / 1000.0
    st["known"] = True
    st["age"] = int(age)
    st["alive"] = bool(st.get("running")) and age < STATE_ALIVE_SECONDS
    return st


def _android_desktop():
    """Path of the launcher file AppSupport created for the Android app, if any."""
    for d in DESKTOP_DIRS:
        for path in sorted(glob.glob(os.path.join(d, "*.desktop"))):
            if "apkd" not in os.path.basename(path) and ANDROID_PACKAGE not in path:
                continue
            try:
                with open(path, encoding="utf-8", errors="replace") as fh:
                    if ANDROID_PACKAGE in fh.read():
                        return path
            except OSError:
                pass
    return ""


def _desktop_exec(path):
    with open(path, encoding="utf-8", errors="replace") as fh:
        for line in fh:
            if line.startswith("Exec="):
                parts = [p for p in shlex.split(line[5:].strip())
                         if not (len(p) == 2 and p.startswith("%"))]
                return parts
    return []


def _bundled_apk():
    apks = sorted(glob.glob(APK_GLOB))
    return apks[-1] if apks else ""


# ---------------------------------------------------------------------------
# Functions called from QML
# ---------------------------------------------------------------------------

def status():
    code, out = _systemctl("is-active", SERVICE)
    apk = _bundled_apk()
    return {
        "enabled": not os.path.exists(DISABLED_FLAG),
        "active": out == "active",
        "serviceState": out,
        "relay": _relay_state(),
        "apk": apk,
        "apkVersion": os.path.basename(apk)[len("Callbridge-"):-len(".apk")] if apk else "",
        "androidLauncher": _android_desktop(),
    }


def set_enabled(enabled):
    os.makedirs(CONF_DIR, exist_ok=True)
    if enabled:
        try:
            os.remove(DISABLED_FLAG)
        except FileNotFoundError:
            pass
        _systemctl("daemon-reload")
        code, out = _systemctl("restart", SERVICE)
    else:
        open(DISABLED_FLAG, "w").close()
        code, out = _systemctl("stop", SERVICE)
    return {"ok": code == 0, "message": out}


def install_apk():
    apk = _bundled_apk()
    if not apk:
        return {"ok": False, "message": "no-apk"}
    if shutil.which("apkd-install"):
        code, out = _run("apkd-install", apk, timeout=120)
        return {"ok": code == 0, "message": out}
    # Same path as tapping the file in the file manager: Sailfish asks for
    # confirmation, then installs it in AppSupport.
    try:
        subprocess.Popen(["xdg-open", apk], start_new_session=True)
        return {"ok": True, "message": "confirm"}
    except OSError as exc:
        return {"ok": False, "message": str(exc)}


def _launch(path):
    cmd = _desktop_exec(path)
    if not cmd:
        return False
    subprocess.Popen(cmd, start_new_session=True)
    return True


def open_android():
    """Opens the Android app, briefly restoring its icon if it is hidden."""
    path = _android_desktop()
    if path:
        return {"ok": _launch(path), "message": ""}
    st = _relay_state()
    if not st.get("alive"):
        return {"ok": False, "message": "not-installed"}
    _drop({"action": "command", "command": "show-icon"})
    for _ in range(40):                      # up to 20 s for AppSupport
        time.sleep(0.5)
        path = _android_desktop()
        if path:
            break
    if not path:
        return {"ok": False, "message": "no-launcher"}
    ok = _launch(path)
    if st.get("icon_hidden"):
        time.sleep(4)
        _drop({"action": "command", "command": "hide-icon"})
    return {"ok": ok, "message": ""}


def set_icon_hidden(hidden):
    _drop({"action": "command", "command": "hide-icon" if hidden else "show-icon"})
    return {"ok": True, "message": ""}


def send_test(text):
    _drop({"action": "post", "kind": "test", "key": "test",
           "title": "Callbridge", "text": text})
    return {"ok": True, "message": ""}
