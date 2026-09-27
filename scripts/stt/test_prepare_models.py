import hashlib
import io
import json
from pathlib import Path
import subprocess
import sys
import tarfile
import tempfile
import unittest


SCRIPT = Path(__file__).with_name("prepare_models.py")


class PrepareModelsTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.cache = self.root / "cache"
        self.output = self.root / "assets"
        self.source = self.root / "model.tar.bz2"
        with tarfile.open(self.source, "w:bz2") as archive:
            for name, contents in [("package/model.onnx", b"model"), ("package/tokens.txt", b"tokens")]:
                member = tarfile.TarInfo(name)
                member.size = len(contents)
                archive.addfile(member, io.BytesIO(contents))
        self.bundle = {
            "name": "test-model.tar.bz2",
            "url": self.source.as_uri(),
            "sha256": hashlib.sha256(self.source.read_bytes()).hexdigest(),
            "files": [
                {"member": "package/model.onnx", "path": "stt/model.onnx", "sha256": hashlib.sha256(b"model").hexdigest()},
                {"member": "package/tokens.txt", "path": "stt/tokens.txt", "sha256": hashlib.sha256(b"tokens").hexdigest()},
            ],
        }

    def run_prepare(self, bundles=None):
        manifest = self.root / "manifest.json"
        manifest.write_text(json.dumps({"bundles": bundles or [self.bundle]}))
        return subprocess.run(
            [sys.executable, str(SCRIPT), "--manifest", str(manifest), "--cache", str(self.cache), "--output", str(self.output)],
            capture_output=True, text=True,
        )

    def test_prepares_selected_assets_and_reuses_verified_cache_without_network(self):
        result = self.run_prepare()
        self.assertEqual(0, result.returncode, result.stderr)
        self.source.unlink()
        result = self.run_prepare()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(b"model", (self.output / "stt/model.onnx").read_bytes())
        self.assertEqual(b"tokens", (self.output / "stt/tokens.txt").read_bytes())
        self.assertEqual(2, len(list(self.output.rglob("*.*"))))

    def test_rejects_corrupt_cached_archive_even_when_outputs_exist(self):
        self.assertEqual(0, self.run_prepare().returncode)
        next(self.cache.iterdir()).write_bytes(b"corrupt")
        result = self.run_prepare()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("SHA-256 mismatch", result.stderr)
        self.assertEqual(b"model", (self.output / "stt/model.onnx").read_bytes())

    def test_rejects_corrupt_download_without_publishing_cache_or_assets(self):
        self.source.write_bytes(b"corrupt")
        result = self.run_prepare()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("SHA-256 mismatch", result.stderr)
        self.assertFalse(list(self.cache.iterdir()))
        self.assertFalse((self.output / "stt/model.onnx").exists())

    def test_rejects_wrong_extracted_hash_without_publishing_assets(self):
        self.bundle["files"][1]["sha256"] = "0" * 64
        result = self.run_prepare()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("SHA-256 mismatch", result.stderr)
        self.assertFalse((self.output / "stt/model.onnx").exists())

    def test_repairs_changed_generated_asset_from_verified_cache(self):
        self.assertEqual(0, self.run_prepare().returncode)
        (self.output / "stt/model.onnx").write_bytes(b"changed")
        self.source.unlink()
        result = self.run_prepare()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(b"model", (self.output / "stt/model.onnx").read_bytes())

    def test_prepares_direct_model_file(self):
        self.source.write_bytes(b"vad")
        digest = hashlib.sha256(b"vad").hexdigest()
        bundle = {"name": "vad.onnx", "url": self.source.as_uri(), "sha256": digest,
                  "files": [{"path": "stt/vad.onnx", "sha256": digest}]}
        result = self.run_prepare([bundle])
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(b"vad", (self.output / "stt/vad.onnx").read_bytes())

    def test_rejects_archive_symlink_instead_of_following_it(self):
        with tarfile.open(self.source, "w:bz2") as archive:
            member = tarfile.TarInfo("package/model.onnx")
            member.type = tarfile.SYMTYPE
            member.linkname = "/etc/passwd"
            archive.addfile(member)
        self.bundle["sha256"] = hashlib.sha256(self.source.read_bytes()).hexdigest()
        result = self.run_prepare()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("regular file", result.stderr)
        self.assertFalse((self.output / "stt/model.onnx").exists())


if __name__ == "__main__":
    unittest.main()
