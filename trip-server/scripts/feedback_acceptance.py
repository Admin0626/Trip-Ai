"""Actual feedback HTTP/SQL acceptance, using disposable users and an admin.

Reuses the local HTTP/redaction helpers from password_acceptance. Only the
generated admin account is promoted; existing users and feedback are untouched.
Non-fixture feedback rows in admin responses are omitted from stored evidence.
"""
import argparse
import datetime as dt
import json
import subprocess
import uuid
from password_acceptance import Acceptance as HttpAcceptance, ROOT


class Acceptance(HttpAcceptance):
    def __init__(self, args):
        super().__init__(args)
        self.report["mode"] = "REAL_LOCAL_FEEDBACK_HTTP_MYSQL_REDIS"
        self.out = ROOT / "docs/dev/evidence/feedback" / (dt.datetime.now().strftime("%Y%m%d-%H%M%S-%f") + "-" + args.label + ".json")

    def call(self, name, path, body=None, method="GET", token="", expected=(200, 200)):
        result = self.request(path, body, method, token)
        evidence = json.loads(json.dumps(result))
        data = evidence["body"].get("data")
        if isinstance(data, dict) and isinstance(data.get("records"), list):
            own = {user["id"] for user in self.users}
            data["records"] = [row if row.get("userId") in own else {"omitted": "non-fixture feedback"} for row in data["records"]]
        self.check(name, [result["status"], result["body"].get("code")], list(expected),
                   request={"method": method, "path": path, "body": body}, response=evidence)
        return result["body"]

    def create(self, name, token, **fields):
        result = self.call(name, "/feedback", {"type": "BUG", "title": name, "content": "Test feedback details",
                                               "contact": "fixture@example.com", "images": [], **fields}, "POST", token)
        if result["code"] != 200:
            raise RuntimeError("Fixture feedback creation failed")
        return result["data"]

    def run(self):
        try:
            self.sql("SELECT 1")
            self.call("anonymous create denied", "/feedback", {}, "POST", expected=(401, 401))
            self.call("anonymous mine denied", "/feedback/my/page", expected=(401, 401))
            self.call("anonymous admin denied", "/admin/feedback/page", expected=(401, 401))
            user, other, admin = self.register("fa"), self.register("fb"), self.register("fc")
            self.sql(f"UPDATE sys_user SET role='ADMIN' WHERE id={admin['id']} AND username='{admin['name']}'")
            token = self.login(user, "InitialPass123", "owner login")["accessToken"]
            other_token = self.login(other, "InitialPass123", "other owner login")["accessToken"]
            admin_token = self.login(admin, "InitialPass123", "admin login")["accessToken"]
            fid = self.create("owner feedback", token, title="  Fixture feedback  ", content="  Actual issue details  ")
            other_id = self.create("other feedback", other_token)
            self.check("owner and initial state in SQL", self.sql(f"SELECT CONCAT(user_id,':',status,':',title) FROM feedback WHERE id={fid}"), f"{user['id']}:0:Fixture feedback")
            own = self.call("owner list", "/feedback/my/page", token=token)["data"]
            self.check("owner sees only own feedback", [row["id"] for row in own["records"]], [fid])
            forged = self.call("query userId cannot select another user", f"/feedback/my/page?userId={other['id']}", token=token)["data"]
            self.check("forged query remains scoped", [row["id"] for row in forged["records"]], [fid])
            self.call("user cannot list admin feedback", "/admin/feedback/page", token=token, expected=(403, 403))
            self.call("user cannot reply to other feedback", f"/admin/feedback/{other_id}/reply",
                      {"expectedStatus": 0, "status": 1, "replyContent": "forged reply"}, "PUT", token, expected=(403, 403))
            self.check("unauthorized reply made no change", self.sql(f"SELECT status FROM feedback WHERE id={other_id}"), "0")
            for label, params in [("zero page", "current=0"), ("negative page size", "size=-1"), ("oversized page", "size=101"), ("noninteger page", "current=1.5")]:
                self.call(label + " rejected", "/feedback/my/page?" + params, token=token, expected=(200, 400))
            self.call("bad admin filter rejected", "/admin/feedback/page?status=4", token=admin_token, expected=(200, 400))
            valid = {"type": "BUG", "title": "Valid title", "content": "Valid body", "contact": "", "images": []}
            for label, fields in [
                ("blank title", {"title": "   "}), ("blank content", {"content": "\n\t"}),
                ("long title", {"title": "a" * 101}), ("long content", {"content": "a" * 2001}),
                ("long contact", {"contact": "a" * 101}), ("numeric contact", {"contact": 123}),
                ("numeric title", {"title": 123}), ("unsupported type", {"type": "ADMIN"}),
                ("external image", {"images": ["https://example.com/test.png"]}),
                ("traversal image", {"images": ["/api/files/../test.png"]}),
                ("null image", {"images": [None]}), ("too many images", {"images": ["/api/files/" + "a" * 32 + ".png"] * 4}),
            ]:
                self.call(label + " rejected", "/feedback", {**valid, **fields}, "POST", token, expected=(200, 400))
            for kind in ["FUNCTION", "CONTENT", "OTHER"]:
                self.create("valid type " + kind, token, type=kind)
            self.create("maximum lengths accepted", token, title="a" * 100, content="b" * 2000, contact="c" * 100)
            probe = self.create("state transition probe", token)
            before = self.sql(f"SELECT CONCAT(status,':',COALESCE(reply_content,'')) FROM feedback WHERE id={probe}")
            self.call("pending cannot jump to solved", f"/admin/feedback/{probe}/reply",
                      {"expectedStatus": 0, "status": 2, "replyContent": "must process first"}, "PUT", admin_token, expected=(200, 400))
            self.check("invalid transition unchanged", self.sql(f"SELECT CONCAT(status,':',COALESCE(reply_content,'')) FROM feedback WHERE id={probe}"), before)
            self.call("missing precondition rejected", f"/admin/feedback/{fid}/reply", {"status": 1, "replyContent": "missing expected state"}, "PUT", admin_token, expected=(200, 400))
            # Restore only this fixture for a stable baseline after intentionally rejected probes.
            self.sql(f"UPDATE feedback SET status=0,reply_content=NULL,reply_by=0,reply_time=NULL WHERE id={fid}")
            for label, fields in [
                ("empty reply", {"replyContent": " "}), ("long reply", {"replyContent": "x" * 2001}),
                ("string status", {"status": "1"}), ("float status", {"status": 1.5}),
                ("status zero", {"status": 0}), ("status four", {"status": 4}),
                ("string precondition", {"expectedStatus": "0"}), ("invalid precondition", {"expectedStatus": 4}),
            ]:
                self.call(label + " rejected", f"/admin/feedback/{probe}/reply", {"expectedStatus": 0, "status": 1, "replyContent": "valid reply", **fields}, "PUT", admin_token, expected=(200, 400))
            self.call("pending to processing", f"/admin/feedback/{fid}/reply",
                      {"expectedStatus": 0, "status": 1, "replyContent": "  Investigating  "}, "PUT", admin_token)
            self.check("processing stored with admin and reply time", self.sql(f"SELECT CONCAT(status,':',reply_content,':',reply_by,':',reply_time IS NOT NULL) FROM feedback WHERE id={fid}"), f"1:Investigating:{admin['id']}:1")
            self.call("stale administrator cannot close updated feedback", f"/admin/feedback/{fid}/reply",
                      {"expectedStatus": 0, "status": 3, "replyContent": "stale tab"}, "PUT", admin_token, expected=(200, 409))
            self.check("stale reply did not overwrite processing", self.sql(f"SELECT CONCAT(status,':',reply_content) FROM feedback WHERE id={fid}"), "1:Investigating")
            self.sql(f"UPDATE feedback SET status=1,reply_content='Investigating' WHERE id={fid}")
            self.call("processing cannot stay processing", f"/admin/feedback/{fid}/reply",
                      {"expectedStatus": 1, "status": 1, "replyContent": "overwrite note"}, "PUT", admin_token, expected=(200, 400))
            self.call("processing to solved", f"/admin/feedback/{fid}/reply",
                      {"expectedStatus": 1, "status": 2, "replyContent": "Issue fixed"}, "PUT", admin_token)
            terminal = self.sql(f"SELECT CONCAT(status,':',reply_content) FROM feedback WHERE id={fid}")
            for target in [1, 2, 3]:
                self.call(f"solved rejects target {target}", f"/admin/feedback/{fid}/reply",
                          {"expectedStatus": 2, "status": target, "replyContent": "overwrite terminal"}, "PUT", admin_token, expected=(200, 400))
            self.check("terminal reply immutable", self.sql(f"SELECT CONCAT(status,':',reply_content) FROM feedback WHERE id={fid}"), terminal)
            for start in [0, 1]:
                closed = self.create(f"closure from {start}", token)
                if start == 1:
                    self.call("begin processing before close", f"/admin/feedback/{closed}/reply", {"expectedStatus": 0, "status": 1, "replyContent": "Checking"}, "PUT", admin_token)
                self.call(f"state {start} to closed", f"/admin/feedback/{closed}/reply", {"expectedStatus": start, "status": 3, "replyContent": "Closed by admin"}, "PUT", admin_token)
                for target in [1, 2, 3]:
                    self.call(f"closed from {start} rejects target {target}", f"/admin/feedback/{closed}/reply", {"expectedStatus": 3, "status": target, "replyContent": "overwrite closed"}, "PUT", admin_token, expected=(200, 400))
            missing = int(self.sql("SELECT COALESCE(MAX(id),0)+100000 FROM feedback"))
            self.call("missing feedback returns 404", f"/admin/feedback/{missing}/reply", {"expectedStatus": 0, "status": 1, "replyContent": "Missing"}, "PUT", admin_token, expected=(200, 404))
            deleted = self.create("deleted fixture", token)
            self.sql(f"UPDATE feedback SET deleted=1 WHERE id={deleted}")
            self.call("deleted feedback cannot be replied", f"/admin/feedback/{deleted}/reply", {"expectedStatus": 0, "status": 1, "replyContent": "Deleted"}, "PUT", admin_token, expected=(200, 404))
            first = self.call("owner first page", "/feedback/my/page?size=2&current=1", token=token)["data"]
            second = self.call("owner second page", "/feedback/my/page?size=2&current=2", token=token)["data"]
            self.check("owner pagination has no overlap", bool({r["id"] for r in first["records"]} & {r["id"] for r in second["records"]}), False)
            own_all = self.call("owner sees admin reply", "/feedback/my/page?size=100", token=token)["data"]["records"]
            row = next(r for r in own_all if r["id"] == fid)
            self.check("owner receives solved reply and timestamp", [row["status"], row["replyContent"], bool(row["replyTime"])], [2, "Issue fixed", True])
            self.check("deleted feedback excluded from mine", deleted in [r["id"] for r in own_all], False)
            filtered = self.call("admin status filter", "/admin/feedback/page?status=2&size=100", token=admin_token)["data"]["records"]
            self.check("admin filter contains only solved", all(r["status"] == 2 for r in filtered), True)
            self.check("deleted feedback excluded from admin", deleted in [r["id"] for r in filtered], False)
            racing = self.create("concurrent admin reply", token)
            results = self.simultaneous([{"path": f"/admin/feedback/{racing}/reply", "method": "PUT", "token": admin_token,
                                         "body": {"expectedStatus": 0, "status": target, "replyContent": "winner " + str(target)}} for target in [1, 3]])
            self.check("concurrent stale replies have exactly one winner", sorted(r["body"]["code"] for r in results), [200, 409], responses=results)
            success = [i for i, r in enumerate(results) if r["body"]["code"] == 200]
            if len(success) == 1:
                target = [1, 3][success[0]]
                self.check("concurrent winner persisted", self.sql(f"SELECT CONCAT(status,':',reply_content) FROM feedback WHERE id={racing}"), f"{target}:winner {target}")
        except Exception as error:
            self.check("execution completed", type(error).__name__, "no exception")
        finally:
            for user in self.users:
                try:
                    if user["id"] is None:
                        user["id"] = int(self.sql(f"SELECT id FROM sys_user WHERE username='{user['name']}'"))
                    uid = user["id"]
                    self.sql(f"DELETE FROM feedback WHERE user_id={uid}; DELETE FROM sys_user WHERE id={uid} AND username='{user['name']}'")
                    self.check("temporary account and feedback removed", self.sql(f"SELECT (SELECT COUNT(*) FROM sys_user WHERE id={uid})+(SELECT COUNT(*) FROM feedback WHERE user_id={uid})"), "0")
                    from redis_fixture import clean_sessions,session_keys
                    clean_sessions(uid)
                    self.check("temporary session keys removed", session_keys(uid), [])
                except Exception as error:
                    self.check("cleanup completed", type(error).__name__, "no exception")
            self.save()
        summary = self.report["summary"]
        print(f'{summary["passed"]}/{summary["total"]} passed; {self.out}')
        return int(summary["failed"] > 0)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", default="http://127.0.0.1:8080/api")
    parser.add_argument("--label", default="baseline", choices=["baseline", "final"])
    raise SystemExit(Acceptance(parser.parse_args()).run())
