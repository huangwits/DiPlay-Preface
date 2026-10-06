# DiPlay Preface maintenance

- Follow carlito12345/DiPlay as the code baseline and default update source, as requested by the owner on 2026-10-06. Keep original-author attribution; import direct shihabal3amri changes only as explicitly reviewed commits.
- Default to Android system Bluetooth. Offer E01 ECARX and H52 ANW as explicit manual choices, independently of the E01 performance profile. Keep the API-18 H52 fork as a selected transport source only.
- Ship a Simplified-Chinese-only app: Chinese default resources, no language picker or foreign translations. Preserve language migration and APK locale filtering when syncing upstream.
- Focus on Geely Preface: no BYD/DiLink vehicle services, fixed key codes, rotating-screen canvas or vendor instrument UI. Retain shared protocols and historical attribution.
- Target Android 5.1 / API 22 and E01. Retain necessary API 23+ fallbacks, low-load settings and validated fixes; do not reintroduce API 18-only compatibility.
- The user-provided APK is a local behavior reference. Do not mention it in README or distribute it. Preserve historical third-party attribution.
- Keep one root README, Chinese first and English below. Release descriptions follow the same order.
- Preserve com.shihab.diplay.e01legacy and the existing signer for upgrades. Only full standalone builds may be delivered for installation; follow docs/BUILD.md authentication and signature gates.
- Keep credentials outside Git. Do not claim real vehicle connectivity based on desktop tests.
- Run relevant common/shared regression tests, API 22 lint, source APK checks and scripts/tests for maintenance changes. Preserve published tags unless the owner explicitly requests removal; back up refs, release metadata and assets before authorized cleanup.
