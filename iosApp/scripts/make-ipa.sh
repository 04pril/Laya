#!/usr/bin/env bash
# Build and package a local iPhone IPA; a sideloader supplies the final device signature.
set -euo pipefail
task_root="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$task_root"
if [[ -z "${JAVA_HOME:-}" ]]; then
  if [[ -d /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ]]; then
    export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
  else
    export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
  fi
fi
export ANDROID_HOME=/nonexistent ANDROID_SDK_ROOT=/nonexistent
(cd iosApp && xcodegen generate)
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Release \
  -sdk iphoneos -destination 'generic/platform=iOS' -derivedDataPath build/ios-customization \
  -packageAuthorizationProvider netrc CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO build

task_stage="$(mktemp -d "$task_root/build/ipa.XXXXXX")"
trap 'rm -rf "$task_stage"' EXIT
mkdir -p "$task_stage/Payload"
ditto build/ios-customization/Build/Products/Release-iphoneos/Laya.app "$task_stage/Payload/Laya.app"
task_app="$task_stage/Payload/Laya.app"
for framework in "$task_app"/Frameworks/*.framework; do
  [[ ! -d "$framework" ]] || codesign --force --sign - --timestamp=none "$framework"
done
codesign --force --sign - --timestamp=none --generate-entitlement-der \
  --entitlements iosApp/LayaWidgets/LayaWidgets.entitlements "$task_app/PlugIns/LayaWidgets.appex"
codesign --force --sign - --timestamp=none --generate-entitlement-der \
  --entitlements iosApp/iosApp/iosApp.entitlements "$task_app"
codesign --verify --strict --verbose=2 "$task_app"
codesign --verify --strict --verbose=2 "$task_app/PlugIns/LayaWidgets.appex"
task_version="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleShortVersionString' "$task_app/Info.plist")"
task_build="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleVersion' "$task_app/Info.plist")"
task_output="$task_root/build/Laya-${task_version}-custom${task_build}.ipa"
rm -f "$task_output"
(cd "$task_stage" && /usr/bin/zip -qry "$task_output" Payload)
/usr/bin/unzip -tq "$task_output"
echo "$task_output"
