#!/usr/bin/env python3
"""End-to-end checks for Kadamm on the USB-connected phone, driven through adb and uiautomator.

Usage: python3 tests/e2e.py [test_name ...]     Screenshots go to tests/screens/.
The GPS test replaces the phone's GPS with a test provider while it runs and removes it afterwards.
"""
import math, os, re, subprocess, sys, time
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ADB = os.path.join(ROOT, "tools/android-build/sdk/platform-tools/adb")
PKG = "com.stride.run"
SHOTS = os.path.join(ROOT, "tests/screens")


def adb(*args, check=False):
    r = subprocess.run([ADB, *args], capture_output=True, text=True, errors="replace")
    if check and r.returncode:
        raise RuntimeError(r.stderr)
    return r.stdout


def sh(cmd):
    # Never inject a tap or swipe unless Kadamm is the app in front: the phone may be in use.
    if cmd.startswith("input ") and PKG not in adb("shell", "dumpsys window | grep mCurrentFocus"):
        raise AssertionError("Kadamm is not in the foreground; stopped so no other app is touched")
    return adb("shell", cmd)


def nodes():
    for _ in range(10):
        sh("rm -f /sdcard/kadamm-ui.xml; uiautomator dump /sdcard/kadamm-ui.xml >/dev/null 2>&1")
        xml = adb("exec-out", "cat", "/sdcard/kadamm-ui.xml")
        try:
            out = []
            for n in ET.fromstring(xml).iter("node"):
                m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", n.get("bounds", ""))
                if m:
                    out.append({"text": n.get("text", ""), "desc": n.get("content-desc", ""), "b": tuple(map(int, m.groups()))})
            return out
        except ET.ParseError:
            time.sleep(1)
    raise RuntimeError("could not read the screen")


def find(label, exact=False):
    for n in nodes():
        for v in (n["text"], n["desc"]):
            if (v == label) if exact else (label.lower() in v.lower()):
                return n
    return None


def visible(label):
    return find(label) is not None


def wait(label, seconds=8):
    end = time.time() + seconds
    while time.time() < end:
        if visible(label):
            return True
        time.sleep(.5)
    return False


def tap(label, at="center", exact=False):
    n = find(label, exact)
    assert n, f"'{label}' is not on screen"
    x0, y0, x1, y1 = n["b"]
    x = {"center": (x0 + x1) // 2, "right": x1 - 12, "left": x0 + 12}[at]
    sh(f"input tap {x} {(y0 + y1) // 2}")
    time.sleep(1)


def scroll_to(label, tries=30):
    for _ in range(tries):
        n = find(label)
        if n and 300 < n["b"][1] < 1950:
            return True
        sh("input swipe 540 1600 540 800 250")
        time.sleep(.6)
    return False


def top():
    sh("input swipe 540 500 540 2100 80"); sh("input swipe 540 500 540 2100 80"); sh("input swipe 540 500 540 2100 80")
    time.sleep(.6)


def shot(name):
    os.makedirs(SHOTS, exist_ok=True)
    with open(os.path.join(SHOTS, name + ".png"), "wb") as f:
        f.write(subprocess.run([ADB, "exec-out", "screencap", "-p"], capture_output=True).stdout)


def awake():
    """Wakes the screen; the tests cannot (and will not) unlock a secured lock screen."""
    adb("shell", "input keyevent KEYCODE_WAKEUP")
    time.sleep(.4)
    if "mDreamingLockscreen=true" in adb("shell", "dumpsys window | grep mDreamingLockscreen") or \
       "isKeyguardShowing=true" in adb("shell", "dumpsys window | grep -i isKeyguardShowing"):
        raise AssertionError("the phone is locked; unlock it and keep the screen on while tests run")


def home():
    """Back to the Today tab, whatever page a previous test left open."""
    launch(False)
    for _ in range(4):
        if visible("Good "):
            break
        sh("input keyevent KEYCODE_BACK"); time.sleep(.8)
        if PKG not in adb("shell", "dumpsys window | grep mCurrentFocus"):
            launch(False)
    top()


def launch(cold=True):
    awake()
    if cold:
        sh(f"am force-stop {PKG}")
        time.sleep(.5)
    out = sh(f"am start -W -n {PKG}/.MainActivity")
    m = re.search(r"TotalTime: (\d+)", out)
    time.sleep(1.5)
    return int(m.group(1)) if m else -1


def fatal_lines():
    return [l for l in adb("logcat", "-d").splitlines() if ("FATAL EXCEPTION" in l or "ANR in " + PKG in l)]


# ---------------------------------------------------------------- tests
def test_launch():
    times = [launch() for _ in range(5)]
    assert wait("KADAMM"), "dashboard did not appear"
    assert wait("Start activity") or visible("View live session"), "no start button"
    assert max(times) < 2000, times
    shot("01_today")
    return f"cold starts {times} ms"


def test_drawer():
    home()
    tap("Kadamm menu", at="right")          # edge of the wordmark button, not the text
    assert wait("Goals & targets"), "drawer did not open"
    shot("02_drawer")
    tap("History", at="right", exact=True)   # far right of the row, away from the label
    assert wait("Browse your sessions by day"), "History row edge tap did nothing"
    tap("Kadamm menu")
    tap("Goals & targets", at="right")
    assert wait("Move minutes"), "Goals row edge tap did nothing"
    shot("03_goals")
    tap("Kadamm menu"); tap("Today", at="right", exact=True)
    assert wait("Good ")


def test_swipe():
    home()
    sh("input swipe 900 1200 150 1200 180"); time.sleep(1)
    assert wait("Browse your sessions by day"), "swipe left did not reach History"
    shot("04_history")
    sh("input swipe 900 1200 150 1200 180"); time.sleep(1)
    assert wait("Appearance"), "swipe left did not reach Profile"
    shot("05_profile")
    sh("input swipe 150 1200 900 1200 180"); time.sleep(1)
    assert wait("Browse your sessions by day"), "swipe right did not return to History"
    sh("input swipe 150 1200 900 1200 180"); time.sleep(1)
    assert wait("Good "), "swipe right did not return to Today"
    sh("input swipe 500 1200 620 1200 300"); time.sleep(1)   # short drag must spring back
    assert visible("Good ")


def test_health_screens():
    home()
    tap("Kadamm menu"); tap("Step sources & health", at="right")
    assert wait("Phone step sensor") and visible("Health Connect")
    shot("06_step_sources")
    n = find("Refresh or manage") or find("Connect Health Connect")
    assert n, "no Health Connect button"
    tap(n["text"])
    assert wait("Bring in your health data"), "Health Connect screen did not open"
    time.sleep(2)
    shot("07_health_connect")
    assert "HealthConnectActivity" in sh("dumpsys activity activities | grep topResumedActivity") or visible("Connect / refresh steps")
    tap("Done")
    assert wait("Phone step sensor")


def test_sync():
    home()
    assert scroll_to("steps come from"), "sources card not found"
    scroll_to("Manage step sources")
    n = find("Sync now")
    if not n:
        assert visible("Health Connect not connected"), "no sync state shown"
        return "Health Connect not connected on this phone; not-connected state shown"
    before = sh(f"run-as {PKG} cat shared_prefs/kadamm_health.xml")
    x0, y0, x1, y1 = n["b"]
    sh(f"input tap {(x0 + x1) // 2} {(y0 + y1) // 2}")
    time.sleep(5)
    after = sh(f"run-as {PKG} cat shared_prefs/kadamm_health.xml")
    stamp = lambda x: re.search(r'healthSyncedAt" value="(\d+)"', x).group(1)
    assert stamp(after) > stamp(before), "sync time did not advance"
    assert wait("Last synced")
    shot("08_sync")
    return "healthSyncedAt advanced"


def test_step_permission_states():
    sh(f"pm revoke {PKG} android.permission.ACTIVITY_RECOGNITION"); time.sleep(1)
    try:
        launch()
        assert wait("Turn on all-day step counting"), "no setup card when permission is denied"
        shot("09_permission_denied")
    finally:
        sh(f"pm grant {PKG} android.permission.ACTIVITY_RECOGNITION")
    launch()
    assert wait("KADAMM") and not visible("Turn on all-day step counting"), "setup card still shown after grant"


def test_widget():
    assert "StepsWidget" in sh("dumpsys appwidget"), "widget provider is not registered"
    return "provider registered (placing it on the home screen is manual)"


def _state():
    return sh(f"run-as {PKG} cat shared_prefs/stride_state.xml")


def _value(state, name):
    m = re.search(r'name="%s" value="([^"]+)"' % name, state)
    return float(m.group(1)) if m else 0.0


def _confirm(title, button):
    assert wait(title), f"'{title}' dialog did not open"
    n = find(button.upper(), exact=True) or [x for x in nodes() if x["text"] == button][-1]
    x0, y0, x1, y1 = n["b"]
    sh(f"input tap {(x0 + x1) // 2} {(y0 + y1) // 2}")
    time.sleep(1.5)


def test_recording():
    """Start, pause, resume and finish a real outdoor session, then delete it. With a mock GPS route
    (needs 'Select mock location app' = Shell in Developer options) it also checks the measured distance."""
    lat0, lon0 = 22.68200, 72.88300
    launch(); top()
    assert not visible("View live session"), "a session is already running; finish it first"
    mock = "Exception" not in sh("cmd location providers add-test-provider gps --requiresSatellite 2>&1")
    try:
        if mock:
            sh("cmd location providers set-test-provider-enabled gps true")
            sh(f"cmd location providers set-test-provider-location gps --location {lat0},{lon0} --accuracy 4")
        tap("Start activity")
        time.sleep(3)
        try:
            if visible("Keep tracking in the background"):
                tap("Not now")
        except RuntimeError:
            pass
        assert '"active" value="true"' in _state(), "session did not start"
        assert "isForeground=true" in sh(f"dumpsys activity services {PKG}"), "recorder is not a foreground service"
        assert "stride_tracking" in sh("dumpsys notification --noredact | grep -A3 'pkg=" + PKG + "'"), "no tracking notification"
        if mock:
            step = 2.0 / 111320.0                  # 2 m of latitude per second, 300 m in total
            for i in range(1, 151):
                t = time.time()
                sh(f"cmd location providers set-test-provider-location gps --location {lat0 + step * i:.7f},{lon0} --accuracy 4")
                time.sleep(max(0, 1.0 - (time.time() - t)))
        else:
            time.sleep(8)
        e1 = _value(_state(), "elapsed")
        assert e1 >= 5000, f"timer is not advancing ({e1} ms)"
        # The live map animates continuously, so uiautomator cannot read this screen: use its fixed button
        # positions (bottom sheet, 1080-wide screen) and check the result through the saved session state.
        W, H = [int(v) for v in re.search(r"(\d+)x(\d+)", sh("wm size")).groups()]
        y = H - int(H * .072); pause, resume, finish = (W // 2, y), (W // 4 + 10, y), (W * 3 // 4 - 10, y)
        shot("10_live")
        sh("input tap %d %d" % pause); time.sleep(1.5)
        p1 = _value(_state(), "elapsed"); time.sleep(3); p2 = _value(_state(), "elapsed")
        assert 'name="paused" value="true"' in _state() and p2 == p1, "pause did not stop the timer"
        sh("input tap %d %d" % resume); time.sleep(3)
        assert _value(_state(), "elapsed") > p2, "resume did not restart the timer"
        sh("input tap %d %d" % pause); time.sleep(1.5)
        km = _value(_state(), "distance"); points = _state().count("&quot;lat&quot;")
        sh("input tap %d %d" % finish)
        _confirm("Finish activity?", "Finish")
        assert wait("Session summary"), "summary screen did not open"
        time.sleep(2)
        shot("11_summary")
        assert '"active" value="false"' in _state(), "session still active after finish"
        assert scroll_to("Delete"), "no Delete action on the summary"
        tap("Delete", exact=True)
        _confirm("Delete this activity?", "Delete")
        if mock:
            assert 0.24 <= km <= 0.33, f"expected about 0.30 km, recorded {km:.3f} km"
            assert points >= 20, f"route has only {points} points"
            return f"mock walk 300 m, {km * 1000:.0f} m recorded, {points} route points"
        return f"real GPS: {km * 1000:.0f} m, {points} route points; mock route skipped (Shell is not the mock location app)"
    finally:
        if mock:
            sh("cmd location providers remove-test-provider gps")
        if '"active" value="true"' in _state():
            print("      note: a session was left running; open Kadamm and discard it")


TESTS = [test_launch, test_drawer, test_swipe, test_health_screens, test_sync, test_step_permission_states, test_widget, test_recording]

if __name__ == "__main__":
    wanted = sys.argv[1:]
    adb("logcat", "-c")
    failed = 0
    for t in TESTS:
        if wanted and t.__name__ not in wanted and t.__name__[5:] not in wanted:
            continue
        try:
            note = t()
            print(f"PASS  {t.__name__}" + (f"  ({note})" if note else ""))
        except Exception as e:
            failed += 1
            shot("FAIL_" + t.__name__)
            print(f"FAIL  {t.__name__}: {e}")
    crashes = fatal_lines()
    print(f"{'FAIL' if crashes else 'PASS'}  no crash or ANR in logcat" + (f": {crashes[:2]}" if crashes else ""))
    sys.exit(1 if failed or crashes else 0)
