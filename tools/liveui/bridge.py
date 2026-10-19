#!/usr/bin/env python3
"""LiveUI bridge — serves the app screenshot and turns browser clicks into taps.

The screenshot is produced by tools/liveui/loop.sh (Robolectric renders the real
Compose UI off-screen). This process only relays: GET / -> page, GET /screen.png
-> current render, POST /cmd -> writes cmd.json for the render loop.

Served on LIVEUI_PORT (default 12000). No app code lives here.
"""
import json
import os
import pathlib
import threading
import time

from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

DIR = pathlib.Path(os.environ.get("LIVEUI_DIR", "/tmp/liveui"))
DIR.mkdir(parents=True, exist_ok=True)
PORT = int(os.environ.get("LIVEUI_PORT", "12000"))
CMD = DIR / "cmd.json"
IMG = DIR / "screen.png"
STATUS = DIR / "status.txt"

_lock = threading.Lock()
_seq = 0

PAGE = """<!doctype html><html><head><meta charset="utf-8">
<title>SoundStage Live UI</title>
<style>
 html,body{margin:0;height:100%;background:#080b11;color:#e9edf5;
   font-family:Inter,'Segoe UI',system-ui,sans-serif}
 .bar{display:flex;flex-wrap:wrap;gap:6px;padding:10px 12px;align-items:center}
 .bar b{font-size:12px;color:#7b859a;letter-spacing:1px;margin-right:4px}
 button{background:linear-gradient(180deg,#283144,#1d2433);color:#dde3ee;border:1px solid rgba(255,255,255,.08);
   border-radius:10px;padding:7px 12px;font-size:12.5px;font-weight:700;cursor:pointer}
 button:hover{border-color:#8fb2e8}
 button.on{background:linear-gradient(180deg,#8fb2e8,#7fa3dd);color:#0f1726}
 .stage{display:flex;justify-content:center;padding:0 12px 16px}
 .wrap{position:relative;line-height:0;border-radius:14px;overflow:hidden;
   box-shadow:0 0 0 2px #1b212c,0 18px 50px rgba(0,0,0,.6)}
 img{display:block;max-width:100%;image-rendering:auto}
 .hud{position:fixed;left:12px;bottom:10px;font-size:11px;color:#59647a}
</style></head><body>
<div class="bar">
  <b>PAGES</b>
  <button data-page="MIXER">Mixer</button>
  <button data-page="DRUMPAD">DrumPad</button>
  <button data-page="SHEETS">Notes</button>
  <button data-page="SETLISTS">Setlists</button>
  <button data-page="SETTINGS">Settings</button>
  <button data-popup="NONE">Close popup</button>
  <span style="width:14px"></span><b>POPUPS</b>
  <button data-popup="SCENE">Scenes</button>
  <button data-popup="SOUNDFONT">SoundFont</button>
  <button data-popup="TRACK_SETTINGS">Track Settings</button>
  <button data-popup="EFFECTS">Effects</button>
  <button data-popup="MIDI">MIDI</button>
</div>
<div class="stage"><div class="wrap"><img id="shot" src="/screen.png"></div></div>
<div class="hud"><span id="hud">connecting…</span> · click directly on the image to tap the app</div>
<script>
const img=document.getElementById('shot'), hud=document.getElementById('hud');
let busy=false, pending=null;
function poll(){
  if(busy){requestAnimationFrame(poll);return}
  busy=true;
  fetch('/screen.png?t='+Date.now()).then(r=>r.blob()).then(b=>new Promise(res=>{
    const u=URL.createObjectURL(b), p=new Image();
    p.onload=()=>{img.src=u; img.dataset.w=p.naturalWidth; img.dataset.h=p.naturalHeight;
      if(pending){URL.revokeObjectURL(u);}
      res()};
    p.onerror=()=>{res()};
  })).catch(()=>{}).finally(()=>{busy=false; setTimeout(poll,250)});
}
fetch('/status').then(r=>r.text()).then(t=>{hud.textContent=t}).catch(()=>{});
setInterval(()=>fetch('/status').then(r=>r.text()).then(t=>{hud.textContent=t}).catch(()=>{}),1000);
function send(body){return fetch('/cmd',{method:'POST',headers:{'Content-Type':'application/json'},
  body:JSON.stringify(body)}).then(r=>r.json()).catch(()=>{})}
document.querySelectorAll('button').forEach(b=>b.onclick=()=>{
  if(b.dataset.page) send({page:b.dataset.page});
  else if(b.dataset.popup) send({popup:b.dataset.popup});
});
img.addEventListener('click',e=>{
  const r=img.getBoundingClientRect();
  const sx=parseFloat(img.dataset.w||img.naturalWidth)/r.width;
  const sy=parseFloat(img.dataset.h||img.naturalHeight)/r.height;
  const x=Math.round((e.clientX-r.left)*sx), y=Math.round((e.clientY-r.top)*sy);
  hud.textContent='tap '+x+','+y;
  send({clicks:[[x,y]]});
});
poll();
</script></body></html>
"""


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def _send(self, code, ctype, body, extra=None):
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(body)))
        for k, v in (extra or {}).items():
            self.send_header(k, v)
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        path = self.path.split("?")[0]
        if path in ("/", "/index.html"):
            self._send(200, "text/html; charset=utf-8", PAGE.encode())
        elif path == "/screen.png":
            for _ in range(200):
                if IMG.exists():
                    break
                time.sleep(0.1)
            if IMG.exists():
                self._send(200, "image/png", IMG.read_bytes())
            else:
                self._send(503, "text/plain", b"no render yet")
        elif path == "/status":
            txt = STATUS.read_text().strip() if STATUS.exists() else "no status"
            age = int(time.time() - IMG.stat().st_mtime) if IMG.exists() else -1
            self._send(200, "text/plain", f"{txt} · render age {age}s".encode())
        else:
            self._send(404, "text/plain", b"not found")

    def do_POST(self):
        global _seq
        if self.path.split("?")[0] != "/cmd":
            self._send(404, "text/plain", b"not found")
            return
        n = int(self.headers.get("Content-Length", 0))
        raw = self.rfile.read(n) if n else b"{}"
        try:
            body = json.loads(raw or b"{}")
        except Exception:
            body = {}
        with _lock:
            _seq += 1
            body["seq"] = _seq
            CMD.write_text(json.dumps(body))
        self._send(200, "application/json", json.dumps({"ok": True, "seq": _seq}).encode())


if __name__ == "__main__":
    srv = ThreadingHTTPServer(("0.0.0.0", PORT), Handler)
    print(f"LiveUI bridge on :{PORT} (dir={DIR})", flush=True)
    srv.serve_forever()
