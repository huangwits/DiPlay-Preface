"""Generate project download, setup and source documentation entry points."""
from pathlib import Path
import json
from html import escape
ROOT = Path(__file__).resolve().parents[1]
SITE = ROOT / 'site'
config = json.loads((SITE / 'content.json').read_text(encoding='utf8'))
repo = config['repository']
html = '''<!doctype html>
<html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>DiPlay 星瑞 · 下载与使用</title><style>
*{box-sizing:border-box}body{margin:0;background:#f1f5f4;color:#1b3537;font:16px/1.8 system-ui,"Microsoft YaHei",sans-serif}main{max-width:900px;margin:60px auto;padding:24px}header{border-bottom:1px solid #cad9d5;padding-bottom:28px}h1{font-size:40px;line-height:1.3}h2{font-size:23px}a{color:#116a5d}nav{display:flex;gap:12px;flex-wrap:wrap;margin:24px 0}nav a{background:#176c62;color:white;padding:10px 20px;border-radius:8px;text-decoration:none}section{background:white;padding:24px 28px;margin:24px 0;border-radius:14px}footer{font-size:13px;color:#536e6b}@media(max-width:500px){main{margin:12px auto;padding:16px}h1{font-size:30px}section{padding:20px}}
</style></head><body><main>
<header><span>DiPlay · Preface</span><h1>DiPlay 星瑞 CarPlay</h1><p>社区维护的 Android 车机项目，面向 Android 5.1 / E01。兼容性仍须按车辆、固件及手机组合验证。</p>
<nav><a href="REPO/releases/latest">下载安装包</a><a href="REPO#readme">使用说明</a></nav></header>
<section><h2>下载与连接</h2><p>在 GitHub Release 附件中下载正式 APK。更新时直接覆盖安装，不要先卸载，以保留设置和授权信息。</p><p>USB 无需软件激活。无线连接请在蓝牙工具页申请授权；通过后右侧申请栏自动收起，下次进入自动核验。首次适配请按应用内完整操作说明进行。</p></section><section><h2>使用须知</h2><p>本项目为社区非官方适配，与 Apple、吉利没有隶属或认证关系。请在安全停车时安装和操作蓝牙工具，提前确认备份及恢复方法。兼容性因车机、固件和手机而异，不保证未来系统兼容或服务持续可用。</p><a href="REPO/blob/main/docs/DISCLAIMER.md">阅读完整免责声明</a></section>
<section lang="en"><h2>Download and use</h2><p>Download the validated APK from GitHub Releases. Update without uninstalling to retain settings and activation information. USB does not require software activation; wireless connections require approval and online revalidation.</p><p>This is an unofficial community adaptation. Perform setup and maintenance only while parked, verify backups and recovery procedures, and read the disclaimer. Compatibility and continued service availability are not guaranteed.</p></section>
<footer>保留原作者及第三方署名，遵循 GPLv3。<a href="REPO/blob/main/LICENSE">许可证</a> · <a href="REPO#readme">项目与致谢</a></footer>
</main></body></html>'''.replace('REPO', escape(repo, quote=True))
# Existing language URLs remain valid entry points, with the current bilingual policy.
pages = {SITE / 'index.html', *SITE.glob('*/index.html')}
for page in pages:
    page.write_text(html, encoding='utf8')
print('Generated', len(pages), 'project entry pages')
