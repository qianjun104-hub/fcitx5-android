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
  First baseline run failed because setup-android's default obsolete `tools`
  package no longer exists; specifying `platform-tools` fixes that setup failure.
- Implemented native keyboard voice window, Chinese settings/test activities,
  microphone permission flow, independent configurable ASR/text endpoints and
  model IDs, encrypted Keystore-backed keys excluded from backup, and 16 kHz WAV.
- ASR supports OpenRouter JSON and OpenAI-compatible multipart. Cleanup masks
  numbers, medical glossary terms, abbreviations and clinical qualifiers, rejects
  new/reordered words, and falls back to raw ASR on errors/truncation/rejection.
  This rejection gate does not prove that a model preserved every fact.
- Async insertion is bound to the same editor session, selection and surrounding
  anchor text. Cancellation on editor changes/hiding aborts HTTP requests and
  releases/erases bounded recording buffers. Exact unchanged insertions can be
  restored to raw ASR; edited/moved text is not overwritten.
- Added unit tests for WAV encoding, cleanup guards, private/password editors,
  provider request formats, API failure fallback and network cancellation.
- First feature build (run 37556227980) compiled and generated an APK; all 20 new
  unit tests passed. Its sole failing test was upstream ThemeSerializationTest's
  stale assumption that version 2.0 does not migrate to current version 2.1.
  The corrected test now checks the actual candidate-color migration.
- A fresh installation now enables US keyboard and pinyin with pinyin as default,
  and activates Chinese mode on first focus, regardless of device locale.
  Existing profiles/learning are left intact.
- Tightened cleanup: omitted symptoms/plans are rejected, not just added words.
  Deletion is limited to unambiguous fillers and adjacent repeated phrases; units,
  temperature, percent and laboratory sign symbols are protected too.
- Key entry uses a non-restoring password dialog so a rotation cannot put the
  plaintext key into EditTextPreferenceDialogFragment's saved-state Bundle.
- Added Android 16 emulator integration checks for native pinyin, installed IME
  microphone entry, missing-key behavior, encrypted key storage, recording/cancel,
  keyboard hiding and password fields. Reports/screenshots are CI artifacts.
- No live API or physical-device tests have run; no real API credentials have
  been requested or used.
- Verification run 37558042778 built the APK and Android test package; all 28
  unit tests passed. Lint found upstream icon-tint and Japanese format-string
  mistakes (fixed), plus incomplete translations. Only 266 missing translations
  confirmed against the recovered upstream commit are recorded in lint-baseline.xml;
  new lint errors remain fatal. The personal Chinese voice title is intentionally
  not localized. Android 16 smoke found that Android's placeholder subtype
  switched back to English after native initialization; its initial subtype now
  points to pinyin. Explicit dynamic subtype choices remain available. Screenshots
  use a shell diagnostic directory because UTP removes the test app after a run.
- Expanded installed-IME recording verification to an independent test APK's
  foreground editor (different process and UID). In-app recording alone cannot
  verify Android microphone access while another app is foreground.
- Cleanup also rejects newly added emoji/markdown symbols; only ordinary
  punctuation/whitespace edits are exempt from content preservation.
- Run 37559405586 passed APK build, all 28 unit tests and lint. Android 16
  screenshots confirmed pinyin was active and offered the correct Chinese phrase;
  the smoke test incorrectly assumed it was the first candidate. It now selects
  the candidate by text, leaving native ranking and learning unchanged.
- Run 37560604221 passed APK build, all 29 unit tests and lint. Its installed-IME
  test successfully committed Chinese text, then exposed a real toolbar bug:
  native prediction candidates hid the idle toolbar's microphone. CandidateUi
  now has its own eligible-editor voice button alongside the expand control.

## Required next steps

1. Compile/test/lint the feature, inspect any failures, fix and push checkpoints.
2. Inspect first-use pinyin defaults and preserve existing native learning.
3. Verify recording, permission flow, cross-app insertion, restore and haptics on
   an Android device/emulator; confirm keyboard pane lifecycle compatibility.
4. Verify API calls on a device only
   once the user configures their own credentials; do not put keys in Git or logs.

## Resuming

Fetch all branches and inspect this file, latest commits and Actions logs. Build
the newest persisted `work/personal-ai-ime` commit. Never assume local files or
an in-progress conversation survived an interruption.
