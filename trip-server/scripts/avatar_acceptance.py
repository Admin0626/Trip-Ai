"""Real local avatar HTTP/SQL acceptance; requires Pillow, mysql and redis-cli.

Creates one dedicated user. Removes only its rows/session keys and the exact
uploaded files whose bytes match this run. Evidence includes redacted JSON
responses; binary responses are represented by length/hash/content headers.
"""
import argparse
import datetime as dt
import hashlib
import io
import json
import os
from pathlib import Path
import re
import struct
import subprocess
import urllib.error
import urllib.parse
import urllib.request
import uuid
import zlib

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]


def redact(value):
    if isinstance(value, dict):
        return {k: "<redacted>" if k.lower() in {
            "password", "oldpassword", "newpassword", "accesstoken",
            "refreshtoken", "authorization", "token",
        } else redact(v) for k, v in value.items()}
    if isinstance(value, list):
        return [redact(v) for v in value]
    return value


class Acceptance:
    def __init__(self, args):
        self.base = args.base.rstrip("/")
        if urllib.parse.urlsplit(self.base).hostname not in {"localhost", "127.0.0.1"}:
            raise ValueError("Only a local test application is supported")
        self.upload_dir = Path(args.upload_dir).resolve()
        if not self.upload_dir.is_relative_to(ROOT) or self.upload_dir == ROOT:
            raise ValueError("Upload cleanup directory must be inside this repository")
        self.username = "avatar_" + uuid.uuid4().hex[:12]
        self.token = ""
        self.uid = None
        self.created = False
        self.files = {}
        self.report = {"mode": "REAL_LOCAL_AVATAR_HTTP_MYSQL_REDIS", "label": args.label,
                       "startedAt": dt.datetime.now().astimezone().isoformat(), "checks": []}
        stamp = dt.datetime.now().strftime("%Y%m%d-%H%M%S-%f")
        self.out = ROOT / "docs/dev/evidence/avatar" / (stamp + "-" + args.label + ".json")

    def save(self):
        checks = self.report["checks"]
        self.report["summary"] = {"total": len(checks), "passed": sum(c["passed"] for c in checks),
                                  "failed": sum(not c["passed"] for c in checks)}
        self.out.parent.mkdir(parents=True, exist_ok=True)
        self.out.write_text(json.dumps(redact(self.report), ensure_ascii=False, indent=2), encoding="utf-8")

    def check(self, name, actual, expected, **evidence):
        self.report["checks"].append({"name": name, "actual": actual, "expected": expected,
                                       "passed": actual == expected, **evidence})
        self.save()

    def sql(self, query):
        return subprocess.check_output(
            ["mysql", "-u", "root", "-N", "-B", "--default-character-set=utf8mb4", "trip_llm", "-e", query],
            text=True, encoding="utf-8", env={**os.environ, "MYSQL_PWD": os.getenv("MYSQL_PASSWORD", "123456")}).strip()

    def call(self, name, path, *, method="GET", body=None, binary=None, mime=None,
             expected=(200, 200), auth=True):
        headers = {}
        payload = binary
        if body is not None:
            payload = json.dumps(body).encode()
            headers["Content-Type"] = "application/json"
        if mime:
            headers["Content-Type"] = mime
        if auth and self.token:
            headers["Authorization"] = "Bearer " + self.token
        req = urllib.request.Request(self.base + path, payload, headers, method=method)
        try:
            response = urllib.request.urlopen(req, timeout=30)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            raw = response.read()
            status = response.status
            content_type = response.headers.get("Content-Type", "")
            selected_headers = {k: response.headers.get(k) for k in
                                ["Content-Type", "Content-Length", "X-Content-Type-Options"]}
        data = json.loads(raw) if "json" in content_type else None
        response_body = data if data is not None else {
            "bytes": len(raw), "sha256": hashlib.sha256(raw).hexdigest()}
        actual = [status, data.get("code") if data is not None else None]
        self.check(name, actual, list(expected), request={"method": method, "path": path,
                   "body": body, "binaryBytes": len(binary) if binary is not None else None},
                   response={"status": status, "headers": selected_headers, "body": response_body})
        return data, raw, selected_headers

    def upload(self, name, content, filename="image.png", mime="image/png", **kwargs):
        boundary = "avatar-" + uuid.uuid4().hex
        payload = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{filename}"\r\n'
                   f'Content-Type: {mime}\r\n\r\n').encode() + content + f"\r\n--{boundary}--\r\n".encode()
        data, _, _ = self.call(name, "/file/upload", method="POST", binary=payload,
                              mime="multipart/form-data; boundary=" + boundary, **kwargs)
        if data and data.get("code") == 200:
            result = data["data"]
            if not re.fullmatch(r"[a-f0-9]{32}\.(png|jpg)", result["name"]):
                raise RuntimeError("Unexpected upload filename; refusing filesystem cleanup")
            self.files[result["name"]] = hashlib.sha256(content).hexdigest()
            self.check(name + " generated path", result["url"], "/api/files/" + result["name"])
            return result
        return None

    def run(self):
        credentials = {"username": self.username, "password": "AvatarTest123", "nickname": "头像验收"}
        png, jpeg = image_bytes("PNG"), image_bytes("JPEG")
        try:
            # Preflight database before creating any records.
            self.sql("SELECT 1")
            self.call("anonymous upload denied", "/file/upload", method="POST", expected=(401, 401), auth=False)
            data, _, _ = self.call("register", "/auth/register", method="POST", body=credentials, auth=False)
            self.created = data is not None and data.get("code") == 200
            if not self.created:
                raise RuntimeError("Temporary registration failed")
            self.uid = int(self.sql(f"SELECT id FROM sys_user WHERE username='{self.username}'"))
            data, _, _ = self.call("login", "/auth/login", method="POST", body=credentials, auth=False)
            self.token = data["data"]["accessToken"]
            for label, content, filename, mime in [
                ("PNG", png, "avatar.png", "image/png"),
                ("JPEG", jpeg, "avatar.jpeg", "image/jpeg"),
            ]:
                uploaded = self.upload(label + " upload", content, filename, mime)
                if uploaded is None:
                    raise RuntimeError("Valid image upload failed")
                url = uploaded["url"]
                saved, _, _ = self.call(label + " save avatar", "/user/profile", method="PUT", body={"avatar": url})
                self.check(label + " save response avatar", (saved.get("data") or {}).get("avatar"), url)
                profile, _, _ = self.call(label + " reread profile", "/user/profile")
                self.check(label + " persisted HTTP avatar", profile["data"]["avatar"], url)
                self.check(label + " persisted SQL avatar", self.sql(f"SELECT avatar FROM sys_user WHERE id={self.uid}"), url)
                _, actual, headers = self.call(label + " public image", url.removeprefix("/api"),
                                               expected=(200, None), auth=False)
                self.check(label + " image bytes unchanged", hashlib.sha256(actual).hexdigest(), hashlib.sha256(content).hexdigest())
                self.check(label + " content headers", [headers["Content-Type"], headers["X-Content-Type-Options"]], [mime, "nosniff"])
            disguised = self.upload("PNG named JPEG uses decoded format", png, "pretend.jpg", "image/jpeg")
            self.check("decoded PNG extension", disguised["name"].endswith(".png"), True)
            self.upload("empty image rejected", b"", expected=(200, 400))
            self.upload("fake PNG text rejected", b"this is not an image", expected=(200, 400))
            self.upload("GIF rejected", image_bytes("GIF"), "image.gif", "image/gif", expected=(200, 400))
            self.upload("truncated PNG rejected", png[:33], expected=(200, 400))
            self.upload("width over 6000 rejected", png_header(6001, 1), expected=(200, 400))
            self.upload("pixels over 20 million rejected", png_header(5000, 5000), expected=(200, 400))
            padded = png + bytes(5 * 1024 * 1024 - len(png))
            self.upload("exactly 5 MiB accepted", padded)
            self.upload("over 5 MiB rejected", padded + b"x", expected=(200, 400))
            self.call("missing multipart file rejected", "/file/upload", method="POST", binary=b"--empty--\r\n",
                      mime="multipart/form-data; boundary=empty", expected=(200, 400))
            for label, avatar in [
                ("legacy 36 character name", "/api/files/" + str(uuid.uuid4()) + ".png"),
                ("unsupported jpeg suffix", "/api/files/" + "a" * 32 + ".jpeg"),
                ("uppercase name", "/api/files/" + "A" * 32 + ".png"),
                ("path traversal", "/api/files/../application.yml"),
                ("javascript URL", "javascript:alert(1)"),
                ("URL with userinfo", "https://user:pass@example.com/avatar.png"),
            ]:
                self.call(label + " avatar rejected", "/user/profile", method="PUT", body={"avatar": avatar}, expected=(200, 400))
            self.call("invalid file name not served", "/files/not-an-image.png", expected=(404, None), auth=False)
            self.call("missing file not served", "/files/" + uuid.uuid4().hex + ".png", expected=(404, None), auth=False)
            self.call("HTTPS avatar supported", "/user/profile", method="PUT", body={"avatar": "https://example.com/avatar.png"})
            saved, _, _ = self.call("clear avatar", "/user/profile", method="PUT", body={"avatar": ""})
            self.check("clear avatar response", saved["data"]["avatar"], "")
            self.check("avatar cleared in SQL", self.sql(f"SELECT avatar FROM sys_user WHERE id={self.uid}"), "")
            email = self.username + "@example.com"
            changed = {"nickname": "Updated avatar tester", "city": "Hangzhou", "email": email}
            saved, _, _ = self.call("other profile fields save", "/user/profile", method="PUT", body=changed)
            self.check("profile save response reflects changes", {k: saved["data"][k] for k in changed}, changed)
            saved, _, _ = self.call("partial update clears nullable email", "/user/profile", method="PUT", body={"email": ""})
            self.check("cleared email and preserved nickname in response", [saved["data"]["email"], saved["data"]["nickname"]], [None, changed["nickname"]])
            self.check("cleared email in SQL", self.sql(f"SELECT email IS NULL FROM sys_user WHERE id={self.uid}"), "1")
        except Exception as error:
            self.check("execution completed without exception", type(error).__name__, "no exception")
        finally:
            self.cleanup()
            self.report["finishedAt"] = dt.datetime.now().astimezone().isoformat()
            self.save()
        summary = self.report["summary"]
        print(f'{summary["passed"]}/{summary["total"]} passed; {self.out}')
        return int(summary["failed"] > 0)

    def cleanup(self):
        # Each operation is independent so one cleanup failure does not skip others.
        def attempt(label, fn):
            try:
                fn()
                self.check(label, True, True)
            except Exception as error:
                self.check(label, type(error).__name__, True)
        if self.created:
            def clean_user():
                if self.uid is None:
                    self.uid = int(self.sql(f"SELECT id FROM sys_user WHERE username='{self.username}'"))
                self.sql(f"DELETE FROM sys_user WHERE id={self.uid} AND username='{self.username}'")
                if self.sql(f"SELECT COUNT(*) FROM sys_user WHERE id={self.uid}") != "0":
                    raise RuntimeError("Temporary user still exists")
            attempt("temporary user cleaned", clean_user)
        if self.uid is not None:
            def clean_sessions():
                from redis_fixture import clean_sessions as clean_redis_sessions
                clean_redis_sessions(self.uid)
            attempt("temporary Redis sessions cleaned", clean_sessions)
        for name, digest in self.files.items():
            def clean_file(name=name, digest=digest):
                path = (self.upload_dir / name).resolve()
                if path.parent != self.upload_dir or hashlib.sha256(path.read_bytes()).hexdigest() != digest:
                    raise RuntimeError("Upload cleanup path or content mismatch")
                path.unlink()
            attempt("uploaded file cleaned: " + name, clean_file)


def image_bytes(format):
    output = io.BytesIO()
    Image.new("RGB", (3, 2), (30, 120, 200)).save(output, format=format)
    return output.getvalue()


def png_header(width, height):
    # Only a header is needed: dimensions must be rejected before pixel decoding.
    chunk = b"IHDR" + struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    return b"\x89PNG\r\n\x1a\n" + struct.pack(">I", 13) + chunk + struct.pack(">I", zlib.crc32(chunk))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", default="http://127.0.0.1:8080/api")
    parser.add_argument("--upload-dir", default=str(ROOT / "trip-server/uploads"))
    parser.add_argument("--label", default="fixed", choices=["before", "fixed"])
    raise SystemExit(Acceptance(parser.parse_args()).run())
