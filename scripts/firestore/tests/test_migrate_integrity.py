import copy
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("migration", Path(__file__).parents[1] / "migrate_integrity.py")
migration = importlib.util.module_from_spec(spec)
spec.loader.exec_module(migration)


class Store:
    """Admin storage seam; fail one write to exercise restart after a partial backfill."""

    def __init__(self):
        root = "projects/demo-home-timing/databases/(default)/documents/"
        self.docs = {}
        for path, fields in {
            "houses/home": {"ownerId": {"stringValue": "alice"}, "itemCount": {"integerValue": "0"}},
            "users/alice": {"currentHouseId": {"stringValue": "home"}},
            "houses/home/members/alice": {"displayName": {"stringValue": "Alice"}},
            "houses/home/items/filter": {"name": {"stringValue": "Filter"}},
            "houses/home/items/filter/history/first": {"completedBy": {"stringValue": "alice"}},
        }.items():
            self.docs[path] = {"name": root + path, "fields": fields, "updateTime": "v1"}
        self.fail_parent = False

    def documents(self, path):
        return copy.deepcopy([doc for key, doc in self.docs.items()
                              if key.startswith(path + "/") and key.count("/") == path.count("/") + 1])

    def request(self, method, suffix):
        assert method == "GET"
        return copy.deepcopy(self.docs[suffix.lstrip("/")])

    def update(self, doc, values):
        path = doc["name"].split("/documents/", 1)[1]
        if self.fail_parent and "/items/" in path:
            self.fail_parent = False
            raise RuntimeError("simulated interruption")
        current = self.docs[path]
        assert doc["updateTime"] == current["updateTime"]
        for key, value in values.items():
            current["fields"][key] = ({"booleanValue": value} if isinstance(value, bool)
                                      else {"integerValue": str(value)})
        current["updateTime"] += "x"


class MigrationFlowTest(unittest.TestCase):
    def test_inspect_then_interrupted_apply_then_resume(self):
        store = Store()
        before = copy.deepcopy(store.docs)
        self.assertEqual(1, migration.migrate(store)["records"])
        self.assertEqual(before, store.docs)
        store.fail_parent = True
        with self.assertRaises(RuntimeError):
            migration.migrate(store, apply=True)
        self.assertEqual("1", store.docs["houses/home"]["fields"]["integrityVersion"]["integerValue"])
        result = migration.migrate(store, apply=True)
        self.assertEqual(0, result["blocked_houses"])
        self.assertEqual("1", store.docs["houses/home/items/filter"]["fields"]["recordCount"]["integerValue"])
        house = store.docs["houses/home"]["fields"]
        self.assertEqual("1", house["itemCount"]["integerValue"])
        self.assertEqual("2", house["integrityVersion"]["integerValue"])
        self.assertFalse(house["closing"]["booleanValue"])
        store.docs["users/alice"]["fields"]["currentHouseId"] = {"stringValue": "different-house"}
        self.assertEqual(1, migration.migrate(store, apply=True)["blocked_houses"])
        self.assertEqual("1", house["integrityVersion"]["integerValue"])

    def test_orphan_is_preserved_and_house_stays_paused(self):
        store = Store()
        del store.docs["houses/home/items/filter"]["fields"]
        result = migration.migrate(store, apply=True)
        self.assertEqual(1, result["blocked_houses"])
        self.assertIn("houses/home/items/filter/history/first", store.docs)
        self.assertEqual("1", store.docs["houses/home"]["fields"]["integrityVersion"]["integerValue"])


if __name__ == "__main__":
    unittest.main()
