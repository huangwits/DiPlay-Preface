"""Verify Bluetooth DEX obfuscation in a final APK; this does not prove irreversibility."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import struct
import zipfile


def dex_definitions(data):
    if not data.startswith(b'dex\n'):
        raise ValueError('Expected a standard DEX file')

    def u32(offset):
        return struct.unpack_from('<I', data, offset)[0]

    def uleb(offset):
        value = 0
        for shift in range(0, 35, 7):
            byte = data[offset]
            offset += 1
            value |= (byte & 0x7f) << shift
            if byte < 0x80:
                return value, offset
        raise ValueError('Invalid DEX uleb128')

    strings = []
    for i in range(u32(56)):
        start = u32(u32(60) + i * 4)
        _, start = uleb(start)
        # Class/method identifiers checked here are ASCII; replacement handles unrelated MUTF-8.
        strings.append(data[start:data.index(b'\0', start)].decode('utf8', errors='replace'))
    types = [strings[u32(u32(68) + i * 4)] for i in range(u32(64))]
    result = {}
    for i in range(u32(96)):
        definition = u32(100) + i * 32
        descriptor = types[u32(definition)]
        methods = set()
        offset = u32(definition + 24)
        if offset:
            counts = []
            for _ in range(4):
                count, offset = uleb(offset)
                counts.append(count)
            for _ in range(counts[0] + counts[1]):
                _, offset = uleb(offset)
                _, offset = uleb(offset)
            for count in counts[2:]:
                method_index = 0
                for _ in range(count):
                    delta, offset = uleb(offset)
                    method_index += delta
                    _, offset = uleb(offset)
                    _, offset = uleb(offset)
                    methods.add(strings[u32(u32(92) + method_index * 8 + 4)])
        result[descriptor] = methods
    return result


def audit(apk, mapping):
    mapping_text = mapping.read_text(encoding='utf8')
    configuration = mapping.with_name('configuration.txt').read_text(encoding='utf8')
    assert not re.search(r'^\s*-(dontobfuscate|dontoptimize)\b', configuration, re.M)
    names = dict(re.findall(r'^([^ #\s][^ ]+) -> ([^:]+):$', mapping_text, re.M))
    with zipfile.ZipFile(apk) as archive:
        assert not any(Path(n).name in {'mapping.txt', 'configuration.txt'} for n in archive.namelist())
        definitions = {}
        for name in archive.namelist():
            if re.fullmatch(r'classes\d*\.dex', name):
                definitions.update(dex_definitions(archive.read(name)))
        assert definitions
        readable = [name for name in archive.namelist() if name.startswith('assets/e01-goc/')]
        assert 'assets/e01-goc/manage.sh' in readable
        script = archive.read('assets/e01-goc/manage.sh')
        assert script.startswith(b'#!/system/bin/sh')

    prefix = 'com.shilapi.xcertplay.'
    business = {
        'e01goc.E01GocManager': {'test', 'install', 'restore', 'inspect', 'exclusive'},
        'e01goc.E01RootBridge': {'execute', 'command', 'parse'},
        'e01goc.E01GocPreferences': {'enabled', 'select'},
        'e01goc.GocSnapshot': {'getInstalled', 'getRecoverable', 'installable'},
        'network.E01ConnectedPhones': {'connected', 'supported'},
        'transport.GocSppTransport': {'connect'},
        'E01BluetoothSwitchIntegration': {'prepare', 'beginFactory', 'endFactory'},
    }
    rows = []
    for suffix, methods in business.items():
        original = prefix + suffix
        assert 'L' + original.replace('.', '/') + ';' not in definitions, original
        renamed = names.get(original)
        if renamed and not renamed.startswith('R8$$REMOVED$$'):
            assert renamed != original
            descriptor = 'L' + renamed.replace('.', '/') + ';'
            assert descriptor in definitions, (original, renamed)
            assert not (methods & definitions[descriptor]), (original, methods & definitions[descriptor])
            rows.append({'class': original, 'result': 'renamed', 'mappedClass': renamed})
        else:
            assert original in names or original + '.' in mapping_text, original
            rows.append({'class': original, 'result': 'optimized_away_or_inlined'})

    activity = prefix + 'e01goc.E01GocActivity'
    activity_descriptor = 'L' + activity.replace('.', '/') + ';'
    assert names.get(activity) == activity and activity_descriptor in definitions
    internal = {'allowConnectionTest', 'continueConnection', 'choosePhone', 'work', 'confirm', 'showStatus'}
    assert not (internal & definitions[activity_descriptor])
    assert 'onCreate' in definitions[activity_descriptor]
    return {
        'apkSha256': hashlib.sha256(apk.read_bytes()).hexdigest(),
        'bluetoothBusinessClasses': rows,
        'activityInternalMethodsObfuscated': True,
        'preservedAndroidEntryClass': activity,
        'mappingEmbedded': False,
        'extractableBluetoothAssets': readable,
        'maintenanceScriptReadable': True,
        'nonReversibilityGuaranteed': False,
        'scope': 'Checks DEX definitions and R8 mapping, not resistance to decompilation, debugging or extraction.',
    }


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk', type=Path)
    parser.add_argument('mapping', type=Path)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    report = json.dumps(audit(args.apk, args.mapping), ensure_ascii=False, indent=2) + '\n'
    if args.output:
        args.output.write_text(report, encoding='utf8')
    print(report)
