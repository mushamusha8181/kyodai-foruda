#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"

if ! command -v gradle >/dev/null 2>&1; then
  echo "Gradle が見つかりません。Android Studio でこのプロジェクトを開いて Build APK(s) を実行してください。"
  echo "GitHubへpushした場合は GitHub Actions でも APK が自動生成されます。"
  exit 1
fi

gradle :app:assembleDebug
cp app/build/outputs/apk/debug/app-debug.apk ./GiantFolder-debug.apk
echo "完成: $(pwd)/GiantFolder-debug.apk"
