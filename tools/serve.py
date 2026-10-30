"""Serveur de recette avec anti-cache.

Python `http.server` n'envoie que `Last-Modified`. Sans `Cache-Control`, Chrome
applique un cache HEURISTIQUE (environ 10 % de l'age du fichier) et ressert
l'ancienne page sans revalider : on croit que rien n'a change alors que le
fichier est a jour. Ce serveur envoie `no-store` sur tout.

Usage : python3 tools/serve.py [port]   (defaut 12000, sert /workspace/dl/pub)
"""
import http.server
import os
import socketserver
import sys


class NoCache(http.server.SimpleHTTPRequestHandler):
    def end_headers(self):
        self.send_header("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0")
        self.send_header("Pragma", "no-cache")
        self.send_header("Expires", "0")
        super().end_headers()

    def log_message(self, *args):
        pass


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 12000
    root = "/workspace/dl/pub"
    os.chdir(root if os.path.isdir(root) else os.getcwd())
    socketserver.TCPServer.allow_reuse_address = True
    with socketserver.TCPServer(("0.0.0.0", port), NoCache) as httpd:
        print(f"serve {os.getcwd()} on {port} (no-store)")
        httpd.serve_forever()
