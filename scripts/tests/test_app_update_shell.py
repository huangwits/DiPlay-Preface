"""Exercise the shipped installer with fake package-manager and E01 services."""
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[2] / 'common/src/main/assets/app-update/install.sh'


class AppUpdateShellTest(unittest.TestCase):
    def run_case(self, case):
        shell = os.environ.get('DIPLAY_TEST_SH') or shutil.which('sh')
        self.assertTrue(shell, 'Set DIPLAY_TEST_SH to a POSIX shell')
        with tempfile.TemporaryDirectory(prefix='diplay-update-') as directory:
            root = Path(directory)
            apk = root / 'update.apk'
            apk.write_bytes(b'validated APK fixture')
            digest = hashlib.sha256(apk.read_bytes()).hexdigest()
            if case == 'tampered':
                apk.write_bytes(b'tampered APK fixture')
            if case in ['busy', 'stale']:
                (root / 'lock').mkdir()
                (root / 'lock/pid').write_text('123')
            code = SCRIPT.read_text(encoding='utf8').replace('__BASE__', root.as_posix())
            code = code.replace('__APK__', apk.as_posix()).replace('__SHA__', digest)
            code = code.replace('__SYSTEM_APP__', '1' if case == 'invalid_system' else '0')
            for name in ['getprop', 'pm', 'am']:
                code = code.replace('/system/bin/' + name, name)
            prelude = f'''
CASE='{case}'
ROOT='{root.as_posix()}'
id() {{ if [ "$CASE" = no_root ]; then echo 2000; else echo 0; fi; }}
getprop() {{ case "$1" in
  ro.product.model) if [ "$CASE" = wrong_car ]; then echo Other; else echo E01; fi ;;
  ro.board.platform) if [ "$CASE" = wrong_platform ]; then echo mt9999; else echo mt6735; fi ;;
  ro.product.device) if [ "$CASE" = invalid_other ]; then echo E01; else echo FS11GQJ; fi ;;
esac; }}
busybox() {{ [ "$CASE" != no_hash ] || return 1; command "$@"; }}
kill() {{ [ "$CASE" = busy ]; }}
pm() {{
  echo "$*" >> "$ROOT/ops"
  case "$1" in
    install-create)
      [ "$CASE" != create_failed ] || return 1
      if [ "$CASE" = read_only ]; then echo 'java.io.IOException: Read-only file system'; return 1; fi
      if [ "$CASE" = denied ]; then echo 'java.lang.SecurityException: denied'; return 1; fi
      if [ "$CASE" = bad_id ]; then echo 'Success: created install session [7;reboot]'; else echo 'Success: created install session [7]'; fi ;;
    install-write)
      [ "$CASE" != write_failed ] || return 1
      if [ "$CASE" = no_space ]; then echo 'No space left on device'; return 1; fi
      echo 'Success: streamed bytes' ;;
    install-commit)
      case "$CASE" in invalid_*) echo 'Failure [INSTALL_FAILED_INVALID_APK]'; return 0 ;; esac
      if [ "$CASE" = pm_failed ]; then echo Failure; return 1; fi
      if [ "$CASE" = pm_rejected ]; then echo 'Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE]'; return 0; fi
      if [ "$CASE" = downgrade ]; then echo 'Failure [INSTALL_FAILED_VERSION_DOWNGRADE]'; return 0; fi
      echo Success ;;
    install-abandon) echo Success ;;
    *) return 1 ;;
  esac
}}
am() {{ echo "$*" >> "$ROOT/launch"; }}
'''
            run = root / 'run.sh'
            run.write_text(prelude + code, encoding='utf8', newline='\n')
            result = subprocess.run([shell, run.as_posix()], capture_output=True, text=True, timeout=10)
            def read(name):
                path = root / name
                return path.read_text().strip() if path.exists() else ''
            return dict(code=result.returncode, state=read('result'), ops=read('ops'),
                        launch=read('launch'), lock=(root / 'lock').exists(),
                        expected_ops='install-create -r\ninstall-write 7 base.apk ' + apk.as_posix() + '\ninstall-commit 7',
                        errors=result.stderr)

    def test_verified_overlay_install_relaunches_only_after_success(self):
        for case in ['success', 'stale']:
            with self.subTest(case=case):
                result = self.run_case(case)
                self.assertEqual(0, result['code'], result)
                self.assertEqual('success', result['state'])
                self.assertEqual(result['expected_ops'], result['ops'])
                self.assertEqual('start -n com.shihab.diplay.preface/com.shilapi.xcertplay.DiPlayActivity', result['launch'])
                self.assertFalse(result['lock'])

    def test_preflight_failures_never_invoke_package_manager(self):
        for case, state in [('no_root', 'permission-denied'), ('wrong_car', 'unsupported-car'),
                            ('wrong_platform', 'unsupported-platform'), ('tampered', 'hash-mismatch'),
                            ('no_hash', 'hash-unavailable'), ('busy', '')]:
            with self.subTest(case=case):
                result = self.run_case(case)
                self.assertNotEqual(0, result['code'], result)
                self.assertEqual(state, result['state'])
                self.assertEqual('', result['ops'])
                self.assertEqual('', result['launch'])

    def test_package_manager_failure_is_reported_without_relaunch(self):
        for case, state in [('pm_failed', 'commit-failed'), ('pm_rejected', 'install-signature'),
                            ('write_failed', 'write-failed'), ('create_failed', 'create-failed'),
                            ('bad_id', 'create-failed'), ('read_only', 'install-read-only'),
                            ('denied', 'install-denied'), ('no_space', 'install-space'),
                            ('downgrade', 'install-version'), ('invalid_user', 'install-oem-auth'),
                            ('invalid_system', 'install-invalid'), ('invalid_other', 'install-invalid')]:
            with self.subTest(case=case):
                result = self.run_case(case)
                self.assertNotEqual(0, result['code'], result)
                self.assertEqual(state, result['state'])
                self.assertEqual('', result['launch'])
                self.assertFalse(result['lock'])
                if case in ['pm_failed', 'pm_rejected', 'write_failed', 'no_space', 'downgrade',
                            'invalid_user', 'invalid_system', 'invalid_other']:
                    self.assertTrue(result['ops'].endswith('install-abandon 7'), result)


if __name__ == '__main__':
    unittest.main()
