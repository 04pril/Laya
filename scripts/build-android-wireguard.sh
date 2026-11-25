#!/usr/bin/env bash
# Build the pinned GPL userspace engine, then package the matching JNI ABI and notices.
set -euo pipefail
laya_root="$(cd "$(dirname "$0")/.." && pwd)"
laya_source="$laya_root/build/android-wireguard-source"
laya_pin=d03b7439f517eeed13d12edc1f922ee0e640cd13
if [[ ! -d "$laya_source/.git" ]]; then
  git clone --filter=blob:none --no-checkout https://github.com/seo-rii/morphe-patches.git "$laya_source"
fi
git -C "$laya_source" sparse-checkout set --no-cone native/wireguard scripts/build-wireguard-android.sh LICENSE NOTICE
git -C "$laya_source" checkout "$laya_pin"
test "$(git -C "$laya_source" rev-parse HEAD)" = "$laya_pin"
# The upstream script requires Go 1.26.10 and NDK r28c; do not substitute a newer ABI.
laya_go_root="$(GOTOOLCHAIN=go1.26.10 go env GOROOT)"
WIREGUARD_GO_BIN="$laya_go_root/bin/go" bash "$laya_source/scripts/build-wireguard-android.sh" arm64-v8a x86_64
for laya_abi in arm64-v8a x86_64; do
  mkdir -p "$laya_root/app/src/main/jniLibs/$laya_abi"
  cp "$laya_source/native/wireguard/build/android/$laya_abi/libmorphewireguard.so" "$laya_root/app/src/main/jniLibs/$laya_abi/"
done
mkdir -p "$laya_root/app/src/main/assets/wireguard/licenses"
cp "$laya_source/LICENSE" "$laya_source/NOTICE" "$laya_root/app/src/main/assets/wireguard/licenses/"
cp -R "$laya_source/native/wireguard/licenses/." "$laya_root/app/src/main/assets/wireguard/licenses/"
