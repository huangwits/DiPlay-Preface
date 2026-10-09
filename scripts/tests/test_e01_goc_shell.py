"""Run the production transaction script against fake E01 services and disposable files."""
import hashlib
import os
import re
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / 'common/src/main/assets/e01-goc/manage.sh'
REAL_SHA = 'e2b71f11ae17f701469f3b60982dbc7a273bcab947f18fd1979e43df6428c54c'


class GocShellTest(unittest.TestCase):
    def run_case(self, case, action='install'):
        shell = os.environ.get('DIPLAY_TEST_SH') or shutil.which('sh')
        self.assertTrue(shell, 'Set DIPLAY_TEST_SH to a POSIX shell')
        with tempfile.TemporaryDirectory(prefix='diplay-goc-') as directory:
            root = Path(directory)
            p = root.as_posix()
            system = root / 'system'
            system.mkdir()
            proc = root / 'proc'
            (proc / '101').mkdir(parents=True)
            (root / 'mounts').write_text(f'/dev/mock {p}/system ext4 ro 0 0\n')
            target = system / 'gocsdk'
            backup = system / 'gocsdk.diplay-original'
            original = b'original factory goc\n'
            original_sha = hashlib.sha256(original).hexdigest()
            candidate = root / 'gocsdk-spp-uuid128-v2'
            candidate.write_text(f'''#!/bin/sh
mkdir -p '{p}/proc/102'
echo '{p}/gocsdk-spp-uuid128-v2' > '{p}/proc/102/exe'
if [ "$CASE" != candidate_no_listener ]; then echo '/dev/socket/goc_spp' > '{p}/unix'; fi
''', newline='\n')
            candidate.chmod(0o700)
            candidate_bytes = candidate.read_bytes()
            candidate_sha = hashlib.sha256(candidate_bytes).hexdigest()
            target.write_bytes(candidate_bytes if action == 'restore' else original)
            (proc / '101/exe').write_text(target.as_posix())
            if action == 'restore' or case == 'backup_conflict':
                backup.write_bytes(b'other version' if case == 'backup_conflict' else original)
                (system / 'gocsdk.diplay-original.sha256').write_text(original_sha)
            if case == 'backup_corrupt': backup.write_bytes(b'corrupted')
            if case == 'backup_missing': backup.unlink()
            (root / 'proof').write_text(f'{original_sha}:{candidate_sha}')
            if case == 'no_proof': (root / 'proof').unlink()
            if case == 'wrong_proof': (root / 'proof').write_text('bad-pair')
            if case == 'bad_candidate': candidate.write_text('corrupt payload')
            if case == 'foreign':
                (proc / '103').mkdir()
                (proc / '103/exe').write_text('/other/gocsdk')
            if case in ['active_lock', 'stale_lock']:
                (root / 'lock').mkdir()
                (root / 'lock/pid').write_text('99')
            expected = candidate_sha if action == 'restore' else original_sha
            if case == 'system_changed': expected = 'a' * 64
            code = SCRIPT.read_text(encoding='utf8').replace(REAL_SHA, candidate_sha)
            for old, new in [('/system/bin/gocsdk', target.as_posix()),
                             ('/proc/[0-9]*', f'{p}/proc/[0-9]*'),
                             ('/proc/net/unix', f'{p}/unix'), ('/proc/mounts', f'{p}/mounts')]:
                code = code.replace(old, new)
            code = re.sub(r'(?<= )/system(?=[ \n])', f'{p}/system', code)
            for command in ['id', 'getprop', 'start', 'stop', 'restorecon']:
                code = code.replace('/system/bin/' + command, command)
            prelude = f'''
CASE={case}
ROOT='{p}'
TARGET_MOCK='{target.as_posix()}'
export ROOT CASE
id() {{ if [ "$CASE" = no_root ]; then echo uid=2000; else echo uid=0; fi; }}
getprop() {{ case "$1" in
  ro.product.model) if [ "$CASE" = wrong_platform ]; then echo Other; else echo E01; fi ;;
  ro.board.platform) echo mt6735 ;;
  init.svc.gocsdk) if [ -f "$ROOT/proc/101/exe" ]; then echo running; else echo stopped; fi ;;
esac; }}
busybox() {{ case "$1" in
  readlink) shift; cat "$1" 2>/dev/null ;;
  *) command "$@" ;;
esac; }}
stop() {{
  echo stop >> "$ROOT/ops"
  [ "$CASE" != stop_fail ] || return 1
  rm -f "$ROOT/proc/101/exe" "$ROOT/unix"
}}
start() {{
  echo start >> "$ROOT/ops"
  [ "$CASE" != restore_start_fail ] || return 1
  if [ "$CASE" = start_fail ] && cmp -s "$TARGET_MOCK" "$ROOT/gocsdk-spp-uuid128-v2"; then return 1; fi
  echo "$TARGET_MOCK" > "$ROOT/proc/101/exe"
  if cmp -s "$TARGET_MOCK" "$ROOT/gocsdk-spp-uuid128-v2" && [ "$CASE" != listener_fail ]; then
    echo '/dev/socket/goc_spp' > "$ROOT/unix"
  fi
  return 0
}}
mount() {{
  echo "mount $*" >> "$ROOT/ops"
  case "$2" in *rw*) mode=rw ;; *) mode=ro ;; esac
  echo "/dev/mock $ROOT/system ext4 $mode 0 0" > "$ROOT/mounts"
  [ "$CASE" != remount_partial_fail ] || [ "$mode" = ro ]
}}
sleep() {{
  if [ -f "$ROOT/state" ] && command grep -q stage=ready "$ROOT/state" && [ "$CASE" != owner_dead ]; then
    echo stop > "$ROOT/stop"
  fi
  command sleep 0.015
}}
kill() {{
  if [ "$1" = -0 ]; then
    if [ "$CASE" = stale_lock ] && [ "$2" = 99 ]; then return 1; fi
    [ "$CASE" != owner_dead ]; return
  fi
  rm -f "$ROOT/proc/102/exe" "$ROOT/unix"
}}
cp() {{
  case "$*" in
    *.diplay-new) [ "$CASE" != copy_fail ] || return 1 ;;
  esac
  command cp "$@"
}}
sync() {{ :; }}
'''
            run = root / 'manage.sh'
            run.write_text(prelude + code, encoding='utf8', newline='\n')
            result = subprocess.run([shell, run.as_posix(), action, expected, '55555'],
                                    capture_output=True, text=True, encoding='utf8', timeout=15)
            state = (root / 'state').read_text() if (root / 'state').exists() else ''
            ops = (root / 'ops').read_text() if (root / 'ops').exists() else ''
            return dict(code=result.returncode, state=state, ops=ops,
                        target=target.read_bytes(), backup=backup.read_bytes() if backup.exists() else None,
                        original=original, candidate=candidate_bytes, mounts=(root / 'mounts').read_text(),
                        service=(proc / '101/exe').exists(), lock=(root / 'lock').exists(),
                        error=result.stdout + result.stderr)

    def test_install_and_restore_verify_backup_and_mount(self):
        for action in ['install', 'restore']:
            with self.subTest(action=action):
                r = self.run_case('success', action)
                self.assertEqual(0, r['code'], r)
                self.assertIn('stage=completed', r['state'])
                self.assertEqual(r['candidate'] if action == 'install' else r['original'], r['target'])
                self.assertEqual(r['original'], r['backup'])
                self.assertIn('ext4 ro', r['mounts'])
                self.assertTrue(r['service'])
                self.assertFalse(r['lock'])

    def test_preflight_rejections_do_not_stop_services_or_write_system(self):
        for case in ['no_root', 'wrong_platform', 'system_changed', 'bad_candidate',
                     'no_proof', 'wrong_proof', 'backup_conflict', 'foreign', 'active_lock']:
            with self.subTest(case=case):
                r = self.run_case(case)
                self.assertNotEqual(0, r['code'], r)
                self.assertEqual(r['original'], r['target'])
                self.assertEqual('', r['ops'], r)

    def test_failed_install_rolls_back_and_restarts_original(self):
        for case in ['stop_fail', 'copy_fail', 'start_fail', 'listener_fail', 'remount_partial_fail']:
            with self.subTest(case=case):
                r = self.run_case(case)
                self.assertNotEqual(0, r['code'], r)
                self.assertIn('stage=failed', r['state'])
                self.assertEqual(r['original'], r['target'], r)
                self.assertIn('ext4 ro', r['mounts'], r)
                self.assertTrue(r['service'], r)
                self.assertFalse(r['lock'])

    def test_restore_rejects_missing_or_corrupted_backup(self):
        for case in ['backup_missing', 'backup_corrupt']:
            with self.subTest(case=case):
                r = self.run_case(case, 'restore')
                self.assertNotEqual(0, r['code'], r)
                self.assertEqual(r['candidate'], r['target'])
                self.assertEqual('', r['ops'])

    def test_temporary_stop_or_owner_exit_restores_without_system_write(self):
        for case in ['success', 'owner_dead']:
            with self.subTest(case=case):
                r = self.run_case(case, 'test')
                self.assertEqual(0, r['code'], r)
                self.assertIn('stage=completed', r['state'])
                self.assertEqual(r['original'], r['target'])
                self.assertIsNone(r['backup'])
                self.assertNotIn('mount ', r['ops'])
                self.assertTrue(r['service'], r)

    def test_stale_lock_is_reclaimed_but_failed_test_never_reports_success(self):
        r = self.run_case('stale_lock')
        self.assertEqual(0, r['code'], r)
        self.assertFalse(r['lock'])
        for case in ['candidate_no_listener', 'restore_start_fail']:
            with self.subTest(case=case):
                r = self.run_case(case, 'test')
                self.assertNotEqual(0, r['code'], r)
                self.assertIn('stage=failed', r['state'])
                self.assertEqual(r['original'], r['target'])
                self.assertIsNone(r['backup'])


if __name__ == '__main__':
    unittest.main()
