#!/usr/bin/env python3
"""Checks tests/apps/web: WebView without a browser engine, and logins through the system browser.

    tools/build_apk.sh tests/apps/web
    mkdir -p build/shots/web
    build/host/switchapk-host --data build/data --screen 1280x720@240 \\
        --script tests/apps/web/web.script --screenshots build/shots/web \\
        build/apps/web/web.apk > build/shots/web/web.log 2>&1
    python3 tests/apps/web/check_shots.py build/shots/web

The asset page loads and renders (dark text on white at the top), the script taps its first link, the second page
loads and the app goes back, loadDataWithBaseURL shows an inline page; then AuthActivity opens the browser twice:
the script answers the first with the app's redirect URL (RedirectActivity hands the code over) and closes the
second (a cancel). The final screen is green.
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
from shotlib import check  # noqa: E402

GREEN = 0xFF43A047

LINES = [
    "I/WEB: user agent has Android: true",
    "I/WEB: cookies root=a=1; c=3 docs=b=2; a=1; c=3 http=a=1",
    "I/WEB: urlutil network=true asset=true name=report.pdf",
    "I/WEB: mime png=image/png ext=html",
    "I/WEB: browsers 1 com.android.browser",
    "I/WEB: title Help: Getting started",
    "I/WEB: finished file:///android_asset/index.html title=Help: Getting started back=false",
    "I/WEB: override file:///android_asset/page2.html gesture=true",
    "I/WEB: finished file:///android_asset/page2.html title=Second page back=true",
    "I/WEB: finished file:///android_asset/index.html title=Help: Getting started back=false",
    "I/WEB: title Inline",
    "I/WEB: webview done",
    "I/WEB: auth round 1 opening browser",
    "I/headless: browser open https://login.example/oauth2/auth?client_id=web&state=s1&redirect_uri=com.example.web%3A%2Foauth (callback com.example.web:/oauth)",
    "I/WEB: auth stopped",
    "I/WEB: redirect com.example.web:/oauth?code=abc&state=s1",
    "I/WEB: auth round 1 code=abc state=s1",
    "I/WEB: auth round 2 opening browser",
    "I/WEB: auth round 2 cancelled",
    "I/WEB: all ok",
]


def main():
    if len(sys.argv) != 2:
        print("usage: check_shots.py <shots-dir>")
        return 2
    d = sys.argv[1]
    text = open(os.path.join(d, "web.log"), encoding="utf-8", errors="replace").read()
    fails = 0
    for line in LINES:
        ok = line in text
        print("%s has %s" % ("ok  " if ok else "FAIL", line))
        fails += 0 if ok else 1
    fails += check(os.path.join(d, "1-index.png"), [(1200, 600, 0xFFFFFFFF, "page background")])
    fails += check(os.path.join(d, "2-auth.png"), [(640, 400, GREEN, "auth result")])
    print("ok   web" if fails == 0 else "FAIL web (%d)" % fails)
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
