"""Catch duplicate Android value resources before Gradle's resource merge."""
import collections
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET


RES = Path(__file__).resolve().parents[1] / "launcher/app/src/main/res"


class ResourceDuplicatesTest(unittest.TestCase):
    def test_value_resource_names_are_unique_per_configuration(self):
        for directory in sorted(RES.glob("values*")):
            names = collections.defaultdict(list)
            for path in sorted(directory.glob("*.xml")):
                for item in ET.parse(path).getroot():
                    name = item.get("name")
                    if name is not None:
                        kind = item.get("type") if item.tag == "item" else item.tag
                        names[(kind, name)].append(path.name)
            duplicates = {key: files for key, files in names.items() if len(files) > 1}
            with self.subTest(configuration=directory.name):
                self.assertEqual({}, duplicates)


if __name__ == "__main__":
    unittest.main()
