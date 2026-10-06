#!/usr/bin/env python3
"""Build the Geely Preface download page."""
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
SITE = ROOT / 'site'
REPO = 'https://github.com/huangwits/DiPlay-Preface'
html = '''<!doctype html>
<html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>DiPlay · 吉利星瑞</title>
<style>body{font:18px/1.7 system-ui,sans-serif;max-width:800px;margin:64px auto;padding:0 24px;color:#dae7f4;background:#101923}a{color:#9fd5ff}h1{font-size:40px}section{margin:40px 0}small{color:#a7b7c8}</style>
<main><h1>DiPlay · 吉利星瑞</h1>
<p>面向 Android 5.1 及以上的吉利星瑞车机，保留系统蓝牙、E01 ECARX 手动连接、吉利 HUD 和方向盘识别。</p>
<p><a href="REPO/releases/latest">下载最新完整安装包</a> · <a href="REPO/blob/main/docs/GEELY-PREFACE-SCOPE.md">功能范围与兼容性</a></p>
<p>请覆盖安装完整 APK。实际手机与实车连接仍需验证。</p>
<section lang="en"><h2>Geely Preface</h2><p>CarPlay receiver for Android 5.1+ with System Bluetooth, manual E01 ECARX selection, Geely HUD and learned steering controls.</p>
<p><a href="REPO/releases/latest">Download the latest full APK</a> · <a href="REPO">Source and installation notes</a></p><p>Phone and vehicle connectivity still require real-device validation.</p></section>
<small>Based on <a href="https://github.com/carlito12345/DiPlay">carlito/DiPlay</a> and original <a href="https://github.com/shihabal3amri/DiPlay">DiPlay</a>. <a href="REPO/blob/main/docs/THIRD_PARTY_NOTICES.md">Attribution and licenses</a>.</small></main></html>
'''.replace('REPO', REPO)
(SITE / 'index.html').write_text(html, encoding='utf-8')
for path in SITE.glob('*/index.html'):
    path.write_text('<!doctype html><meta charset="utf-8"><meta http-equiv="refresh" content="0;url=../index.html"><a href="../index.html">DiPlay · 吉利星瑞 / Geely Preface</a>\n', encoding='utf-8')
