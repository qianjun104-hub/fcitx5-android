# Personal Chinese AI IME recovery checkpoint

## Verified remote baseline (2026-10-07)

- Target repository: `qianjun104-hub/fcitx5-android`.
- Default branch: `master`; only existing remote branch at recovery.
- Baseline: `e6199a2801b0c76e1baeff7a48f6b18e910e9dd7`, upstream
  "Update fcitx5 submodules and prebuilt", 2026-09-23 15:48:36 UTC.
- No open pull requests or Actions runs at recovery. No personal IME work had
  been persisted to this fork; the interrupted Work environment is not a source
  of recoverable progress.
- Separate experimental repository `qianjun104-hub/OpenRouterVoiceTest` has
  `main` and `feat/qianyu-fcitx-ime` at `bd649f7d53ae4715d1fec9ef4d0a2816fc4579a3`.
  Latest Actions run 37516070476 succeeded; earlier resource-linking failure
  (run 37515843278) was missing `string/ime_name` and is fixed in that HEAD.
  Branch `build-v2-safe` is older (68a03a5, 2026-10-06 18:01:34 UTC).
- This fork remains the production base. The separate prototype remains an API
  experiment; do not replace Fcitx's pinyin engine or learning infrastructure.

## Current work

- Development branch: `work/personal-ai-ime`.
- Added CI that builds an arm64 APK, runs unit tests and lint, and uploads reports.
- Read the current Gradle/native build configuration, service lifecycle and
  toolbar integration. The upstream microphone invokes Android speech recognition;
  user-configured ASR and conservative AI cleanup are not yet integrated.

## Required next steps

1. Add encrypted local ASR/text-provider settings and microphone permission flow.
2. Integrate bounded audio capture and cancellable ASR/cleanup in the existing IME.
3. Preserve numbers, abbreviations and medical terms; fall back to raw ASR on
   cleanup failure or rejected transformation. Provide safe original restoration.
4. Cancel on editor changes/hiding; reject password and no-personal-learning fields;
   never insert an asynchronous result into a different editor.
5. Inspect CI failures, fix and push checkpoints. Verify API calls on a device only
   once the user configures their own credentials; do not put keys in Git or logs.

## Resuming

Fetch all branches and inspect this file, latest commits and Actions logs. Build
the newest persisted `work/personal-ai-ime` commit. Never assume local files or
an in-progress conversation survived an interruption.
