#!/usr/bin/env python3
"""Reject reintroduction of removed vehicle-specific integrations in this fork."""
from pathlib import Path
import hashlib
import re

ROOT = Path(__file__).resolve().parents[1]
for module in ('common', 'shared', 'mobile'):
    for path in (ROOT / module / 'src').rglob('*'):
        if not path.is_file() or '/test/' in path.as_posix():
            continue
        assert not re.match(r'(Byd|DiLink\d|AdbCluster|CarPlayRotation)', path.name), path
        assert 'byd-hud-icons' not in path.as_posix(), path
        if path.suffix in ('.kt', '.java', '.xml'):
            text = path.read_text(encoding='utf-8-sig')
            assert not re.search(r'com\.byd\.|android\.permission\.BYDAUTO|byd\.hud\.|com\.ts\.car\.someip', text), path
            if '/res/values' in path.as_posix():
                assert not re.search(r'BYD|DiLink|比亚迪', text), path
# This historical filename used to contain removed-brand artwork. Only the reviewed
# Geely badge from carlito 08aa5fd3 may now occupy it; keep rejecting other artwork.
badge = ROOT / 'common/src/main/res/raw/ic_car_home.png'
assert hashlib.sha256(badge.read_bytes()).hexdigest() == \
    '4b0f959e8caa27c4185c21d8205d7bd9aea73dba19ed35176863d846a0e9e34e', 'Unreviewed default vehicle badge'
wheel = (ROOT / 'common/src/main/java/com/shilapi/xcertplay/WheelKeyService.kt').read_text(encoding='utf-8')
assert 'simulate-keys' not in wheel and 'BYD_KEYS' not in wheel, 'Removed manufacturer key defaults returned'
print('Geely scope: no removed vendor classes, service targets, UI strings or logo assets.')
