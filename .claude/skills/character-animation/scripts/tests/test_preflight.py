"""Exercise the live catalog and isolated invalid copies without editing source art."""

import importlib.util
import json
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / "preflight.py"
SPEC = importlib.util.spec_from_file_location("preflight", SCRIPT)
PREFLIGHT = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(PREFLIGHT)
REPO = Path(__file__).resolve().parents[5]
ITEM_MANIFEST = "art/items/luna_cap/references/reference.json"


class ReferenceGateTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.baseline = PREFLIGHT.audit(REPO, "luna", "accessory:luna_cap")

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.repo = Path(self.temp.name)
        for relative in self.baseline["checked_sha256"]:
            target = self.repo / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(REPO / relative, target)

    def review_record(self, result):
        path = self.repo / "input-review.json"
        path.write_text(json.dumps({
            "schema_version": 1,
            "status": "accepted_for_reference",
            "reviewer": "Fixture reviewer",
            "authority": "Isolated test fixture only",
            "date": "2026-10-05",
            "evidence": "Fixture inspection record",
            "separate_user_visual_signoff": False,
            "checked_sha256": result["checked_sha256"],
        }))
        return path

    def update(self, relative, change):
        path = self.repo / relative
        data = json.loads(path.read_text())
        change(data)
        path.write_text(json.dumps(data))

    def check(self):
        return PREFLIGHT.audit(self.repo, "luna", "accessory:luna_cap")

    def test_current_catalog_targets(self):
        for character in ("mobi", "luna", "las"):
            with self.subTest(character=character):
                result = PREFLIGHT.audit(REPO, character)
                self.assertEqual(result["status"], "reference_checks_passed")
                self.assertFalse(result["generation_ready"])
        for character, item in (("mobi", "mobi_headphones"), ("mobi", "mobi_goggles"),
                                ("luna", "luna_cap"), ("luna", "luna_sunglasses")):
            with self.subTest(item=item):
                self.assertEqual(PREFLIGHT.audit(REPO, character, f"accessory:{item}")["status"],
                                 "reference_checks_passed")

    def test_matching_review_binds_current_inputs(self):
        review = self.review_record(self.check())
        result = PREFLIGHT.audit(self.repo, "luna", "accessory:luna_cap", review)
        self.assertTrue(result["generation_ready"])
        self.assertEqual(result["input_review_status"], "matched")
        self.assertEqual(result["status"], "reference_gate_passed")

    def test_unreviewed_base_contract_change_cannot_authorize_generation(self):
        baseline = PREFLIGHT.audit(self.repo, "luna")
        review = self.review_record(baseline)
        self.update("art/characters/luna/references/reference.json",
                    lambda d: d.update(identity_constraints=["Changed without review"]))
        result = PREFLIGHT.audit(self.repo, "luna")
        self.assertFalse(result["generation_ready"])
        self.assertEqual(result["input_review_status"], "pending")
        with self.assertRaisesRegex(ValueError, "Reviewed input inventory mismatch"):
            PREFLIGHT.audit(self.repo, "luna", reviewed_inputs=review)

    def test_item_contract_change_invalidates_review(self):
        review = self.review_record(self.check())
        self.update(ITEM_MANIFEST, lambda d: d["attachment_contract"].update(pivot="new pivot"))
        with self.assertRaisesRegex(ValueError, "Reviewed input inventory mismatch"):
            PREFLIGHT.audit(self.repo, "luna", "accessory:luna_cap", review)

    def test_incomplete_or_pending_review_cannot_authorize_generation(self):
        for field, value in (("status", "pending"), ("evidence", ""),
                             ("authority", " "), ("checked_sha256", {})):
            with self.subTest(field=field):
                review = self.review_record(self.check())
                record = json.loads(review.read_text())
                record[field] = value
                review.write_text(json.dumps(record))
                with self.assertRaises(ValueError):
                    PREFLIGHT.audit(self.repo, "luna", "accessory:luna_cap", review)

    def test_cli_distinguishes_pending_pass_and_blocked(self):
        args = [sys.executable, "-B", str(SCRIPT), "--repo", str(self.repo), "--character", "luna"]
        pending = subprocess.run(args, capture_output=True, text=True)
        self.assertEqual(pending.returncode, 2)
        self.assertFalse(json.loads(pending.stdout)["generation_ready"])
        review = self.review_record(json.loads(pending.stdout))
        passed = subprocess.run(args + ["--reviewed-inputs", str(review)], capture_output=True, text=True)
        self.assertEqual(passed.returncode, 0)
        self.assertTrue(json.loads(passed.stdout)["generation_ready"])
        (self.repo / self.baseline["images_to_inspect"][0]).unlink()
        blocked = subprocess.run(args, capture_output=True, text=True)
        self.assertEqual(blocked.returncode, 1)
        self.assertEqual(json.loads(blocked.stdout)["status"], "blocked")

    def test_delegated_review_and_variant_mapping(self):
        result = self.check()
        self.assertEqual(result["asset_variant"], "hat")
        self.assertFalse(result["reference_reviews"][ITEM_MANIFEST]["separate_user_visual_signoff"])

    def test_missing_image_blocks(self):
        (self.repo / self.baseline["images_to_inspect"][-1]).unlink()
        with self.assertRaisesRegex(ValueError, "Missing file"):
            self.check()

    def test_changed_image_blocks(self):
        path = self.repo / self.baseline["images_to_inspect"][-1]
        path.write_bytes(path.read_bytes() + b"changed")
        with self.assertRaisesRegex(ValueError, "hash mismatch"):
            self.check()

    def test_changed_base_manifest_blocks(self):
        path = self.repo / "art/characters/luna/references/reference.json"
        path.write_text(path.read_text() + "\n")
        with self.assertRaisesRegex(ValueError, "Base manifest hash mismatch"):
            self.check()

    def test_unreviewed_sheet_blocks(self):
        self.update(ITEM_MANIFEST, lambda d: d["turnaround"]["review"].update(status="pending"))
        with self.assertRaisesRegex(ValueError, "Unreviewed"):
            self.check()

    def test_missing_authority_blocks(self):
        self.update(ITEM_MANIFEST, lambda d: d["turnaround"]["review"].pop("authority"))
        with self.assertRaisesRegex(ValueError, "Missing review authority"):
            self.check()

    def test_missing_back_view_blocks(self):
        self.update(ITEM_MANIFEST, lambda d: d["turnaround"]["views"].pop())
        with self.assertRaisesRegex(ValueError, "Missing views"):
            self.check()

    def test_outside_sheet_rectangle_blocks(self):
        self.update(ITEM_MANIFEST, lambda d: d["turnaround"]["views"][0].update(rect_px=[0, 0, 9000, 300]))
        with self.assertRaisesRegex(ValueError, "Out-of-bounds"):
            self.check()

    def test_wrong_character_blocks(self):
        self.update(ITEM_MANIFEST, lambda d: d.update(character_id="friend:mobi"))
        with self.assertRaisesRegex(ValueError, "Item/character mismatch"):
            self.check()

    def test_missing_attachment_blocks(self):
        self.update(ITEM_MANIFEST, lambda d: d["attachment_contract"].pop("pivot"))
        with self.assertRaisesRegex(ValueError, "Missing attachment"):
            self.check()

    def test_unknown_schema_blocks(self):
        self.update(ITEM_MANIFEST, lambda d: d.update(schema_version=2))
        with self.assertRaisesRegex(ValueError, "Unsupported schema"):
            self.check()

    def test_path_escape_blocks(self):
        self.update(ITEM_MANIFEST, lambda d: d["turnaround"].update(path="../outside.png"))
        with self.assertRaisesRegex(ValueError, "outside repository"):
            self.check()


if __name__ == "__main__":
    unittest.main()
