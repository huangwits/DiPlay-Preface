"""Check public-variant segregation and actual R8 output; never print credential bytes."""
import json
from pathlib import Path
import re
import sys
import zipfile

apk_path, mapping_path = map(Path, sys.argv[1:3])
with zipfile.ZipFile(apk_path) as apk:
    names = apk.namelist()
    for name in ("assets/e01-goc/gocsdk-spp-uuid128-v2", "assets/e01-bluetooth/mtk-su", "assets/license/server.json"):
        assert name not in names, "Public APK contains a private helper or online activation config"
    assert not any("issuer-private" in name or "admin-token" in name or "signing-private" in name for name in names)
    config = json.loads(apk.read("assets/offline-license/public.json"))
    assert set(config) == {"version", "package", "signer", "contact", "publicKey"}
    assert config["version"] == 1 and config["package"] == "com.shihab.diplay.preface"
    assert config["contact"] == "starts181004"
    assert apk.read("assets/offline-license/public.json") == (Path(__file__).resolve().parents[1] / "mobile/src/e01Public/assets/offline-license/public.json").read_bytes()
mapping = mapping_path.read_text(encoding="utf8")
classes = re.findall(r"^(com\.shilapi\.xcertplay\.[^ ]+) -> ([^:]+):$", mapping, re.M)
renamed = sum(original != result for original, result in classes)
assert renamed > 100, "Expected R8 application class renaming"
assert "com.shilapi.xcertplay.license.OfflineLicenseProtocol.verify" in mapping
for suffix in ("transport.LinuxI2cNative", "transport.LinuxI2cNativeException", "network.LegacyHotspotNative", "media.SpeexEchoCanceller"):
    name = "com.shilapi.xcertplay." + suffix
    assert name + " -> " + name + ":" in mapping, "JNI entry point renamed"
assert "-dontobfuscate" not in mapping_path.with_name("configuration.txt").read_text(encoding="utf8")
print(f"Public APK: offline public config only, no personal helper/online config; {renamed} application classes renamed.")
