"""Exercise the live catalog and isolated invalid copies without editing source art."""

import importlib.util
import json
import shutil
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
                self.assertEqual(PREFLIGHT.audit(REPO, character)["status"], "reference_gate_passed")
        for character, item in (("mobi", "mobi_headphones"), ("mobi", "mobi_goggles"),
                                ("luna", "luna_cap"), ("luna", "luna_sunglasses")):
            with self.subTest(item=item):
                self.assertEqual(PREFLIGHT.audit(REPO, character, f"accessory:{item}")["status"],
                                 "reference_gate_passed")

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
