# S789 / SukiSU-Ultra Custom Manager Handoff

## 1. Purpose of This Document

This document is a detailed handoff for a new AI or engineer taking over the S789 custom manager work on top of `SukiSU-Ultra`.

The goal is that a fresh agent can open this file and continue work without needing the original chat history.

This document covers:

1. Repository layout and branch model.
2. What was changed compared to upstream.
3. How the custom build works.
4. Why specific workflow and native-library decisions were made.
5. How manager recognition and certificate hashing work.
6. How the hidden-module feature currently works.
7. Known weaknesses, open issues, and the safest next steps.

---

## 2. Repository / Workspace Context

Primary working clone:

- `H:\1\webui\sukisu-ultra-fork`

Reference copy kept in sync during the work:

- `H:\1\webui\sukisu-ultra`

Portable Git used in this workspace:

- `H:\1\webui\mingit\cmd\git.exe`

Historically used local Android toolchain paths:

- JDK: `H:\1\webui\jdk-21.0.10+7`
- Android SDK: `H:\1\webui\android-sdk`

Important note about local builds on this Windows host:

- Local Gradle builds were unreliable because Maven / Google artifact downloads failed due TLS / networking issues on this machine.
- Because of that, release verification was done primarily through GitHub Actions, not local full release builds.
- If a future AI sees local Gradle dependency handshake failures, that is consistent with previous behavior and is not necessarily caused by source changes.

---

## 3. Current Branch / Remote Model

Local and remote branch model intentionally separated upstream tracking from custom work:

- `origin/main`
  Fork default branch, mirrors the fork's main line.

- `codex/upstream-main`
  Long-lived branch intended to track `upstream/main` cleanly.

- `codex/s789-manager-custom`
  Long-lived custom branch containing all S789-specific changes.

Remotes:

- `origin` -> user fork (`s78910/SukiSU-Ultra`)
- `upstream` -> upstream project (`SukiSU-Ultra/SukiSU-Ultra`)

Maintenance script added for this model:

- `scripts/sync_upstream.ps1`

Maintenance notes:

- `docs/S789_MAINTENANCE.md`

Recommended update flow:

1. Refresh `codex/upstream-main` from `upstream/main`.
2. Merge `codex/upstream-main` into `codex/s789-manager-custom`.
3. Resolve conflicts only on `codex/s789-manager-custom`.
4. Build releases only from `codex/s789-manager-custom`.

This structure was chosen so upstream sync does not overwrite custom manager work.

---

## 4. Important Commit History on `codex/s789-manager-custom`

The key commits, in order from oldest relevant custom work to newest:

1. `bebcbe2a`  
   `Customize manager branding and staged build workflows`

2. `44896baa`  
   `Bundle official manager libs for baseline and final builds`

3. `ae1611dc`  
   `Resolve official manager libs from latest release`

4. `eae5d030`  
   `Keep bundled native libs while adding official manager libs`

5. `8003b1dc`  
   `Inject only libksud from official manager APK`

6. `32567fea`  
   `Refine hidden module debug mode UX`

7. `205afe6c`  
   `Persist hidden module state under root storage`

8. `3df3b873`  
   `Skip password when exiting debug mode`

9. `2bbee420`  
   `Add maintenance workflow and move auth sentinel`

10. `deff8c08`  
    `Remove unused GitHub workflows`

Current branch HEAD at the time this document was written:

- `deff8c08`

Important nuance:

- The last known successful APK build was produced from commit `2bbee420`.
- Commit `deff8c08` only removed extra workflow files; it did not change APK runtime code.
- If a future AI wants an artifact built from absolute latest HEAD, rerun the single remaining workflow: `Build S789 Final`.

---

## 5. Scope of Customization

This work customized the `SukiSU-Ultra` manager specifically, not `KsuWebUI`.

The primary customization goals were:

1. Change the package name to `com.s789.sk`.
2. Change app branding / text while leaving version numbering behavior intact.
3. Add a hidden-module feature with password-protected debug mode.
4. Make hidden-module state survive APK update / reinstall.
5. Keep the manager recognizable by a custom kernel once the user updates the kernel’s expected manager certificate hash.
6. Simplify the repository so only the single intended release workflow remains.

---

## 6. User-Facing Branding Changes

### 6.1 Package Name

The final manager build uses:

- `com.s789.sk`

Implementation:

- `manager/app/build.gradle.kts`

This is controlled via:

- environment variable `MANAGER_APPLICATION_ID`
- default value now: `com.s789.sk`

The `namespace` remains `com.sukisu.ultra`.  
Only the `applicationId` changed, which is correct for this use case.

### 6.2 App Name and Home Text

Main strings changed in:

- `manager/app/src/main/res/values/strings.xml`

Relevant user-visible changes:

1. App name -> `SSSKI`
2. Home title -> intended text: `刷机做环境找微信S78910JQKKKAA`
3. Built-in tag -> `S789`

Home screen wiring changed in:

- `manager/app/src/main/java/com/sukisu/ultra/ui/screen/Home.kt`

This ensures the top-left home title and built-in tag use the new resource strings.

Additional localized string resources were also touched:

- `manager/app/src/main/res/values-zh-rCN/strings.xml`
- `manager/app/src/main/res/values-zh-rTW/strings.xml`

---

## 7. Build / Versioning Changes

### 7.1 Manager Application ID Override

File:

- `manager/app/build.gradle.kts`

Behavior:

- Reads `MANAGER_APPLICATION_ID` from environment.
- Defaults to `com.s789.sk`.

This originally supported a staged approach where different package IDs could be built from the same source tree.

### 7.2 Fallback Versioning for ZIP / Non-Git Builds

File:

- `manager/build.gradle.kts`

Problem solved:

- ZIP-based or constrained environments may not have reliable Git metadata.
- The manager build previously relied on Git information for version code / version name.

Current behavior:

- `MANAGER_VERSION_CODE` env var can override version code.
- `MANAGER_VERSION_NAME` env var can override version name.
- If Git metadata is unavailable, falls back to:
  - `fallbackManagerVersionCode = 40545`
  - `fallbackManagerVersionName = "v4.1.2"`

This avoids manager build failures in environments where Git commands are unavailable or the repository is not fully intact.

---

## 8. Userspace Package Name Adjustments

Files changed:

- `userspace/ksud/src/cli.rs`
- `userspace/ksud/src/utils.rs`

Relevant changes:

1. `Debug::SetManager` default package now points to `com.s789.sk`.
2. Uninstall-related logic now references `com.s789.sk`.

Why this matters:

- The manager package name is no longer upstream default.
- Debug / uninstall flows should use the customized manager package name consistently.

---

## 9. Final Workflow Design

### 9.1 Only Remaining Workflow

After cleanup, the repository keeps only one workflow file:

- `.github/workflows/build-s789-final.yml`

Current trigger:

- `workflow_dispatch` only

This means:

- no automatic push builds
- no accidental extra runs
- the user explicitly triggers builds when needed

### 9.2 Workflow Responsibilities

The `Build S789 Final` workflow does the following:

1. Checks out the repository.
2. Requires signing secrets:
   - `KEYSTORE`
   - `KEYSTORE_PASSWORD`
   - `KEY_ALIAS`
   - `KEY_PASSWORD`
3. Sets up Java 21.
4. Sets up Gradle and Android SDK.
5. Downloads the latest upstream official manager APK from GitHub Releases.
6. Extracts only `libksud.so` from that APK.
7. Copies `libksud.so` into local `jniLibs`.
8. Configures signing for the manager module.
9. Builds the release APK.
10. Extracts certificate size + SHA-256 from the built APK using `scripts/extract_apk_signature.py`.
11. Writes a helper text file describing the kernel-side hash configuration needed.
12. Uploads the artifact as `S789-Final`.

### 9.3 Why Only `libksud.so` Is Injected

This was a key technical decision.

Observed issues during earlier workflow iterations:

1. Official APK download URL was initially hardcoded and broke.
2. Clearing all local `jniLibs` broke required bundled libraries like `libzakosign.so`.
3. Copying official `libkernelsu.so` caused duplicate-library conflicts because the project already builds its own `libkernelsu.so`.

Final conclusion:

- Keep repository native libs in place.
- Inject only missing `libksud.so` from the official APK.

Why:

- `libksud.so` is needed for manager / module CLI behavior.
- `libkernelsu.so` should remain the project-built copy.
- `libzakosign.so` and other repository libs must not be discarded blindly.

This rationale is critical.  
A future AI should not “optimize” this by replacing all native libs with upstream ones unless it has revalidated the full packaging behavior.

---

## 10. APK Signature Extraction Helper

File added:

- `scripts/extract_apk_signature.py`

Purpose:

1. Parse the APK v2 signing block.
2. Extract the leaf certificate payload length.
3. Compute SHA-256 over the certificate bytes.
4. Produce a human-readable report.
5. Export values to GitHub Actions outputs.

Why this exists:

- Kernel recognition depends on certificate size and hash, not simply package name or version code.
- The workflow needs to emit the exact values for the user to patch into the kernel or `dynamic_manager`.

---

## 11. Manager Recognition / Signature Model

### 11.1 Core Reality

The custom manager is not recognized just because it is “based on official”.

Recognition is driven primarily by the manager certificate expected by the kernel.

Earlier analysis established:

- Upstream / kernel logic compares manager certificate size and SHA-256.
- Version name / version code are not the main recognition gate.
- Package name may matter only if the kernel was compiled with explicit package-name enforcement (`KSU_MANAGER_PACKAGE` or equivalent logic path).

### 11.2 Practical Consequences

1. Official manager works because it is signed with the official private key expected by upstream kernel defaults.
2. A self-built manager signed with a different key will have a different certificate hash.
3. If the user’s kernel is patched to accept the custom manager certificate hash, self-built APKs can be recognized.
4. If the same custom keystore is reused, the certificate hash remains stable across future builds.

### 11.3 Workflow Output

The workflow writes:

- `final-signature.txt`
- `final-kernel-config.txt`

These are intended to help the user patch kernel expectations or write `dynamic_manager`.

### 11.4 Important Limitation

This repository does not contain the official signing key.  
Therefore, all custom builds are expected to have a non-upstream certificate hash unless the user signs with a keystore matching the user’s patched kernel expectations.

---

## 12. Hidden Module Feature: User-Level Behavior

Main UI file:

- `manager/app/src/main/java/com/sukisu/ultra/ui/screen/Module.kt`

Current behavior:

1. Normal mode:
   - Hidden modules are not shown.
   - No selection checkboxes are visible.

2. Entering debug mode:
   - User taps top-right eye button.
   - Entering debug mode requires password.
   - After successful password verification, hidden modules become visible and selection checkboxes appear.

3. In debug mode:
   - Hidden modules show a `HIDDEN` badge.
   - Selection controls are visible.
   - The top action button uses `SwapHoriz` icon to toggle hidden state for selected modules.

4. Exiting debug mode:
   - Does not require password anymore.
   - App exit also naturally ends debug mode because the state is in UI memory, not persisted as “always-on debug mode”.

This UX was explicitly refined over multiple iterations to avoid:

- always-visible checkboxes
- confusing duplicated eye icons
- repeated password prompts for every hide/show action

---

## 13. Hidden Module Feature: Persistence / Security Design

Main storage file:

- `manager/app/src/main/java/com/sukisu/ultra/ui/util/HiddenModuleStore.kt`

### 13.1 Current Root-Persistent Files

State file:

- `/data/adb/ksu/.s789_module_state`

Auth / sentinel file:

- `/data/adb/ksu/bin/ksuda`

Master key file:

- `/data/adb/ksu/.s789_module_master`

### 13.2 Why Storage Moved Out of App Private Data

Earlier app-private storage under the manager package path was lost on uninstall / reinstall.

That failed the requirement that hidden-module state must survive manager removal and reinstall.

Moving state to `/data/adb/ksu` makes it persist across APK updates and reinstalls.

### 13.3 Current Encryption Model

The current design is:

1. `master` file stores 32 random bytes.
2. File-specific AES keys are derived from that master key using `HmacSHA256`.
3. State and auth payloads are stored as AES-GCM encrypted envelopes.
4. Passwords are not stored in plaintext.
5. Password verification uses PBKDF2-HMAC-SHA256 with random salt.

Current constants:

- AES mode: `AES/GCM/NoPadding`
- GCM nonce size: `12`
- Master key size: `32`
- PBKDF2 iterations: `120000`
- PBKDF2 key length: `256`

### 13.4 Current Serialization Structure

The encrypted envelope stores:

1. `version`
2. `nonce`
3. `ciphertext`

The state payload stores:

1. payload `version`
2. `hidden_ids` JSON array

The auth payload stores:

1. payload `version`
2. `salt`
3. `hash`

### 13.5 Current File IO Strategy

The store uses root shell commands and busybox:

- `getRootShell()`
- `/data/adb/ksu/bin/busybox base64`

Writes are done by:

1. creating parent directory if needed
2. writing to a temporary file
3. `chmod 600`
4. `mv -f` into place
5. `chmod 600` again

Important note:

- This is atomic enough for many cases because it stages through a temp file and rename.
- There is no explicit `fsync`.
- There is no explicit `chown`, but files are created from a root shell so ownership is effectively root-side.

### 13.6 Fail-Closed Behavior

The important security property is:

- if state cannot be read, modules default to hidden
- if auth cannot be read, modules default to hidden

This is represented by:

- `HiddenModuleSnapshot.shouldHideAllModules`

UI filtering uses that flag so that the normal list remains empty when the root persistence is missing or invalid.

---

## 14. Known Hidden-Module Security Limitation

This section is extremely important for any future AI.

### 14.1 Problem Not Fully Solved

Moving auth from `.s789_module_auth` to `/data/adb/ksu/bin/ksuda` improved obscurity, but it did **not** fully solve the underlying reset vulnerability.

Current logic still has this weakness:

1. If `ksuda` is deleted, `hasPassword` becomes false.
2. The UI treats that as “no password currently configured”.
3. The user can set a new password.
4. After password setup, debug mode can be entered again.

So the current system is:

- stronger than the original app-private storage approach
- stronger than keeping all three obvious files side-by-side
- **not** a complete anti-reset mechanism

### 14.2 Why This Matters

The user specifically identified this as a logic hole:

- deleting the auth/sentinel material should not silently downgrade the system into “first-time setup”.

That issue is only partially mitigated right now.

### 14.3 Recommended Future Fix Direction

The strongest next-step design would be:

1. Separate “initialized before” from “password auth file currently exists”.
2. Add a tamper-evident or external sentinel that cannot be trivially re-created from the manager alone.
3. Possible approaches:
   - recovery-code-based reinitialization
   - a separate immutable or harder-to-reset root-side sentinel
   - `ksud` or kernel-side initialization anchor

The most robust design would involve a `ksud` / kernel-side anchor rather than manager-only logic.

This remains open work.

---

## 15. Hidden-Module UI Logic Details

Key points inside `Module.kt`:

1. Hidden state is loaded from `HiddenModuleStore.loadSnapshot(context)`.
2. `showHiddenModules` is runtime-only UI state for debug mode.
3. `showSelectionControls` is tied to `showHiddenModules`.
4. Visible modules are filtered as:
   - all modules in debug mode
   - otherwise only non-hidden modules and only if `shouldHideAllModules == false`
5. Entering debug mode:
   - if password exists -> verify password
   - if password does not exist -> allow password setup
6. Exiting debug mode:
   - no password prompt
7. Toggling selected modules:
   - no extra password prompt once already in debug mode

This file is the main place to inspect if future UX changes are needed.

---

## 16. Why Module Listing Was Broken Earlier

This was a major issue in the original build attempts.

Observed symptom:

- manager installed, but module list was empty / modules were not recognized

Root cause:

- built APK lacked `libksud.so`

Fix:

- final workflow now injects `libksud.so` from the latest official upstream manager release APK

Do not remove that workflow step without revalidating module operations.

---

## 17. Workflow Cleanup and Current State

Earlier in the work, the fork accumulated many workflows:

- custom build experiments
- baseline / compatible / final variants
- inherited upstream workflows
- signing / build-manager workflows that were not needed

This was cleaned up.

Current state:

- only `build-s789-final.yml` remains under `.github/workflows`

Why this matters:

1. no accidental extra runs
2. no push-triggered noise
3. easier repository maintenance
4. easier handoff to future agents

Historical note:

- Historical GitHub Actions runs before `2026-03-22` were explicitly deleted during cleanup.

---

## 18. Maintenance Documentation Added

Added files:

- `docs/S789_MAINTENANCE.md`
- `scripts/sync_upstream.ps1`

These exist to formalize long-term maintenance.

`sync_upstream.ps1` is intended to:

1. ensure `upstream` remote exists
2. fetch `upstream/main`
3. refresh `codex/upstream-main`
4. merge `codex/upstream-main` into `codex/s789-manager-custom`
5. optionally push both branches

This script reduces context loss for future updates.

---

## 19. Files Most Relevant to Future Work

If a future AI needs to continue this work, these files are the highest-priority ones to read first:

### Build / workflow

- `.github/workflows/build-s789-final.yml`
- `scripts/extract_apk_signature.py`
- `scripts/sync_upstream.ps1`
- `docs/S789_MAINTENANCE.md`

### Manager branding / package

- `manager/app/build.gradle.kts`
- `manager/build.gradle.kts`
- `manager/app/src/main/res/values/strings.xml`
- `manager/app/src/main/java/com/sukisu/ultra/ui/screen/Home.kt`

### Hidden-module feature

- `manager/app/src/main/java/com/sukisu/ultra/ui/screen/Module.kt`
- `manager/app/src/main/java/com/sukisu/ultra/ui/util/HiddenModuleStore.kt`

### Userspace package handling

- `userspace/ksud/src/cli.rs`
- `userspace/ksud/src/utils.rs`

---

## 20. Known Good / Known Important Facts

1. Package ID target is `com.s789.sk`.
2. Custom app name target is `SSSKI`.
3. Build verification should prefer GitHub Actions over local full builds on this host.
4. Only one workflow remains and it is manual.
5. `libksud.so` injection is intentional and required.
6. Hidden state survives app reinstall because it is no longer stored under app private data.
7. Hidden-module reset protection is improved but not fully solved.
8. Current latest code cleanup commit is `deff8c08`.
9. Last known successful APK build came from `2bbee420`.

---

## 21. Recommended Next Steps for a Future AI

If continuing this project, the most valuable next steps are:

1. Re-run `Build S789 Final` from latest HEAD (`deff8c08`) to have a current artifact built after workflow cleanup.
2. Address the remaining hidden-module reset weakness around `ksuda` deletion.
3. If strong tamper-resistance is required, move sentinel / initialization truth into `ksud` or kernel side instead of manager-only logic.
4. Keep `codex/upstream-main` fresh and periodically merge upstream into the custom branch.
5. Revalidate workflow behavior if upstream changes manager native library packaging.

---

## 22. Quick Summary for Fast Orientation

If a new AI only has one minute:

1. Work branch is `codex/s789-manager-custom`.
2. Upstream tracking branch is `codex/upstream-main`.
3. Only workflow left is `Build S789 Final`.
4. Final manager package is `com.s789.sk`.
5. Branding is `SSSKI` + custom home title / `S789`.
6. Hidden modules are implemented in `Module.kt` + `HiddenModuleStore.kt`.
7. Hidden state lives in `/data/adb/ksu`, not app-private storage.
8. `ksuda` in `/data/adb/ksu/bin/ksuda` acts as current auth/sentinel file.
9. `libksud.so` injection from upstream official APK is required.
10. Biggest remaining issue: deleting auth material can still eventually allow password reset; this is not fully hardened yet.
