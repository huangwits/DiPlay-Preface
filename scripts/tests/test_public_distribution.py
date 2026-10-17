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

    def test_generated_bluetooth_ciphertext_cannot_enter_public_source(self):
        with self.assertRaises(ValueError):
            public.check_entry('innocent.bin', b'DPG1' + b'x' * 64)

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

class PublicScopeTest(unittest.TestCase):
    def test_rejects_administration_source_in_tree_or_wrapped_archive(self):
        for name in ['license-server/app.py', 'license-workers/src/worker.ts',
                     'server/steering_profiles/server.py', 'snapshot/server/steering_profiles/install.py',
                     'offline-license/issue.py', 'snapshot/license-workers/package.json',
                     'snapshot/offline-license/renamed.txt', 'AGENTS.md', 'docs/BUILD.md',
                     'snapshot/docs/PREFACE-0.2.20.4.md', '.github/workflows/sync-upstream.yml',
                     'scripts/build-signed-e01.ps1', 'validation/report.json']:
            with self.subTest(name=name), self.assertRaises(ValueError):
                public.check_entry(name, b'nonsecret source')

    def test_preserves_application_verification_asset_and_legal_files(self):
        for name in ['mobile/src/e01Public/assets/offline-license/public.json',
                     'snapshot/mobile/src/e01Public/assets/offline-license/public.json',
                     'docs/DISCLAIMER.md', 'docs/THIRD_PARTY_NOTICES.md',
                     'docs/licenses/dependencies/Apache-2.0.txt', 'mobile/build.gradle.kts',
                     'scripts/seal-bluetooth.gradle']:
            with self.subTest(name=name):
                public.check_entry(name, b'public source')

    def test_nested_zip_cannot_reintroduce_administration_source(self):
        buffer = io.BytesIO()
        with zipfile.ZipFile(buffer, 'w') as archive:
            archive.writestr('snapshot/license-workers/src/worker.ts', b'nonsecret source')
        with self.assertRaises(ValueError):
            public.check_entry('snapshot.zip', buffer.getvalue())

    def test_tree_scan_rejects_even_untracked_issuer_sources(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'license-server').mkdir()
            (root / 'license-server/app.py').write_text('nonsecret source')
            with self.assertRaises(ValueError):
                public.check_tree(root)
