#!/usr/bin/env python3
"""Reject reintroduction of removed vehicle-specific integrations in this fork."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
for module in ('common', 'shared', 'mobile'):
    for path in (ROOT / module / 'src').rglob('*'):
        if not path.is_file() or '/test/' in path.as_posix():
            continue
        assert not re.match(r'(Byd|DiLink\d|AdbCluster|WheelKeyService|CarPlayRotation)', path.name), path
        assert 'byd-hud-icons' not in path.as_posix(), path
        if '/src/main/res/' in path.as_posix():
            assert not re.fullmatch(r'values-[a-z]{2}(?:-r[A-Z]{2})?', path.parent.name), path
        if path.suffix in ('.kt', '.java', '.xml'):
            text = path.read_text(encoding='utf-8-sig')
            assert not re.search(r'com\.byd\.|android\.permission\.BYDAUTO|byd\.hud\.|com\.ts\.car\.someip', text), path
            if '/res/values' in path.as_posix():
                assert not re.search(r'BYD|DiLink|比亚迪', text), path
assert not (ROOT / 'common/src/main/res/raw/ic_car_home.png').exists()
print('Preface scope: no removed vendor integrations or foreign app translation resources.')
