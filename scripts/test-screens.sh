#!/usr/bin/env bash
set -euo pipefail

./gradlew --no-daemon :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk

package=com.eithonx.rejoin
output=app/build/outputs/screenshots
mkdir -p "$output"
adb shell am instrument -w -r "$package.test/androidx.test.runner.AndroidJUnitRunner" |
  tee "$output/results.txt"
grep -Eq '^OK \([1-9][0-9]* tests?\)' "$output/results.txt"

for name in home-dark settings-large-text setup-first-step; do
  adb exec-out run-as "$package" cat "files/$name.png" > "$output/$name.png"
  test -s "$output/$name.png"
done
