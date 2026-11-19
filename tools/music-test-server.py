"""Local-only music prototype. No production configuration is modified.

Run: python tools/music-test-server.py
Optional environment: MUSIC_TEST_QQ_KEY, MUSIC_TEST_NETEASE_KEY.
"""
import argparse
import ipaddress
import json
import os
from pathlib import Path
import secrets
import socket
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


PROVIDERS = {
    "qq": "https://cyapi.top/API/qq_music.php",
    "netease": "https://cyapi.top/API/netease.php",
    "mitu": "https://www.qqmp3.vip/api/songs.php",
}
MEDIA_DOMAINS = ("qq.com", "qqmusic.qq.com", "qqmusic.tc.qq.com", "gtimg.cn",
                 "qpic.cn", "kuwo.cn", "126.net", "music.163.com")
TOKENS = {}
TOKEN_LOCK = threading.Lock()
MAX_JSON = 4 * 1024 * 1024


def load_workspace_keys():
    env_file = Path(__file__).resolve().parent.parent / ".env"
    if not env_file.is_file():
        return
    for line in env_file.read_text(encoding="utf-8-sig").splitlines():
        name, separator, value = line.partition("=")
        if separator and name.strip() in ("BLOG_MUSIC_API_KEY", "BLOG_MUSIC_NETEASE_API_KEY"):
            os.environ.setdefault("MUSIC_TEST_QQ_KEY" if name.strip() == "BLOG_MUSIC_API_KEY" else "MUSIC_TEST_NETEASE_KEY", value.strip().strip("\"'"))


def checked_url(url, api=False):
    parsed = urllib.parse.urlsplit(url)
    hostname = (parsed.hostname or "").lower()
    allowed = hostname in ("cyapi.top", "www.qqmp3.vip") if api else any(
        hostname == domain or hostname.endswith("." + domain) for domain in MEDIA_DOMAINS)
    if not allowed or parsed.scheme not in ("https", "http") or parsed.username or parsed.password:
        raise ValueError("上游地址不在允许的音乐服务范围内")
    if parsed.port not in (None, 80, 443):
        raise ValueError("上游端口不允许")
    addresses = socket.getaddrinfo(hostname, parsed.port or (443 if parsed.scheme == "https" else 80))
    if not addresses or any(not ipaddress.ip_address(item[4][0]).is_global for item in addresses):
        raise ValueError("上游地址不可访问")
    return url


class SafeRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, response, code, message, headers, new_url):
        checked_url(new_url, api=urllib.parse.urlsplit(new_url).hostname in ("cyapi.top", "www.qqmp3.vip"))
        return super().redirect_request(request, response, code, message, headers, new_url)


OPENER = urllib.request.build_opener(SafeRedirect())


def upstream(url, range_header=None, api=False):
    checked_url(url, api=api)
    host = urllib.parse.urlsplit(url).hostname
    referer = "https://www.qqmp3.vip/" if host == "www.qqmp3.vip" or "kuwo" in host else "https://y.qq.com/"
    headers = {"User-Agent": "Mozilla/5.0 MusicPrototype/1.0", "Referer": referer,
               "Accept": "*/*", "Accept-Encoding": "identity"}
    if range_header:
        headers["Range"] = range_header
    return OPENER.open(urllib.request.Request(url, headers=headers), timeout=18)


def api_json(url):
    try:
        with upstream(url, api=True) as response:
            body = response.read(MAX_JSON + 1)
            if len(body) > MAX_JSON:
                raise ValueError("接口响应过大")
            result = json.loads(body)
    except urllib.error.HTTPError as error:
        if error.code in (401, 403):
            raise ValueError("接口拒绝访问（HTTP %s），请检查该源的 Key 或接口权限" % error.code) from None
        raise ValueError("上游接口 HTTP %s" % error.code) from None
    except (json.JSONDecodeError, UnicodeDecodeError):
        raise ValueError("上游返回的不是 JSON") from None
    if isinstance(result, dict) and result.get("error"):
        # Do not forward provider messages that could contain credentials.
        raise ValueError("接口返回错误，请检查该源的 Key、参数或服务状态")
    return result


def issue_media(url):
    if not url:
        return ""
    checked_url(url)
    with TOKEN_LOCK:
        now = time.time()
        for token in list(TOKENS):
            if TOKENS[token][1] < now:
                del TOKENS[token]
        if len(TOKENS) >= 2000:
            raise ValueError("测试会话过多，请稍后重试")
        token = secrets.token_urlsafe(24)
        TOKENS[token] = (url, now + 3600)
    return "/media/" + token


def get_key(source, supplied):
    env_name = "MUSIC_TEST_QQ_KEY" if source == "qq" else "MUSIC_TEST_NETEASE_KEY"
    return supplied or os.environ.get(env_name, "")


def call_provider(operation, data):
    source = data.get("source")
    if source not in PROVIDERS:
        raise ValueError("未知音乐源")
    keyword = str(data.get("keyword", "")).strip()[:100]
    limit = max(1, min(20, int(data.get("limit", 10))))
    if source == "mitu":
        if operation == "search":
            payload = api_json(PROVIDERS[source] + "?" + urllib.parse.urlencode({"type": "search", "keyword": keyword}))
            items = payload.get("data", [])
            if not isinstance(items, list):
                raise ValueError("米兔未返回歌曲列表")
            return {"list": [{"id": str(item.get("rid", "")), "name": item.get("name", ""),
                              "artist": item.get("artist", ""), "cover": issue_media(item.get("pic", ""))}
                             for item in items[:limit]]}
        rid = str(data.get("id", ""))
        if not rid.isdigit():
            raise ValueError("米兔歌曲 ID 无效")
        payload = api_json("https://www.qqmp3.vip/api/kw.php?" + urllib.parse.urlencode({"rid": rid}))
        track = payload.get("data") or {}
        if not isinstance(track, dict) or not track.get("url"):
            raise ValueError("米兔未返回可播放音频")
        return {"url": issue_media(track["url"]), "lyric": track.get("lrc", "")}
    key = get_key(source, str(data.get("key", "")))
    if not key:
        raise ValueError("未填写该源 API Key")
    params = {"apikey": key, "msg": keyword, "num": limit, "type": "json"}
    if operation == "resolve":
        index = int(data.get("index", 0))
        if not 1 <= index <= limit:
            raise ValueError("歌曲序号无效")
        params["n"] = index
        if source == "qq" and data.get("id"):
            params["mid"] = str(data["id"])
    payload = api_json(PROVIDERS[source] + "?" + urllib.parse.urlencode(params))
    if operation == "search":
        if not isinstance(payload.get("list"), list):
            raise ValueError("接口未返回歌曲列表")
        for item in payload["list"]:
            cover = item.get("cover", "")
            if isinstance(cover, dict):
                cover = cover.get("large") or cover.get("medium") or cover.get("small") or ""
            item["cover"] = issue_media(cover)
        return payload
    if not payload.get("url"):
        raise ValueError("接口未返回可播放音频")
    payload["url"] = issue_media(payload["url"])
    cover = payload.get("cover", "")
    if isinstance(cover, dict):
        cover = cover.get("large") or cover.get("medium") or cover.get("small") or ""
    payload["cover"] = issue_media(cover)
    return payload


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, *_args):
        pass  # URLs, request bodies and credentials never go to access logs.

    def allowed_request(self):
        host = self.headers.get("Host", "")
        origin = self.headers.get("Origin")
        return host in ("127.0.0.1:%d" % self.server.server_port, "localhost:%d" % self.server.server_port) and (
            not origin or origin == "http://" + host)

    def reply(self, status, body, content_type="application/json; charset=utf-8"):
        if not isinstance(body, bytes):
            body = json.dumps(body, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.end_headers()
        try:
            self.wfile.write(body)
        except (BrokenPipeError, ConnectionResetError, ConnectionAbortedError):
            pass

    def do_GET(self):
        if not self.allowed_request():
            return self.reply(403, {"error": "仅允许本机同源访问"})
        path = urllib.parse.urlsplit(self.path).path
        if path in ("/", "/music-player-test.html"):
            return self.reply(200, Path(__file__).with_name("music-player-test.html").read_bytes(), "text/html; charset=utf-8")
        if path == "/api/config":
            return self.reply(200, {"qq": bool(get_key("qq", "")), "netease": bool(get_key("netease", ""))})
        if path.startswith("/media/"):
            with TOKEN_LOCK:
                entry = TOKENS.get(path.removeprefix("/media/"))
            if not entry or entry[1] < time.time():
                return self.reply(410, {"error": "链接已过期，请重新搜索"})
            response = None
            try:
                response = upstream(entry[0], self.headers.get("Range"))
                content_type = response.headers.get("Content-Type", "application/octet-stream")
                if not content_type.startswith(("audio/", "image/", "application/octet-stream", "video/mp4")):
                    response.close()
                    return self.reply(502, {"error": "上游未返回音乐或图片"})
                self.send_response(response.status)
                for name in ("Content-Type", "Content-Length", "Content-Range", "Accept-Ranges"):
                    if response.headers.get(name):
                        self.send_header(name, response.headers[name])
                self.send_header("Cache-Control", "private, max-age=300")
                self.send_header("X-Content-Type-Options", "nosniff")
                if not response.headers.get("Content-Length"):
                    self.send_header("Connection", "close")
                    self.close_connection = True
                self.end_headers()
                while chunk := response.read(64 * 1024):
                    self.wfile.write(chunk)
            except (BrokenPipeError, ConnectionResetError, ConnectionAbortedError):
                pass
            except urllib.error.HTTPError as error:
                self.reply(error.code, {"error": "上游音频或封面暂不可用"})
            except Exception:
                if response is None:
                    self.reply(502, {"error": "上游音频或封面连接失败"})
                else:
                    self.close_connection = True
            finally:
                if response:
                    response.close()
            return
        self.reply(404, {"error": "路径不存在"})

    def do_POST(self):
        if not self.allowed_request():
            return self.reply(403, {"error": "仅允许本机同源访问"})
        if self.path not in ("/api/search", "/api/resolve"):
            return self.reply(404, {"error": "路径不存在"})
        if not self.headers.get("Content-Type", "").startswith("application/json"):
            return self.reply(415, {"error": "请使用 JSON 请求"})
        try:
            size = int(self.headers.get("Content-Length", "0"))
            if not 0 < size <= 8192:
                raise ValueError("请求大小无效")
            data = json.loads(self.rfile.read(size))
            result = call_provider(self.path.rsplit("/", 1)[1], data)
            self.reply(200, result)
        except (ValueError, TypeError, KeyError):
            # ValueError messages above are safe; JSON decoding messages need no request data.
            import sys
            error = sys.exc_info()[1]
            message = str(error) if isinstance(error, ValueError) and not isinstance(error, json.JSONDecodeError) else "请求格式无效"
            self.reply(400, {"error": message})
        except Exception:
            self.reply(502, {"error": "上游请求失败或超时，请稍后重试"})


if __name__ == "__main__":
    load_workspace_keys()
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", type=int, default=8765)
    args = parser.parse_args()
    server = ThreadingHTTPServer(("127.0.0.1", args.port), Handler)
    server.daemon_threads = True
    print("音乐独立测试页：http://127.0.0.1:%d/" % args.port, flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
