"""Regression checks for the small fork-specific settings panel and focus ordering."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1] / "launcher/app/src/main"
ANDROID = "{http://schemas.android.com/apk/res/android}"


class LauncherPanelContractTest(unittest.TestCase):
    def test_single_settings_section_contains_all_four_controls(self):
        root = ET.parse(ROOT / "res/layout/activity_launcher_settings.xml").getroot()
        ids = {node.get(ANDROID + "id"): node for node in root.iter()}
        section = ids["@+id/layoutTabContent"]
        section_ids = {node.get(ANDROID + "id") for node in section.iter()}
        self.assertTrue({"@+id/columns4", "@+id/dock100", "@+id/wallpaperChoice",
                         "@+id/reorderPanelChoice"}.issubset(section_ids))
        self.assertFalse({"@+id/tabApps", "@+id/tabAppearance", "@+id/tabMotion",
                          "@+id/animationsChoice", "@+id/rows0", "@+id/panelClose", "@+id/settingsTabs"}.intersection(ids))
        self.assertIn("@+id/backgroundScope", section_ids)
        self.assertNotIn("@+id/panelTitle", ids)
        self.assertNotIn("@style/SettingsCard", {n.get("style") for n in root.iter()})

    def test_launcher_button_follows_system_settings(self):
        root = ET.parse(ROOT / "res/layout/activity_main.xml").getroot()
        bar = next(n for n in root.iter() if n.get(ANDROID + "id") == "@+id/topButtons")
        ids = [n.get(ANDROID + "id") for n in bar]
        self.assertEqual(ids.index("@+id/btnSettings") + 1, ids.index("@+id/btnLauncherPanel"))

    def test_row_alignment_precedes_glass_measurement(self):
        source = (ROOT / "java/com/gathin/tvhome/MainActivity.java").read_text(encoding="utf-8")
        method = source.split("public void onTileFocused(", 1)[1].split("private void refitGlass(", 1)[0]
        self.assertLess(method.index("grid.scrollBy(0, dy)"), method.index("root.offsetDescendantRectToMyCoords"))
        self.assertEqual(1, method.count("springY.animateToFinalPosition"))

    def test_reorder_uses_the_same_row_alignment_as_home(self):
        source = (ROOT / "java/com/gathin/tvhome/MainActivity.java").read_text(encoding="utf-8")
        method = source.split("public void onTileFocused(", 1)[1].split("private void refitGlass(", 1)[0]
        self.assertNotIn("dockRow && !reordering", method)
        self.assertNotIn("settings_back_hint", (ROOT / "res/layout/activity_launcher_settings.xml").read_text())

    def test_ambient_renderer_matches_original_source_exactly(self):
        source = (ROOT / "java/com/gathin/tvhome/MainActivity.java").read_text(encoding="utf-8")
        renderer = source.split("private void setAmbient(int color) {", 1)[1].split("private void transitionAmbient", 1)[0]
        self.assertNotIn("backgroundPalette", renderer)
        self.assertIn("w * 0.32f, h * 0.42f, w * 0.62f", renderer)
        self.assertIn("0x5CFFFFFF, 0x26FFFFFF, 0x00FFFFFF", renderer)
        self.assertIn("new float[]{0f, 0.45f, 1f}", renderer)
        self.assertIn("glow.setTint(0xFF000000 | color)", renderer)


if __name__ == "__main__":
    unittest.main()
