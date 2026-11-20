"""Local standalone page using the real backend. Run python tools/music-test-server.py.

Keys are configured in the blog dashboard, never entered or stored by this tool.
"""
import argparse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import urllib.error
import urllib.parse
import urllib.request

class Handler(BaseHTTPRequestHandler):
    def log_message(self, *_args):
        pass

    def reply(self, status, body, kind):
        self.send_response(status)
        self.send_header('Content-Type', kind)
        self.send_header('Content-Length', str(len(body)))
        self.send_header('Cache-Control', 'no-store')
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        host = self.headers.get('Host', '')
        if host not in ('localhost:%d' % self.server.server_port, '127.0.0.1:%d' % self.server.server_port):
            return self.reply(403, b'Forbidden', 'text/plain')
        origin = self.headers.get('Origin')
        if origin and origin != 'http://' + host:
            return self.reply(403, b'Forbidden', 'text/plain')
        path = urllib.parse.urlsplit(self.path).path
        if path in ('/', '/music-player-test.html'):
            return self.reply(200, Path(__file__).with_name('music-player-test.html').read_bytes(), 'text/html; charset=utf-8')
        if path == '/musicSourceFallback.js':
            return self.reply(200, (Path(__file__).resolve().parents[1] / 'frontend/utils/musicSourceFallback.js').read_bytes(), 'text/javascript')
        if path not in ('/api/v1/music/search', '/api/v1/music/resolve', '/api/v1/music/stream', '/api/v1/music/cover'):
            return self.reply(404, b'Not found', 'text/plain')
        headers = {'Accept': '*/*'}
        if self.headers.get('Range'):
            headers['Range'] = self.headers['Range']
        started = False
        try:
            request = urllib.request.Request(self.server.backend + self.path, headers=headers)
            with urllib.request.urlopen(request, timeout=70) as response:
                self.send_response(response.status)
                for name in ('Content-Type', 'Content-Length', 'Content-Range', 'Accept-Ranges'):
                    if response.headers.get(name): self.send_header(name, response.headers[name])
                self.send_header('Connection', 'close'); self.end_headers(); started = True
                while chunk := response.read(65536): self.wfile.write(chunk)
        except (BrokenPipeError, ConnectionResetError):
            pass
        except urllib.error.HTTPError as error:
            if not started: self.reply(error.code, error.read(), 'application/json')
        except Exception:
            if not started: self.reply(502, b'{"message":"Backend unavailable"}', 'application/json')

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--port', type=int, default=8765)
    parser.add_argument('--backend', default='http://175.178.69.4')
    args = parser.parse_args()
    server = ThreadingHTTPServer(('127.0.0.1', args.port), Handler)
    server.backend = args.backend.rstrip('/')
    print('测试页：http://127.0.0.1:%d/' % args.port, flush=True)
    try: server.serve_forever()
    except KeyboardInterrupt: pass
    finally: server.server_close()
