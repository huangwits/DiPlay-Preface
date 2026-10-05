# Upstream-first Android 5.1 / E01 maintenance

The code baseline is the original [shihabal3amri/DiPlay](https://github.com/shihabal3amri/DiPlay), currently `2fc876e578eba3905a5b873e3c2dbd74f498433a` (0.2.12). `upstream` points directly to that repository. `geely` points to [carlito12345/DiPlay](https://github.com/carlito12345/DiPlay) for selective vehicle changes; it is not the automatic merge source.

The GitHub repository stays at `huangwits/DiPlay-Preface`, retaining its existing releases and fork-network membership. GitHub's historical direct parent remains carlito; the network source is the original DiPlay. That display does not control local Git remotes or update policy. No repository deletion, force-push or published-tag movement is required.

## Layered migration

The migration starts at the original-author commit, adds the reviewed Geely runtime, resources, dependencies and supporting documentation from `84050d6` (full commit in the migration manifest), then applies the E01 changes from former main `11136b93df966d09eb63e5c2b9d83aac84b8fa3e` and removes obsolete pre-API-22 paths. See [migration details](UPSTREAM-FIRST-MIGRATION.md) and `maintenance-sources.json` for exact source identifiers and the selected-file list.

Preserved E01 behavior includes audio focus/read/write fallbacks, Media3 gating, bounded USB transfers, Java/network compatibility, H.264/30 fps/canvas limits, factory Bluetooth detection and complete-build authentication validation. Factory connection detection does not provide a verified vendor RFCOMM backend.

The user-provided APK remains a local reference for interface behavior. Do not describe that reference in the root README or publish the reference binary. Historical reused code retains its attribution in THIRD_PARTY_NOTICES.md.

## Updates

Use **Actions → Review upstream updates**:

- `original` (default): merge original DiPlay main into a temporary `sync/original-<sha>` branch.
- `geely`: supply one reviewed full, non-merge commit SHA. Only that commit is cherry-picked with provenance onto `sync/geely-<sha>`. Do not merge the whole Geely branch.

The workflow opens a draft PR and explicitly runs Android 5.1 E01 checks. Conflicts are aborted and reported. Existing remote candidates are reused, never overwritten. Main and releases are not changed automatically. For local use, fetch `upstream main` and `geely main`, then run `python scripts/prepare_upstream_update.py --source original` or `--source geely --commit <full-sha>` from a clean review checkout.

Before accepting an update, inspect API 22 behavior, E01 limits and affected vehicle integrations. Run maintenance tests, public-tree checks, common/shared unit tests, NewApi lint and a source-only APK build. Changes affecting connections/audio/video still require vehicle testing; automated checks cannot establish E01 vendor connectivity.

Keep one bilingual README.md, Chinese above English. Use the same language order in release descriptions. Complete car-test builds require explicit local runtime inputs and the APK bootstrap tests described in BUILD.md; CI source-only builds are never installation deliverables.
