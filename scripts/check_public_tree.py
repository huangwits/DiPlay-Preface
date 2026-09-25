#!/usr/bin/env python3
"""Fail CI if credential containers or private-key blocks enter the source tree."""
from pathlib import Path
import re
import subprocess


# Public application source excludes administration and internal maintenance files.
INTERNAL_PATHS = ('.github/workflows/android51.yml', '.github/workflows/sync-upstream.yml', 'agents.md', 'changelog.md', 'claude.md', 'product.md', 'scripts/build-beta.py', 'scripts/build-signed-e01.ps1', 'scripts/merge-local-into-upstream.ps1', 'scripts/package_source.py', 'scripts/prepare_upstream_update.py', 'scripts/reset-integrated-tree.ps1', 'scripts/tests/test_prepare_upstream_update.py')
LEGAL_DOCS = {'disclaimer.md', 'third_party_notices.md'}


def check_public_scope(parts, name):
    normalized = '/'.join(parts)
    for directory in ('license-server', 'license-workers'):
        if directory in parts:
            raise ValueError('Private administration source: ' + name)
    if 'offline-license' in parts:
        public_asset = 'mobile/src/e01public/assets/offline-license/public.json'
        if normalized != public_asset and not normalized.endswith('/' + public_asset):
            raise ValueError('Private issuer source: ' + name)
    for internal in INTERNAL_PATHS:
        if normalized == internal or normalized.endswith('/' + internal):
            raise ValueError('Internal maintenance file: ' + name)
    for directory in ('validation', 'work', 'outputs'):
        if directory in parts[:2]:
            raise ValueError('Local working files: ' + name)
    if 'docs' in parts[:2]:
        tail = parts[parts.index('docs') + 1:]
        if not (len(tail) == 1 and tail[0] in LEGAL_DOCS or tail and tail[0] == 'licenses'):
            raise ValueError('Internal documentation: ' + name)


root = Path(__file__).resolve().parents[1]
names = subprocess.check_output(['git', 'ls-files', '-z'], cwd=root).decode().split('\0')
blocked_suffixes = {'.pk8', '.p7b', '.pem', '.key', '.p12', '.pfx', '.jks', '.keystore', '.apk', '.aab'}
private_block = re.compile(rb'-----BEGIN (?:[A-Z0-9 ]+ )?PRIVATE KEY-----\s+[A-Za-z0-9+/=\r\n]{40,}')
failures = []
for name in filter(None, names):
    check_public_scope(name.lower().split('/'), name)
    path = root / name
    if not path.is_file():
        continue
    if '.private' in path.relative_to(root).parts or path.suffix.lower() in blocked_suffixes:
        failures.append(name)
    elif private_block.search(path.read_bytes()):
        failures.append(name)
if failures:
    raise SystemExit('Credential or distribution files are forbidden in the public tree:\n' + '\n'.join(failures))
print('Public tree check passed: no credential containers or private-key blocks.')
