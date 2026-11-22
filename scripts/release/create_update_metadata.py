#!/usr/bin/env python3
"""Describe the signed APK already verified by the trusted release job; contains no secrets."""
import argparse
import hashlib
import json
import re
from pathlib import Path


def metadata(apk: Path, version: str, code: int) -> dict:
    if not re.fullmatch(r"(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)", version):
        raise ValueError("Stable version required")
    if not 1 <= code <= 2147483647 or len(version) > 32:
        raise ValueError("Invalid release version")
    if apk.name != f"home-timing-v{version}.apk" or not apk.is_file():
        raise ValueError("Versioned official APK required")
    size = apk.stat().st_size
    if not 1 <= size <= 200 * 1024 * 1024:
        raise ValueError("Invalid APK size")
    digest = hashlib.sha256()
    with apk.open("rb") as stream:
        for chunk in iter(lambda: stream.read(65536), b""):
            digest.update(chunk)
    return {"versionName": version, "versionCode": code, "sizeBytes": size,
            "sha256": digest.hexdigest(),
            "apkUrl": f"https://github.com/kw-mobile/home-timing/releases/download/v{version}/{apk.name}"}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--code", type=int, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    value = metadata(args.apk, args.version, args.code)
    args.output.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
