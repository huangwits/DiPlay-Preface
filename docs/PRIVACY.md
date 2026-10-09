# Privacy and diagnostics

普通 `e01` 与历史离线 `e01Public` 使用本地软件授权/认证流程。可选 `e01Licensed` 会向构建时配置的 Workers HTTPS 服务发送本次安装的设备公钥、其 SHA-256 摘要、包名、安装签名摘要及签名挑战，请求或刷新软件授权。车机只在授权页面发起请求；待批准时每分钟查询一次，也可手动刷新，离开页面或 20 分钟后停止。应用不向服务发送配件认证身份、私钥、微信内容或安装包。

D1 stores the installation public-key hash, random request number, license status/expiry and request times; the public key is used for proof verification without being stored. Pending requests inactive for 30 days are removed, audit decisions are retained for 180 days, and approved/revoked records remain until the operator handles deletion. Infrastructure processes IP addresses; the application's rate-limit hashes expire shortly. The administrator token stays in page memory and is cleared on reload, navigation or logout. See [the Workers data and deployment notes](../license-workers/README.md). Service operators are responsible for their own infrastructure configuration and data handling.

CarPlay accessory authentication remains local and uses a direct USB/Wi-Fi connection to the iPhone, independently of optional online software licensing. The iPhone's CarPlay apps have their own internet and privacy behavior.

Diagnostic reports are not uploaded automatically. In Settings, the user may enter a problem description and explicitly upload one redacted diagnostic report to the private project cloud. The upload contains that description, head-unit and connection information, and the same redacted session logs available through Save diagnostic report. It excludes protocol payloads, credentials and accessory identities.

The head unit stores app preferences, paired-device selections, pairing data and bounded diagnostic logs in app storage. Authentication and pairing material are kept out of Android backup. Uninstalling removes app-private data; exported reports in Downloads remain until you delete them.

Diagnostic export is initiated by you. Reports include app/device versions, display settings and negotiation, connection transitions, Wi-Fi band/channel and state, and decoder recovery events. The exporter filters protocol payloads, credential-bearing lines and common identifiers. Redaction cannot promise to recognize every vendor-specific string: review reports before posting them publicly. A GitHub issue is public.

When the document picker or Downloads storage is unavailable, reports save under `Android/data/<package>/files/diagnostic-reports/` on primary external storage without requesting storage permission. The confirmation shows the actual TXT file path. The release package is `com.shihab.diplay.preface`. File managers on newer Android versions may restrict access to `Android/data`; use **View** or **Share** in DiPlay instead. If external storage is also unavailable, reports save in private app storage. Each fallback location retains its newest eight exports, and uninstalling removes them. Sharing grants read access to the selected report only; nothing is sent automatically. On Android 9 and older, other apps with storage permission may be able to read the external reports.

Microphone access supports Siri and calls. Bluetooth/Nearby devices and Wi-Fi/Location permissions support discovery and transport. The optional local VPN permission supports the USB link; it does not provide a remote internet VPN.

Usage Access is optional for hiding the launcher map over other apps. Android HOME activities identify launchers; foreground activity events are processed locally. This build does not query BYD instrument, battery, gear or wheel-speed services.

The static website has no analytics script or account. GitHub Pages, GitHub and Telegram apply their own policies when you use those services.


2026-10-09 更新：USB 普通连接不使用软件授权页面，也不触发软件授权请求；Android USB 权限和配件认证保持原流程。软件授权入口只保留在蓝牙工具中，连接设置不再显示该入口。
