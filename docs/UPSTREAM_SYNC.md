# Updating the Android 5.1 E01 adaptation

The maintained base is `carlito12345/DiPlay`, which integrates changes from `shihabal3amri/DiPlay`. Prefer the Geely base so that original-author updates do not bypass Geely modifications.

`android51-e01` owns this project's compatibility changes. `main` retains the fork's upstream snapshot. The initial validated adaptation is based on `049e080`; the newer initial fork main `6b2b3b9` has not been merged into it.

The **Review carlito updates** workflow is manual. It fetches carlito main and attempts a merge on `sync/carlito-<sha>`. If conflicts occur, it reports the files and pushes nothing. If successful, it pushes a candidate, explicitly dispatches **Android 5.1 E01 checks**, and opens a draft PR targeting `android51-e01`. GitHub must allow Actions to create pull requests. No automatic merge occurs; a clean Git merge is not proof of runtime compatibility.

Use **Actions → Review carlito updates → Run workflow** after the adaptation branch is set as the repository default. Existing workflow branches are not force-pushed. Check for an existing draft before retrying. Review the actual candidate commit's workflow results before merging. PR and workflow creation may require enabling Actions in a new fork.

Before accepting an update:

1. Preserve API 22, E01 defaults and older Android API fallbacks.
2. Run public-source credential checks, unit tests, NewApi checks and source-only builds.
3. Provision private build inputs only outside the source tree when creating a standalone vehicle package.
4. Validate on the vehicle. Standard Bluetooth tests do not verify the E01 vendor data channel.

The former automatic upstream-to-main merge workflow is replaced on the adaptation branch. This repository does not automatically publish releases containing authentication materials.
