# DiPlay Preface maintenance

- Follow original shihabal3amri/DiPlay as the code baseline and default update source. Import carlito Geely adaptations selectively with provenance; do not restore automatic whole-branch Geely merges.
- Target Android 5.1 / API 22 and E01. Retain necessary API 23+ fallbacks, low-load settings and validated fixes; do not reintroduce API 18-only compatibility.
- The user-provided APK is a local behavior reference. Do not mention it in README or distribute it. Preserve historical third-party attribution.
- Keep one root README, Chinese first and English below. Release descriptions follow the same order.
- Preserve com.shihab.diplay.e01legacy and the existing signer for upgrades. Only full standalone builds may be delivered for installation; follow docs/BUILD.md authentication and signature gates.
- Keep credentials outside Git. Do not claim real vehicle connectivity based on desktop tests.
- Run relevant common/shared regression tests, API 22 lint, source APK checks and scripts/tests for maintenance changes. Keep published release tags stable.
