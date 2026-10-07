#!/usr/bin/env bash
set -euo pipefail

# Collect while the emulator is alive, including when instrumentation fails.
collect_reports() {
  mkdir -p app/build/reports/ime-smoke
  adb logcat -d > app/build/reports/ime-smoke/logcat.txt || true
  adb pull /data/local/tmp/personal-ime-smoke app/build/reports/ime-smoke/screenshots || true
}
trap collect_reports EXIT
adb logcat -c
./gradlew :app:connectedDebugAndroidTest -PbuildABI=x86_64 -Pandroid.testInstrumentationRunnerArguments.class=org.fcitx.fcitx5.android.PersonalImeSmokeTest --stacktrace
