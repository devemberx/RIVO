"""Exercise review gates and source preservation without calling image generation."""

import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

from PIL import Image, ImageDraw

SCRIPT = Path(__file__).resolve().parents[1] / "prepare_review.py"
SPEC = importlib.util.spec_from_file_location("prepare_review", SCRIPT)
review = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(review)


class PrepareReviewTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.master = self.image("master.png", (80, 45))

    def tearDown(self):
        self.temp.cleanup()

    def image(self, name, size, color="#203040"):
        path = self.root / name
        Image.new("RGB", size, color).save(path)
        return path

    def run_review(self, variants, periods=("day",), regions=None, directory="review"):
        return review.prepare_review(self.master, variants, periods, regions or {}, self.root / directory, "test_scene")

    def test_relighting_remains_pending_and_sources_are_unchanged(self):
        day = self.image("day.png", (80, 45), "#aec0e0")
        before = {path: path.read_bytes() for path in (self.master, day)}
        report = self.run_review({"day": day}, regions={"skyline": [0, 0, 1, 0.7], "tiny": [0, 0, 0.0001, 0.0001]})
        self.assertEqual("pass", report["automatic_checks"])
        self.assertEqual("pending_visual_review", report["verdict"])
        self.assertEqual({"pending"}, set(report["variants"]["day"]["visual_checks"].values()))
        self.assertTrue((self.root / "review" / "focus_skyline.png").is_file())
        self.assertEqual(before, {path: path.read_bytes() for path in before})
        saved = json.loads((self.root / "review" / "review.json").read_text())
        self.assertEqual(report, saved)

    def test_structural_change_cannot_receive_automatic_semantic_pass(self):
        day = self.image("changed-tower.png", (80, 45))
        with Image.open(day) as image:
            changed = image.copy()
        ImageDraw.Draw(changed).rectangle((25, 2, 38, 42), fill="orange")
        changed.save(day)
        report = self.run_review({"day": day})
        self.assertEqual("pass", report["automatic_checks"])
        self.assertEqual("pending_visual_review", report["variants"]["day"]["verdict"])
        self.assertEqual("pending", report["variants"]["day"]["visual_checks"]["building_and_terrain_geometry"])

    def test_missing_corrupt_and_wrong_size_variants_fail_without_being_dropped(self):
        corrupt = self.root / "broken.png"
        corrupt.write_bytes(b"not an image")
        wrong = self.image("wrong.png", (81, 45))
        report = self.run_review({"day": corrupt, "night": wrong}, ("sunrise", "day", "night"))
        self.assertEqual("rejected", report["verdict"])
        self.assertEqual({"sunrise", "day", "night"}, set(report["variants"]))
        self.assertTrue(all(row["automatic_checks"] == "fail" for row in report["variants"].values()))
        self.assertTrue((self.root / "review" / "overview.png").is_file())
        self.assertIn("Variant verdict: rejected", (self.root / "review" / "review.md").read_text())

    def test_transparency_mismatch_and_animation_fail(self):
        transparent = self.root / "transparent.png"
        Image.new("RGBA", (80, 45), (1, 2, 3, 0)).save(transparent)
        animated = self.root / "animated.gif"
        Image.new("RGB", (80, 45), "red").save(
            animated, save_all=True, append_images=[Image.new("RGB", (80, 45), "blue")], duration=100,
        )
        report = self.run_review({"day": transparent, "night": animated}, ("day", "night"))
        self.assertEqual("fail", report["automatic_checks"])
        self.assertIn("transparency", report["variants"]["day"]["errors"][0])
        self.assertIn("single still image", report["variants"]["night"]["errors"][0])

    def test_duplicate_content_warns_and_existing_review_is_preserved(self):
        report = self.run_review({"day": self.master})
        self.assertTrue(report["variants"]["day"]["warnings"])
        evidence = self.root / "review" / "review.md"
        evidence.write_text("reviewer evidence")
        with self.assertRaises(ValueError):
            self.run_review({"day": self.master})
        self.assertEqual("reviewer evidence", evidence.read_text())
        with self.assertRaises(ValueError):
            review.prepare_review(self.master, {}, ("day",), {}, self.root, "test_scene")

    def test_invalid_crop_regions_cannot_escape_or_hide_the_scene(self):
        for value in ("../escape=0,0,1,1", "sky=0,0,2,1", "sky=0,0,nan,1", "sky=0.5,0,0.4,1"):
            with self.subTest(value=value), self.assertRaises(ValueError):
                review.parse_regions([value])
        with self.assertRaises(ValueError):
            review.parse_regions(["sky=0,0,1,1", "sky=0,0,1,1"])


if __name__ == "__main__":
    unittest.main()
