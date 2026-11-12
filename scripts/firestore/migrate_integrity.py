#!/usr/bin/env python3
"""Inspect/backfill integrity v2 using an explicitly supplied administrator token.

Read-only by default. Production writes require --apply; see docs/DATA_MODEL.md.
No client credentials or tokens are written to disk or printed.
"""

import argparse
import json
import os
import urllib.error
import urllib.parse
import urllib.request


class Firestore:
    def __init__(self, project, emulator=None):
        if emulator and not project.startswith("demo-"):
            raise ValueError("Emulator migration requires a demo- project")
        host = f"http://{emulator}" if emulator else "https://firestore.googleapis.com"
        self.base = f"{host}/v1/projects/{urllib.parse.quote(project, safe='')}/databases/(default)/documents"
        self.token = "owner" if emulator else os.environ.get("FIRESTORE_ADMIN_TOKEN")
        if not self.token:
            raise ValueError("Set FIRESTORE_ADMIN_TOKEN to an administrator access token")

    def request(self, method, suffix, body=None):
        raw = None if body is None else json.dumps(body).encode()
        request = urllib.request.Request(self.base + suffix, data=raw, method=method,
                                         headers={"Authorization": f"Bearer {self.token}",
                                                  "Content-Type": "application/json"})
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                return json.load(response)
        except urllib.error.HTTPError as error:
            # Do not echo response bodies, headers or URLs containing credentials.
            raise RuntimeError(f"Firestore {method} failed: HTTP {error.code}") from None

    def documents(self, path):
        documents = []
        token = None
        while True:
            query = {"pageSize": 300, "showMissing": "true"}
            if token:
                query["pageToken"] = token
            result = self.request("GET", "/" + urllib.parse.quote(path, safe="/") + "?"
                                  + urllib.parse.urlencode(query))
            documents.extend(result.get("documents", []))
            token = result.get("nextPageToken")
            if not token:
                return documents

    def update(self, document, values):
        fields = {}
        for key, value in values.items():
            fields[key] = ({"booleanValue": value} if isinstance(value, bool)
                           else {"integerValue": str(value)})
        self.request("POST", ":commit", {"writes": [{
            "update": {"name": document["name"], "fields": fields},
            "updateMask": {"fieldPaths": list(fields)},
            "currentDocument": {"updateTime": document["updateTime"]},
        }]})


def migrate(store, apply=False):
    summary = {"houses": 0, "parents": 0, "records": 0, "blocked_houses": 0}
    for house in store.documents("houses"):
        if "fields" not in house:
            # Missing parents can contain otherwise invisible orphan subcollections.
            summary["blocked_houses"] += 1
            continue
        path = house["name"].split("/documents/", 1)[1]
        if apply:
            # Deploy v2 rules FIRST. Version 1 pauses client mutations while we count.
            store.update(house, {"integrityVersion": 1})
        counts = {}
        blocked = False
        for collection, children in (("items", "history"), ("chores", "logs")):
            parents = store.documents(f"{path}/{collection}")
            counts[collection] = 0
            for parent in parents:
                parent_path = parent["name"].split("/documents/", 1)[1]
                records = store.documents(f"{parent_path}/{children}")
                if "fields" not in parent or any("fields" not in record for record in records):
                    blocked = True
                    continue
                fields = parent["fields"]
                deleting = fields.get("deleting", {"booleanValue": False})
                if set(deleting) != {"booleanValue"}:
                    blocked = True
                    continue
                counts[collection] += 1
                summary["parents"] += 1
                summary["records"] += len(records)
                if apply:
                    store.update(parent, {"recordCount": len(records),
                                          "deleting": deleting["booleanValue"]})
        members = store.documents(f"{path}/members")
        actual_members = [member for member in members if "fields" in member]
        owner = house["fields"].get("ownerId", {}).get("stringValue")
        if (len(actual_members) != len(members) or not 1 <= len(actual_members) <= 6
                or not any(member["name"].rsplit("/", 1)[-1] == owner for member in actual_members)):
            blocked = True
        summary["houses"] += 1
        if blocked:
            # Never delete or fabricate orphan records. A failed apply stays paused at version 1.
            summary["blocked_houses"] += 1
            continue
        if apply:
            # Refresh the update precondition after the maintenance marker write.
            current = store.request("GET", "/" + urllib.parse.quote(path, safe="/"))
            store.update(current, {"itemCount": counts["items"], "choreCount": counts["chores"],
                                   "memberCount": len(actual_members), "integrityVersion": 2})
    return summary


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project", required=True)
    parser.add_argument("--emulator", help="Explicit localhost emulator host:port (demo- projects only)")
    parser.add_argument("--apply", action="store_true", help="Backfill and activate v2 after deploying its rules")
    args = parser.parse_args()
    if args.emulator:
        host = args.emulator.rsplit(":", 1)[0]
        if host not in ("127.0.0.1", "localhost", "[::1]"):
            parser.error("--emulator must be a loopback host")
    try:
        summary = migrate(Firestore(args.project, args.emulator), args.apply)
        print(json.dumps({"mode": "apply" if args.apply else "inspect", **summary}))
        return 2 if summary["blocked_houses"] else 0
    except (ValueError, RuntimeError, OSError) as error:
        print(f"Migration stopped: {error}")
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
