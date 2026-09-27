#!/usr/bin/env python3
"""Prepare pinned speech assets at build time; never download from the application."""

import argparse
import hashlib
import json
from pathlib import Path
import shutil
import tarfile
import tempfile
import urllib.request


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def verify(path, expected):
    actual = sha256(path)
    if actual != expected:
        raise ValueError(f"SHA-256 mismatch for {path}: expected {expected}, got {actual}. Remove the corrupt cache file and retry.")


def cached_download(bundle, cache):
    target = cache / (bundle["sha256"] + "-" + bundle["name"])
    if not target.exists():
        with tempfile.TemporaryDirectory(prefix="download-", dir=cache) as temporary:
            partial = Path(temporary) / "download"
            print(f"Downloading {bundle['url']}", flush=True)
            with urllib.request.urlopen(bundle["url"], timeout=120) as source, partial.open("wb") as output:
                shutil.copyfileobj(source, output)
            verify(partial, bundle["sha256"])
            partial.replace(target)
    verify(target, bundle["sha256"])
    return target


def asset_path(root, relative):
    path = Path(relative)
    if path.is_absolute() or ".." in path.parts:
        raise ValueError(f"Invalid asset path: {relative}")
    return root / path


def extract(bundle, source, staging):
    files = bundle["files"]
    if all("member" in item for item in files):
        remaining = {item["member"]: item for item in files}
        with tarfile.open(source, "r|*") as archive:
            for member in archive:
                if member.name not in remaining:
                    continue
                item = remaining.pop(member.name)
                if not member.isfile():
                    raise ValueError(f"Expected regular file: {member.name}")
                target = asset_path(staging, item["path"])
                target.parent.mkdir(parents=True, exist_ok=True)
                with archive.extractfile(member) as contents, target.open("wb") as output:
                    shutil.copyfileobj(contents, output)
                verify(target, item["sha256"])
        if remaining:
            raise ValueError(f"Missing archive members: {', '.join(remaining)}")
    elif len(files) == 1 and "member" not in files[0]:
        target = asset_path(staging, files[0]["path"])
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, target)
        verify(target, files[0]["sha256"])
    else:
        raise ValueError("A bundle must be one direct file or named archive members")


def prepare(manifest, cache, output):
    cache.mkdir(parents=True, exist_ok=True)
    output.parent.mkdir(parents=True, exist_ok=True)
    # Publish only after every source and selected member passes verification.
    with tempfile.TemporaryDirectory(prefix="stt-", dir=output.parent) as temporary:
        staging = Path(temporary)
        for bundle in manifest["bundles"]:
            source = cached_download(bundle, cache)
            if all(
                asset_path(output, item["path"]).is_file()
                and sha256(asset_path(output, item["path"])) == item["sha256"]
                for item in bundle["files"]
            ):
                continue
            extract(bundle, source, staging)
        for path in staging.rglob("*"):
            if path.is_file():
                target = output / path.relative_to(staging)
                target.parent.mkdir(parents=True, exist_ok=True)
                path.replace(target)
    print("Verified speech model assets", flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", required=True, type=Path)
    parser.add_argument("--cache", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    prepare(json.loads(args.manifest.read_text()), args.cache, args.output)


if __name__ == "__main__":
    main()
