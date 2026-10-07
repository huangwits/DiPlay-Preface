import importlib.util
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

MODULE = Path(__file__).resolve().parents[1] / "prepare_upstream_update.py"
spec = importlib.util.spec_from_file_location("updates", MODULE)
updates = importlib.util.module_from_spec(spec)
spec.loader.exec_module(updates)


class UpdatePolicyTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.previous = Path.cwd()
        os.chdir(self.temp.name)
        self.git("init", "-b", "main")
        self.git("config", "user.name", "Test")
        self.git("config", "user.email", "test@example.invalid")
        self.base = self.commit("shared.txt", "base\n")

    def tearDown(self):
        os.chdir(self.previous)
        self.temp.cleanup()

    def git(self, *args):
        return subprocess.check_output(["git", *args], stderr=subprocess.STDOUT).decode().strip()

    def commit(self, filename, content):
        Path(filename).write_text(content, encoding="utf-8")
        self.git("add", filename)
        self.git("commit", "-m", "Change " + filename)
        return self.git("rev-parse", "HEAD")

    def test_carlito_update_keeps_local_adaptation(self):
        local = self.commit("e01.txt", "API 22\n")
        self.git("switch", "-c", "author", self.base)
        upstream = self.commit("upstream.txt", "new feature\n")
        self.git("update-ref", "refs/remotes/geely/main", upstream)
        self.git("switch", "main")
        self.assertTrue(updates.prepare("geely").startswith("sync/geely-"))
        self.assertEqual(Path("e01.txt").read_text(), "API 22\n")
        self.assertEqual(Path("upstream.txt").read_text(), "new feature\n")
        self.assertEqual(self.git("rev-parse", "main"), local)
        self.assertIsNone(updates.prepare("geely"))

    def test_original_picks_only_selected_commit_and_detects_repeat(self):
        self.git("switch", "-c", "geely-work")
        self.commit("unrelated.txt", "do not import\n")
        chosen = self.commit("audio.txt", "selected fix\n")
        self.git("update-ref", "refs/remotes/upstream/main", chosen)
        self.git("switch", "main")
        self.assertTrue(updates.prepare("original", chosen).startswith("sync/original-"))
        self.assertFalse(Path("unrelated.txt").exists())
        self.assertEqual(Path("audio.txt").read_text(), "selected fix\n")
        self.assertIn("cherry picked from commit " + chosen, self.git("log", "-1", "--format=%B"))
        self.assertIsNone(updates.prepare("original", chosen))

    def test_conflict_returns_to_original_branch_without_changing_main(self):
        local = self.commit("shared.txt", "E01 behavior\n")
        self.git("switch", "-c", "author", self.base)
        target = self.commit("shared.txt", "different behavior\n")
        self.git("update-ref", "refs/remotes/geely/main", target)
        self.git("switch", "main")
        with self.assertRaisesRegex(RuntimeError, "manual resolution"):
            updates.prepare("geely")
        self.assertEqual(self.git("branch", "--show-current"), "main")
        self.assertEqual(self.git("rev-parse", "HEAD"), local)
        self.assertEqual(Path("shared.txt").read_text(), "E01 behavior\n")
        self.assertEqual(self.git("status", "--porcelain"), "")

    def test_reject_dirty_tree_missing_sha_and_unrelated_commit(self):
        with self.assertRaises(ValueError):
            updates.prepare("original")
        self.git("update-ref", "refs/remotes/upstream/main", self.base)
        unrelated = self.commit("local.txt", "local\n")
        with self.assertRaisesRegex(ValueError, "not in upstream/main"):
            updates.prepare("original", unrelated)
        Path("untracked.txt").write_text("keep me")
        with self.assertRaisesRegex(RuntimeError, "clean working tree"):
            updates.prepare("geely")


if __name__ == "__main__":
    unittest.main()
