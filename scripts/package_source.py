"""Build a source-only archive from the inspected source files, excluding local output."""
import argparse
import hashlib
from pathlib import Path
import zipfile
from check_public_tree import ROOT, check_tree, check_zip, source_files

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('output', type=Path, help='New directory outside the source tree')
args = parser.parse_args()
out = args.output.resolve()
if out == ROOT or ROOT in out.parents:
    raise SystemExit('Output must be outside the source tree')
check_tree(ROOT)
out.mkdir(parents=True, exist_ok=False)
archive = out / 'DiPlay-Preface-source.zip'
with zipfile.ZipFile(archive, 'x', compression=zipfile.ZIP_DEFLATED) as target:
    for path in source_files(ROOT):
        target.write(path, path.relative_to(ROOT).as_posix())
check_zip(archive)
readme = out / 'BUILD-README.md'
readme.write_text('''# DiPlay 星瑞：源码与构建工具

此目录只提供源码，不含可安装包、配件认证身份或私钥。先阅读源码内 README.md、docs/BUILD.md 和 docs/RELEASE-POLICY.md。使用者自行提供所需本地输入并组装、签名和使用。

Workers 线上授权部署见 license-workers/README.md；需要自己的 Cloudflare 账户、D1 和 HTTPS 地址。源码交付不表示已经部署或通过实车连接验证。

Source and build tools only. No installable package, accessory identity or private key is supplied. See the included build and Workers deployment documentation. Preserve the GPLv3 license and third-party attribution.
''', encoding='utf8')
(out / 'SHA256SUMS.txt').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest() + '  ' + p.name + '\n' for p in [archive, readme]), encoding='ascii')
print('Source-only archive created:', archive)
