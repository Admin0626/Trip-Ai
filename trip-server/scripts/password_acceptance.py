"""Real password/session acceptance against the local app, MySQL and Redis.

All accounts are temporary. Passwords, JWTs and password hashes are never saved
in evidence. Concurrent requests use the real server; no provider or API mocks.
"""
import argparse
from concurrent.futures import ThreadPoolExecutor
import datetime as dt
import json
import os
from pathlib import Path
import subprocess
from threading import Barrier
import urllib.error
import urllib.parse
import urllib.request
import uuid
from redis_fixture import clean_sessions, session_keys

ROOT = Path(__file__).resolve().parents[2]
SECRET_FIELDS = {"password", "oldpassword", "newpassword", "accesstoken", "refreshtoken", "token", "authorization"}


def redact(value):
    if isinstance(value, dict):
        return {k: "<redacted>" if k.lower() in SECRET_FIELDS else redact(v) for k, v in value.items()}
    if isinstance(value, list):
        return [redact(v) for v in value]
    return value


class Acceptance:
    def __init__(self, args):
        self.base = args.base.rstrip("/")
        if urllib.parse.urlsplit(self.base).hostname not in {"localhost", "127.0.0.1"}:
            raise ValueError("Only local test applications are supported")
        self.users = []
        self.report = {"mode": "REAL_LOCAL_PASSWORD_HTTP_MYSQL_REDIS", "label": args.label,
                       "startedAt": dt.datetime.now().astimezone().isoformat(), "checks": []}
        stamp = dt.datetime.now().strftime("%Y%m%d-%H%M%S-%f")
        self.out = ROOT / "docs/dev/evidence/password" / (stamp + "-" + args.label + ".json")

    def check(self, name, actual, expected, **evidence):
        self.report["checks"].append({"name": name, "actual": actual, "expected": expected,
                                       "passed": actual == expected, **evidence})
        self.save()

    def save(self):
        checks = self.report["checks"]
        self.report["summary"] = {"total": len(checks), "passed": sum(c["passed"] for c in checks),
                                  "failed": sum(not c["passed"] for c in checks)}
        self.out.parent.mkdir(parents=True, exist_ok=True)
        self.out.write_text(json.dumps(redact(self.report), ensure_ascii=False, indent=2), encoding="utf-8")

    def sql(self, query):
        return subprocess.check_output(["mysql", "-u", "root", "-N", "-B", "trip_llm", "-e", query],
                                       text=True, env={**os.environ, "MYSQL_PWD": os.getenv("MYSQL_PASSWORD", "123456")}).strip()

    def request(self, path, body=None, method="GET", token=""):
        headers = {"Content-Type": "application/json"}
        if token:
            headers["Authorization"] = "Bearer " + token
        payload = None if body is None else json.dumps(body).encode()
        request = urllib.request.Request(self.base + path, payload, headers, method=method)
        try:
            response = urllib.request.urlopen(request, timeout=30)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            return {"status": response.status, "body": json.loads(response.read())}

    def call(self, name, path, body=None, method="GET", token="", expected=(200, 200)):
        result = self.request(path, body, method, token)
        self.check(name, [result["status"], result["body"].get("code")], list(expected),
                   request={"method": method, "path": path, "body": body}, response=result)
        return result["body"]

    def register(self, suffix):
        name = "pw_" + uuid.uuid4().hex[:10] + suffix
        result = self.call("register " + suffix, "/auth/register", {"username": name, "password": "InitialPass123"}, "POST")
        if result["code"] != 200:
            raise RuntimeError("Temporary registration failed")
        # Track by the known generated name even if the ID lookup fails.
        user = {"name": name, "id": None}
        self.users.append(user)
        user["id"] = int(self.sql(f"SELECT id FROM sys_user WHERE username='{name}'"))
        return user

    def login(self, user, password, name, expected=(200, 200)):
        result = self.call(name, "/auth/login", {"username": user["name"], "password": password}, "POST", expected=expected)
        return result.get("data")

    def revoked(self, label, pair):
        self.call(label + " access rejected", "/user/profile", token=pair["accessToken"], expected=(401, 401))
        self.call(label + " refresh rejected", "/auth/refresh", {"refreshToken": pair["refreshToken"]}, "POST", expected=(200, 401))

    def simultaneous(self, paths):
        barrier = Barrier(len(paths))
        def send(item):
            barrier.wait(timeout=10)
            return self.request(**item)
        with ThreadPoolExecutor(max_workers=len(paths)) as pool:
            return list(pool.map(send, paths))

    def run(self):
        try:
            self.sql("SELECT 1")
            self.call("anonymous password change denied", "/user/password", {}, "PUT", expected=(401, 401))
            user, other, race = self.register("a"), self.register("b"), self.register("c")
            first = self.login(user, "InitialPass123", "login first device")
            second = self.login(user, "InitialPass123", "login second device")
            other_pair = self.login(other, "InitialPass123", "login unrelated user")
            token = first["accessToken"]
            original_hash = self.sql(f"SELECT password FROM sys_user WHERE id={user['id']}")
            self.check("database uses BCrypt", original_hash.startswith(("$2a$", "$2b$", "$2y$")) and len(original_hash) == 60, True)
            self.call("refresh token cannot authorize password change", "/user/password",
                      {"oldPassword": "InitialPass123", "newPassword": "UpdatedPass123"}, "PUT",
                      first["refreshToken"], expected=(401, 401))
            invalid = [
                ("wrong old password", {"oldPassword": "WrongPass123", "newPassword": "UpdatedPass123"}),
                ("same password", {"oldPassword": "InitialPass123", "newPassword": "InitialPass123"}),
                ("short password", {"oldPassword": "InitialPass123", "newPassword": "Abc1234"}),
                ("long password", {"oldPassword": "InitialPass123", "newPassword": "A" * 20 + "1"}),
                ("digits only", {"oldPassword": "InitialPass123", "newPassword": "12345678"}),
                ("letters only", {"oldPassword": "InitialPass123", "newPassword": "abcdefgh"}),
                ("blank password", {"oldPassword": "InitialPass123", "newPassword": "        "}),
                ("missing old password", {"newPassword": "UpdatedPass123"}),
                ("missing new password", {"oldPassword": "InitialPass123"}),
                ("numeric old password", {"oldPassword": 12345678, "newPassword": "UpdatedPass123"}),
                ("numeric new password", {"oldPassword": "InitialPass123", "newPassword": 12345678}),
                ("null new password", {"oldPassword": "InitialPass123", "newPassword": None}),
                ("array new password", {"oldPassword": "InitialPass123", "newPassword": ["UpdatedPass123"]}),
                ("other user ID", {"oldPassword": "InitialPass123", "newPassword": "UpdatedPass123", "userId": other["id"]}),
            ]
            for label, body in invalid:
                self.call(label + " rejected", "/user/password", body, "PUT", token, expected=(200, 400))
            self.check("invalid attempts leave password unchanged", self.sql(f"SELECT password FROM sys_user WHERE id={user['id']}") == original_hash, True)
            self.call("invalid attempts preserve first session", "/user/profile", token=token)
            self.call("invalid attempts preserve second session", "/user/profile", token=second["accessToken"])
            rotated = self.call("refresh second device before change", "/auth/refresh", {"refreshToken": second["refreshToken"]}, "POST")["data"]
            self.revoked("rotation invalidates second old pair", second)
            self.call("8 character new password accepted", "/user/password", {"oldPassword": "InitialPass123", "newPassword": "New12345"}, "PUT", token)
            self.check("successful change replaces BCrypt hash", self.sql(f"SELECT password FROM sys_user WHERE id={user['id']}") != original_hash, True)
            self.revoked("first device after change", first)
            self.revoked("second rotated device after change", rotated)
            self.revoked("second old device after change", second)
            self.call("unrelated user access unaffected", "/user/profile", token=other_pair["accessToken"])
            self.call("unrelated user refresh unaffected", "/auth/refresh", {"refreshToken": other_pair["refreshToken"]}, "POST")
            self.login(user, "InitialPass123", "old password login rejected", expected=(200, 1001))
            current = self.login(user, "New12345", "new password login succeeds")
            self.call("new session profile works", "/user/profile", token=current["accessToken"])
            refreshed = self.call("new session refresh works", "/auth/refresh", {"refreshToken": current["refreshToken"]}, "POST")["data"]
            self.call("20 character new password accepted", "/user/password",
                      {"oldPassword": "New12345", "newPassword": "A" * 19 + "1"}, "PUT", refreshed["accessToken"])
            self.revoked("new pair invalid after second change", refreshed)
            max_pair = self.login(user, "A" * 19 + "1", "20 character password login")
            self.call("change back to original password", "/user/password",
                      {"oldPassword": "A" * 19 + "1", "newPassword": "InitialPass123"}, "PUT", max_pair["accessToken"])
            self.revoked("original password does not revive original pair", first)
            self.check("same plaintext still produces new salt", self.sql(f"SELECT password FROM sys_user WHERE id={user['id']}") != original_hash, True)
            final_pair = self.login(user, "InitialPass123", "restored password fresh login")
            # Two independent sessions race to update the same password. Only one may win.
            a = self.login(race, "InitialPass123", "race device A login")
            b = self.login(race, "InitialPass123", "race device B login")
            candidates = ["ConcurrentPass123", "ConcurrentPass456"]
            results = self.simultaneous([{"path": "/user/password", "method": "PUT", "token": pair["accessToken"],
                                         "body": {"oldPassword": "InitialPass123", "newPassword": password}}
                                        for pair, password in zip([a, b], candidates)])
            self.check("concurrent change has exactly one winner", sum(r["body"]["code"] == 200 for r in results), 1, responses=results)
            self.check("concurrent loser rejected safely", all(r["body"]["code"] in {200, 400, 401, 409} for r in results), True)
            winner = next(i for i, result in enumerate(results) if result["body"]["code"] == 200)
            winning_pair = self.login(race, candidates[winner], "winning password authenticates")
            self.login(race, candidates[1-winner], "losing password rejected", expected=(200, 1001))
            self.revoked("race old pair A", a)
            self.revoked("race old pair B", b)
            # Redis compare-and-set must also allow only one refresh of a given pair.
            results = self.simultaneous([{"path": "/auth/refresh", "method": "POST",
                                         "body": {"refreshToken": winning_pair["refreshToken"]}} for _ in range(2)])
            self.check("concurrent refresh has one success and one rejection", sorted(r["body"]["code"] for r in results), [200, 401], responses=results)
            winner_pair = next(r["body"]["data"] for r in results if r["body"]["code"] == 200)
            self.call("winning refresh access works", "/user/profile", token=winner_pair["accessToken"])
            self.revoked("race refresh old pair", winning_pair)
            self.call("logout new session", "/auth/logout", method="POST", token=final_pair["accessToken"])
            self.revoked("logged out new pair", final_pair)
        except Exception as error:
            self.check("execution completed without exception", type(error).__name__, "no exception")
        finally:
            for user in self.users:
                try:
                    if user["id"] is None:
                        user["id"] = int(self.sql(f"SELECT id FROM sys_user WHERE username='{user['name']}'"))
                    self.sql(f"DELETE FROM sys_user WHERE id={user['id']} AND username='{user['name']}'")
                    self.check("temporary user removed", self.sql(f"SELECT COUNT(*) FROM sys_user WHERE id={user['id']}"), "0")
                    removed = clean_sessions(user["id"])
                    self.check("temporary session keys removed", session_keys(user["id"]), [], removedCount=removed)
                except Exception as error:
                    self.check("cleanup completed", type(error).__name__, "no error")
            self.report["finishedAt"] = dt.datetime.now().astimezone().isoformat()
            self.save()
        summary = self.report["summary"]
        print(f'{summary["passed"]}/{summary["total"]} passed; {self.out}')
        return int(summary["failed"] > 0)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", default="http://127.0.0.1:8080/api")
    parser.add_argument("--label", default="baseline", choices=["baseline", "final"])
    raise SystemExit(Acceptance(parser.parse_args()).run())
