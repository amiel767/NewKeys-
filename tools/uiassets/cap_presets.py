"""Capture every preset of a split preview, one PNG each.

Selects the preset through the preview's own API and waits for the background
crossfade to settle before screenshotting, so a capture never shows the
previous image mid-transition.
"""
import base64
import json
import os
import subprocess
import sys
import time
import urllib.request

import websocket

PORT = 9350


def cmd(ws, n, method, **params):
    ws.send(json.dumps({'id': n, 'method': method, 'params': params}))
    while True:
        r = json.loads(ws.recv())
        if r.get('id') == n:
            return r


def main(path, outdir, mode):
    os.makedirs(outdir, exist_ok=True)
    proc = subprocess.Popen(['chromium', '--headless', '--no-sandbox', '--disable-gpu',
                             f'--remote-debugging-port={PORT}', '--remote-allow-origins=*',
                             '--hide-scrollbars', '--window-size=1000,460', 'about:blank'],
                            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    time.sleep(4)
    try:
        tabs = json.load(urllib.request.urlopen(f'http://127.0.0.1:{PORT}/json'))
        ws = websocket.create_connection(
            [t for t in tabs if t['type'] == 'page'][0]['webSocketDebuggerUrl'], timeout=120)
        cmd(ws, 1, 'Page.enable')
        cmd(ws, 2, 'Runtime.enable')
        cmd(ws, 3, 'Page.navigate', url=f'file://{path}')
        time.sleep(10)
        cmd(ws, 4, 'Runtime.evaluate',
            expression="document.querySelector('#re')?.click();1")
        sel = 'selPiano' if mode == 'piano' else 'selStr'
        # names come from the rendered list, which is the real UI
        names = json.loads(cmd(ws, 5, 'Runtime.evaluate', returnByValue=True,
                               expression="JSON.stringify([...document.querySelectorAll('#list .row')].map(r=>r.textContent.trim()))"
                               )['result']['result'].get('value', '[]'))
        for i, m in enumerate(names):
            cmd(ws, 100 + i, 'Runtime.evaluate',
                expression=f"window.__x.{sel}({i});1")
            time.sleep(2.2)
            r = cmd(ws, 200 + i, 'Page.captureScreenshot', format='png')
            raw = base64.b64decode(r['result']['data'])
            out = f"{outdir}/{mode}_{i:02d}.png"
            open(out, 'wb').write(raw)
            print(f'  {out}  {len(raw) / 1024:6.0f} Ko  {m}')
    finally:
        proc.terminate()


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2], sys.argv[3])
