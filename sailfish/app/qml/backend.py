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
import subprocess
import time

SERVICE = "callbridge.service"
CONF_DIR = os.path.expanduser("~/.config/callbridge")
DISABLED_FLAG = os.path.join(CONF_DIR, "disabled")
APK_GLOB = "/usr/share/callbridge/Callbridge-*.apk"
ANDROID_PACKAGE = "org.callbridge.relay"
ICON_FLAG = os.path.join(CONF_DIR, "android-icon-hidden")
OVERRIDE_MARK = "# Callbridge override"   # copies written by the service, not launchers
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
                    text = fh.read()
                if ANDROID_PACKAGE in text and OVERRIDE_MARK not in text:
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
        "iconHidden": os.path.exists(ICON_FLAG),
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
    """Fallback when QML cannot open the APK itself.

    xdg-open takes the same route as the file manager: Sailfish shows its own
    confirmation dialog, then installs the app in AppSupport. Calling
    apkd-install directly is not possible: it requires the "privileged"
    group, which this app does not have.
    """
    apk = _bundled_apk()
    if not apk:
        return {"ok": False, "message": "no-apk"}
    try:
        subprocess.Popen(["xdg-open", apk], start_new_session=True)
        return {"ok": True, "message": "confirm"}
    except OSError as exc:
        return {"ok": False, "message": str(exc)}


def _launch(path):
    """Runs the launcher's Exec line. Returns "" on success, else the error."""
    cmd = _desktop_exec(path)
    if not cmd:
        return "no Exec line in " + path
    try:
        proc = subprocess.Popen(cmd, start_new_session=True,
                                stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                universal_newlines=True)
    except OSError as exc:
        return str(exc)
    try:
        out, _ = proc.communicate(timeout=3)
    except subprocess.TimeoutExpired:
        return ""                              # still running: launched
    return "" if proc.returncode == 0 else (out.strip() or "exit %d" % proc.returncode)


def open_android():
    """Opens the Android app through the launcher file AppSupport created.

    A hidden icon only carries NoDisplay=true: the file is still there and
    its Exec line still works.
    """
    path = _android_desktop()
    if not path:
        return {"ok": False, "message": "not-installed"}
    err = _launch(path)
    return {"ok": not err, "message": err}


def set_icon_hidden(hidden):
    """Delegates to the service, which also re-applies it if AppSupport
    regenerates its launcher files."""
    code, out = _run("callbridge-daemon", "--icon", "hide" if hidden else "show")
    try:
        rep = json.loads(out.splitlines()[-1])
    except (ValueError, IndexError):
        return {"ok": False, "message": out or "exit %d" % code}
    if not rep.get("launchers"):
        return {"ok": False, "message": "not-installed"}
    return {"ok": True, "message": rep["launchers"][0]["method"]}


def uninstall_android():
    """Asks the Android app to show Android's uninstall dialog.

    Android only lets an open screen ask for an uninstall: the request is
    queued through the shared folder, then the app is opened and shows the
    dialog. Removing an Android app directly would need privileges.
    """
    if not _android_desktop():
        return {"ok": False, "message": "not-installed"}
    try:
        os.remove(os.path.join(_drop_dir(), "relay.state"))
    except OSError:
        pass
    _drop({"action": "command", "command": "uninstall"})
    time.sleep(2.5)                          # let the relay record the request
    return open_android()


def send_test(text):
    _drop({"action": "post", "kind": "test", "key": "test",
           "title": "Callbridge", "text": text})
    return {"ok": True, "message": ""}


# ---------------------------------------------------------------------------
# Choice of forwarded apps (read by the service at each notification)
# ---------------------------------------------------------------------------

APPS_FILE = os.path.join(CONF_DIR, "apps.json")
SEEN_FILE = os.path.join(CONF_DIR, "seen-apps.json")
CONF_KEYS = {"sms": "FORWARD_SMS", "missed": "FORWARD_MISSED_CALLS",
             "calls": "FORWARD_INCOMING_CALLS"}


def _load(path, default):
    try:
        with open(path, encoding="utf-8") as fh:
            data = json.load(fh)
        return data if isinstance(data, type(default)) else default
    except (OSError, ValueError):
        return default


def _save(path, data):
    os.makedirs(CONF_DIR, exist_ok=True)
    tmp = path + ".tmp"
    with open(tmp, "w", encoding="utf-8") as fh:
        json.dump(data, fh, ensure_ascii=False, indent=1)
    os.rename(tmp, path)


def _conf_value(key):
    """Value of a setting in callbridge.conf, "1" when absent."""
    try:
        with open(os.path.join(CONF_DIR, "callbridge.conf"), encoding="utf-8") as fh:
            for line in fh:
                line = line.strip()
                if line.startswith(key + "="):
                    return line.split("=", 1)[1].strip().strip('"').strip("'")
    except OSError:
        pass
    return "1"


def apps():
    """Phone switches and the list of native apps seen so far, newest first."""
    choice = _load(APPS_FILE, {})
    selected = set(choice.get("apps") or [])
    phone = {k: choice[k] if isinstance(choice.get(k), bool) else _conf_value(v) == "1"
             for k, v in CONF_KEYS.items()}
    seen = _load(SEEN_FILE, {})
    native, android = [], []
    for name, info in seen.items():
        if not isinstance(info, dict) or info.get("builtin"):
            continue
        item = {"name": name, "enabled": name in selected,
                "last": int(info.get("last", 0)), "count": int(info.get("count", 0))}
        (android if info.get("origin") == "android" else native).append(item)
    # Apps chosen earlier but not seen since the list was cleared
    for name in selected - set(seen):
        native.append({"name": name, "enabled": True, "last": 0, "count": 0})
    native.sort(key=lambda a: (-a["last"], a["name"].lower()))
    android.sort(key=lambda a: a["name"].lower())
    return {"phone": phone, "native": native, "android": android}


def set_phone(key, enabled):
    if key not in CONF_KEYS:
        return {"ok": False, "message": key}
    choice = _load(APPS_FILE, {})
    choice[key] = bool(enabled)
    _save(APPS_FILE, choice)
    return {"ok": True, "message": ""}


def set_app(name, enabled):
    choice = _load(APPS_FILE, {})
    selected = [a for a in (choice.get("apps") or []) if a != name]
    if enabled:
        selected.append(name)
    choice["apps"] = sorted(selected, key=str.lower)
    _save(APPS_FILE, choice)
    return {"ok": True, "message": ""}


def forget_seen():
    """Clears the list of seen apps; selected apps stay selected."""
    try:
        os.remove(SEEN_FILE)
    except FileNotFoundError:
        pass
    # The service keeps its own copy in memory: restart it so it starts afresh.
    if not os.path.exists(DISABLED_FLAG):
        _systemctl("restart", SERVICE)
    return {"ok": True, "message": ""}



def set_level(level):
    """How Callbridge notifications behave on the phone (high, low, min)."""
    if level not in ("high", "low", "min"):
        return {"ok": False, "message": level}
    _drop({"action": "command", "command": "set-level", "level": level})
    return {"ok": True, "message": ""}
