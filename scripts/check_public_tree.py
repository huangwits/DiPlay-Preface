"""Audit source trees and source ZIPs before public distribution (no Git required)."""
from __future__ import annotations
import argparse
import io
import os
from pathlib import Path, PurePosixPath
import re
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SKIP = {'.git', '.gradle', '.kotlin', 'build', '.cxx', '.externalNativeBuild',
        'node_modules', '.wrangler', '__pycache__', '.idea', '.vscode'}
BLOCKED = {'.pk8', '.p7b', '.pem', '.key', '.p12', '.pfx', '.jks', '.keystore', '.apk', '.aab', '.db', '.sqlite', '.sqlite3'}
PRIVATE = re.compile(rb'-----BEGIN (?:[A-Z0-9 ]+ )?PRIVATE KEY-----\s+[A-Za-z0-9+/=\r\n]{40,}')
DOWNLOAD = re.compile(rb'https?://[^\s"<>]+\.(?:apk|aab)(?:[?#\s"<>]|$)', re.I)


def source_files(root):
    """Skip generated directories, but do not silently omit secret-looking source files."""
    for folder, dirs, files in os.walk(root, followlinks=False):
        for name in dirs:
            p = Path(folder) / name
            if p.is_symlink():
                raise ValueError('Source symlink is not allowed: ' + str(p.relative_to(root)))
        dirs[:] = sorted(d for d in dirs if d not in SKIP)
        for name in sorted(files):
            p = Path(folder) / name
            if p.is_symlink():
                raise ValueError('Source symlink is not allowed: ' + str(p.relative_to(root)))
            if p.suffix.lower() in {'.pyc', '.pyo', '.log'} or name in {'local.properties', '.DS_Store', 'Thumbs.db'}:
                continue
            yield p


def check_entry(name, data, depth=0):
    path = PurePosixPath(name.replace('\\', '/'))
    parts = [p.lower() for p in path.parts]
    if path.is_absolute() or '..' in path.parts or ':' in name:
        raise ValueError('Unsafe archive path: ' + name)
    if (path.suffix.lower() in BLOCKED or '.private' in parts or
            path.name.lower().startswith(('.env', '.dev.vars')) or
            any(mark in path.name.lower() for mark in ('issuer-private', 'signing-private', 'admin-token', 'server-secrets', 'gocsdk-spp', 'mtk-su'))):
        raise ValueError('Private or installable file: ' + name)
    if PRIVATE.search(data):
        raise ValueError('Private-key block: ' + name)
    if 'site' in parts and path.suffix == '.html' and DOWNLOAD.search(data):
        raise ValueError('Website links to an installable package: ' + name)
    if path.suffix.lower() == '.zip':
        if depth >= 2:
            raise ValueError('Nested source archive is too deep: ' + name)
        check_zip(io.BytesIO(data), depth + 1)


def check_zip(source, depth=0):
    count = total = 0
    with zipfile.ZipFile(source) as archive:
        seen = set()
        for item in archive.infolist():
            if item.is_dir():
                continue
            if item.filename in seen or item.flag_bits & 1:
                raise ValueError('Duplicate or encrypted archive entry: ' + item.filename)
            seen.add(item.filename)
            total += item.file_size
            if total > 128 * 1024 * 1024 or item.file_size > 32 * 1024 * 1024:
                raise ValueError('Source archive exceeds inspection size limit')
            check_entry(item.filename, archive.read(item), depth)
            count += 1
    return count


def check_tree(root):
    paths = list(source_files(root))
    # Tracked generated/ignored files must still be checked when a Git checkout exists.
    if (root / '.git').exists():
        names = subprocess.check_output(['git', 'ls-files', '-z'], cwd=root).decode().split('\0')
        paths = sorted(set(paths) | {root / n for n in names if n and (root / n).is_file()})
    for path in paths:
        if path.stat().st_size > 32 * 1024 * 1024:
            raise ValueError('Source file exceeds inspection size limit: ' + str(path.relative_to(root)))
        check_entry(path.relative_to(root).as_posix(), path.read_bytes())
    return len(paths)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('path', nargs='?', type=Path, default=ROOT)
    args = parser.parse_args()
    count = check_tree(args.path.resolve()) if args.path.is_dir() else check_zip(args.path)
    print(f'Public source check passed: {count} files inspected.')


if __name__ == '__main__':
    main()
