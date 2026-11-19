import hashlib
import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("metadata", Path(__file__).parents[1] / "create_update_metadata.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class MetadataTests(unittest.TestCase):
    def test_exact_file_hash_size_and_channel(self):
        with tempfile.TemporaryDirectory() as folder:
            apk = Path(folder) / "home-timing-v0.4.0.apk"
            apk.write_bytes(b"test signed artifact")
            value = module.metadata(apk, "0.4.0", 6)
            self.assertEqual(value["sha256"], hashlib.sha256(apk.read_bytes()).hexdigest())
            self.assertEqual(value["sizeBytes"], 20)
            self.assertEqual(value["versionCode"], 6)
            self.assertEqual(value["apkUrl"], "https://github.com/kw-mobile/home-timing/releases/download/v0.4.0/home-timing-v0.4.0.apk")

    def test_mismatched_name_and_invalid_version_code_are_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            apk = Path(folder) / "home-timing-v0.4.0.apk"
            apk.write_bytes(b"artifact")
            for version, code in [("../0.4.0", 6), ("0.4.0-beta", 6), ("0.4.1", 6), ("0.4.0", 0), ("0.4.0", 2147483648)]:
                with self.subTest(version=version, code=code), self.assertRaises(ValueError):
                    module.metadata(apk, version, code)

    def test_empty_and_missing_artifact_are_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            apk = Path(folder) / "home-timing-v0.4.0.apk"
            with self.assertRaises(ValueError):
                module.metadata(apk, "0.4.0", 6)
            apk.write_bytes(b"")
            with self.assertRaises(ValueError):
                module.metadata(apk, "0.4.0", 6)
