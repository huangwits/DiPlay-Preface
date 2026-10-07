"""Check a source-only CI APK, without reading or printing authentication data."""
import sys
import zipfile
from pathlib import PurePosixPath

with zipfile.ZipFile(sys.argv[1]) as apk:
    blocked = [name for name in apk.namelist()
               if name.startswith('assets/') and (
                   'offline-mfi' in PurePosixPath(name).parts or
                   PurePosixPath(name).suffix.lower() in {
                       '.pk8', '.p7b', '.key', '.pem', '.p12', '.pfx', '.jks', '.keystore'
                   })]
if blocked:
    raise SystemExit('Source-only APK unexpectedly contains authentication assets.')
print('Source-only APK contains no authentication asset containers.')
