#!/usr/bin/env bash
set -euo pipefail
repo_dir="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$repo_dir"
android_adb="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}/platform-tools/adb"
if [[ ! -x "$android_adb" ]]; then
  if command -v adb >/dev/null 2>&1; then android_adb="$(command -v adb)";
  else echo 'Android SDK의 adb를 찾지 못했습니다. ANDROID_SDK_ROOT를 설정하세요.' >&2; exit 2; fi
fi
preview_serial="${ANDROID_SERIAL:-}"
preview_screen=home
preview_id=''
preview_build=true
while [[ $# -gt 0 ]]; do
  case "$1" in
    --serial) preview_serial="$2"; shift 2;;
    --screen) preview_screen="$2"; shift 2;;
    --id) preview_id="$2"; shift 2;;
    --no-build) preview_build=false; shift;;
    *) echo 'Usage: scripts/design/preview.sh [--serial emulator-5554] [--screen home|login|gallery|...] [--id filter] [--no-build]' >&2; exit 2;;
  esac
done
case "$preview_screen" in home|items|calendar|settings|login|signup|reset|editor|detail|weather|family|preferences|gallery) ;; *) echo '알 수 없는 미리보기 화면입니다.' >&2; exit 2;; esac
if [[ -n "$preview_id" && ! "$preview_id" =~ ^[a-zA-Z0-9_-]+$ ]]; then
  echo '미리보기 ID에는 영문·숫자·밑줄·하이픈만 사용할 수 있습니다.' >&2; exit 2
fi
if [[ -z "$preview_serial" ]]; then
  preview_devices=()
  while IFS= read -r connected; do preview_devices+=("$connected"); done < <("$android_adb" devices | awk 'NR>1 && $2=="device" {print $1}')
  if [[ ${#preview_devices[@]} -ne 1 ]]; then
    echo 'Android Studio에서 에뮬레이터를 실행하세요. 여러 기기 연결 시 --serial을 지정하세요.' >&2; exit 2
  fi
  preview_serial="${preview_devices[0]}"
fi
if [[ "$preview_build" == true ]]; then
  if [[ "$(uname -s)" == Darwin ]]; then export JAVA_HOME="$(/usr/libexec/java_home -v 17)"; fi
  ./gradlew :app:assembleDebug
fi
"$android_adb" -s "$preview_serial" install -r app/build/outputs/apk/debug/app-debug.apk
preview_intent=(--es preview_screen "$preview_screen" --es preview_state normal --es preview_weather auto --es preview_condition clear)
if [[ -n "$preview_id" ]]; then preview_intent+=(--es preview_id "$preview_id"); fi
"$android_adb" -s "$preview_serial" shell am start -W --activity-clear-top --activity-single-top \
  -n com.kwmobile.hometiming/.ui.main.MainActivity "${preview_intent[@]}"
printf '때맞춤 디자인 미리보기: %s / %s\n' "$preview_serial" "$preview_screen"
