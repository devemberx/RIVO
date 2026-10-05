"""Verify preview timing, byte provenance and refusal of incomplete inputs."""

import importlib.util
import base64
import io
import json
import tempfile
import unittest
from pathlib import Path

from PIL import Image

SCRIPT = Path(__file__).resolve().parents[1] / "prepare_review.py"
SPEC = importlib.util.spec_from_file_location("prepare_review", SCRIPT)
PREVIEW = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(PREVIEW)


class ReviewPreparationTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.clip = self.root / "clip.json"
        self.out = self.root / "review"
        for name, color in (("open.png", (255, 0, 0, 128)), ("closed.png", (0, 0, 255, 0))):
            Image.new("RGBA", (32, 32), color).save(self.root / name)
        self.record = {"canvas_px": [32, 32], "display_px": [16, 16], "loop": False,
                       "duration_ms": 600,
                       "terminal": {"preview_end": "hold_last", "pose": "open eyes",
                                    "handoff": "same-pose idle", "completion": "once at 600 ms"},
                       "frames": [{"path": "open.png", "duration_ms": 200},
                                  {"path": "closed.png", "duration_ms": 100},
                                  {"path": "open.png", "duration_ms": 300}]}

    def prepare(self):
        self.clip.write_text(json.dumps(self.record))
        return PREVIEW.prepare(self.clip, self.out)

    def test_variable_timing_and_repeated_frames_preserve_inventory(self):
        result = self.prepare()
        self.assertEqual(result["status"], "prepared_unreviewed")
        self.assertEqual([f["start_ms"] for f in result["frames"]], [0, 200, 300])
        self.assertEqual(result["duration_ms"], 600)
        self.assertEqual(result["frames"][0]["sha256"], result["frames"][2]["sha256"])
        self.assertNotEqual(result["frames"][0]["sha256"], result["frames"][1]["sha256"])
        with Image.open(self.out / "contact-sheet.png") as sheet:
            sheet.verify()
        # The player is self-contained, including the exact decoded alpha pixels.
        html = (self.out / "preview.html").read_text()
        data = json.loads(html.split("const clip = ", 1)[1].split(";\nconst stage", 1)[0])
        self.assertEqual(data["display"], [16, 16])
        with Image.open(io.BytesIO(base64.b64decode(data["frames"][0]["src"].split(",", 1)[1]))) as frame:
            self.assertEqual(frame.getpixel((0, 0)), (255, 0, 0, 128))

    def test_bad_duration_or_sum_leaves_no_evidence(self):
        for duration in (0, -1, True, float("nan"), 250):
            with self.subTest(duration=duration):
                self.record["frames"][0]["duration_ms"] = duration
                with self.assertRaises(ValueError):
                    self.prepare()
                self.assertFalse(self.out.exists())

    def test_one_shot_requires_terminal_contract(self):
        del self.record["terminal"]
        with self.assertRaisesRegex(ValueError, "terminal"):
            self.prepare()

    def test_display_cannot_stretch_anatomy(self):
        self.record["display_px"] = [16, 32]
        with self.assertRaisesRegex(ValueError, "aspect ratio"):
            self.prepare()

    def test_loop_does_not_require_terminal_hold(self):
        del self.record["terminal"]
        self.record["loop"] = True
        self.assertEqual(self.prepare()["duration_ms"], 600)

    def test_wrong_canvas_missing_frame_and_path_escape_block(self):
        self.record["canvas_px"] = [64, 64]
        with self.assertRaisesRegex(ValueError, "full-canvas"):
            self.prepare()
        self.record["canvas_px"] = [32, 32]
        self.record["frames"][0]["path"] = "missing.png"
        with self.assertRaises(FileNotFoundError):
            self.prepare()
        self.record["frames"][0]["path"] = "../outside.png"
        with self.assertRaisesRegex(ValueError, "within the clip directory"):
            self.prepare()

    def test_existing_review_is_never_overwritten(self):
        self.prepare()
        original = (self.out / "inventory.json").read_bytes()
        with self.assertRaises(FileExistsError):
            self.prepare()
        self.assertEqual((self.out / "inventory.json").read_bytes(), original)


if __name__ == "__main__":
    unittest.main()
