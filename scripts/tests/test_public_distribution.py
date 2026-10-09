import importlib.util
import io
from pathlib import Path
import tempfile
import unittest
import zipfile

spec = importlib.util.spec_from_file_location('public_tree', Path(__file__).resolve().parents[1] / 'check_public_tree.py')
public = importlib.util.module_from_spec(spec)
spec.loader.exec_module(public)


class PublicDistributionTest(unittest.TestCase):
    def test_rejects_private_inputs_installers_databases_and_worker_secrets(self):
        for name in ['a/identity.pk8', 'CERT.P7B', 'x.APK', 'x.aab', 'a/.dev.vars',
                     'server-secrets.json', 'x/admin-token.txt', 'licenses.sqlite3',
                     'a/.private/renamed.txt', 'e01-goc/gocsdk-spp-uuid128-v2']:
            with self.subTest(name=name), self.assertRaises(ValueError):
                public.check_entry(name, b'x')

    def test_detects_renamed_pem_key_and_installer_link(self):
        key = b'-----BEGIN ' + b'PRIVATE KEY-----\n' + b'A' * 64
        with self.assertRaises(ValueError):
            public.check_entry('innocent.txt', key)
        with self.assertRaises(ValueError):
            public.check_entry('site/index.html', b'<a href="https://example.test/installer.apk">Download</a>')
        public.check_entry('public.json', b'{"publicKey":"example"}')

    def test_nested_archive_cannot_hide_an_installer_and_traversal_is_rejected(self):
        buffer = io.BytesIO()
        with zipfile.ZipFile(buffer, 'w') as z:
            z.writestr('app.apk', b'x')
        with self.assertRaises(ValueError):
            public.check_entry('nested.zip', buffer.getvalue())
        with self.assertRaises(ValueError):
            public.check_entry('../outside.txt', b'x')

    def test_no_git_snapshot_checks_untracked_files_but_omits_build_outputs(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'README.md').write_text('source')
            (root / 'build').mkdir()
            (root / 'build/app.apk').write_bytes(b'ephemeral')
            self.assertEqual(1, public.check_tree(root))
            (root / 'accidental.apk').write_bytes(b'x')
            with self.assertRaises(ValueError):
                public.check_tree(root)
