# Privacy and diagnostics

DiPlay's product flow uses local authentication and a direct USB/Wi-Fi connection to the iPhone. No account or remote authentication service is used. The iPhone's CarPlay apps have their own internet and privacy behavior.

Diagnostic reports are not uploaded automatically. In Settings, the user may enter a problem description and explicitly upload one redacted diagnostic report to the private project cloud. The upload contains that description, head-unit and connection information, and the same redacted session logs available through Save diagnostic report. It excludes protocol payloads, credentials and accessory identities.

The head unit stores app preferences, paired-device selections, pairing data and bounded diagnostic logs in app storage. Authentication and pairing material are kept out of Android backup. Uninstalling removes app-private data; exported reports in Downloads remain until you delete them.

Diagnostic export is initiated by you. Reports include app/device versions, display settings and negotiation, connection transitions, Wi-Fi band/channel and state, and decoder recovery events. The exporter filters protocol payloads, credential-bearing lines and common identifiers. Redaction cannot promise to recognize every vendor-specific string: review reports before posting them publicly. A GitHub issue is public.

When the document picker or Downloads storage is unavailable, reports save under `Android/data/<package>/files/diagnostic-reports/` on primary external storage without requesting storage permission. The confirmation shows the actual TXT file path. The release package is `com.shihab.diplay.preface`. File managers on newer Android versions may restrict access to `Android/data`; use **View** or **Share** in DiPlay instead. If external storage is also unavailable, reports save in private app storage. Each fallback location retains its newest eight exports, and uninstalling removes them. Sharing grants read access to the selected report only; nothing is sent automatically. On Android 9 and older, other apps with storage permission may be able to read the external reports.

Microphone access supports Siri and calls. Bluetooth/Nearby devices and Wi-Fi/Location permissions support discovery and transport. The optional local VPN permission supports the USB link; it does not provide a remote internet VPN.

Usage Access is optional for hiding the launcher map over other apps. Android HOME activities identify launchers; foreground activity events are processed locally. This build does not query BYD instrument, battery, gear or wheel-speed services.

The static website has no analytics script or account. GitHub Pages, GitHub and Telegram apply their own policies when you use those services.
