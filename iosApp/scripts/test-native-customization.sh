#!/usr/bin/env bash
set -euo pipefail
task_root="$(cd "$(dirname "$0")/../.." && pwd)"
task_tmp="$(mktemp -d "${TMPDIR:-/tmp}/laya-native-test.XXXXXX")"
trap 'rm -rf "$task_tmp"' EXIT
cat > "$task_tmp/language.m" <<'OBJC'
#import <Foundation/Foundation.h>
int main(void) { @autoreleasepool {
    NSUserDefaults *d = NSUserDefaults.standardUserDefaults;
    NSDictionary *before = [d volatileDomainForName:NSArgumentDomain];
    for (NSString *language in @[@"ko", @"en", @"ko"]) {
        [d setVolatileDomain:@{@"app_language_override": language} forName:NSArgumentDomain];
        NSCAssert([NSLocale.preferredLanguages.firstObject isEqual:language], @"Locale must change immediately");
    }
    [d setVolatileDomain:before forName:NSArgumentDomain];
    puts("PASS: live Korean/English switching");
} return 0; }
OBJC
xcrun clang -fobjc-arc -framework Foundation "$task_root/iosApp/iosApp/ProxyNative/LayaLanguage.m" "$task_tmp/language.m" -o "$task_tmp/language"
"$task_tmp/language"

cp "$task_root/iosApp/Shared/SignedApplicationGroups.swift" "$task_tmp/SignedApplicationGroups.swift"
cat > "$task_tmp/main.swift" <<'SWIFT'
import Foundation
let expected = ["group.com.music.laya", "group.test.renamed"]
guard SignedApplicationGroups.read(Bundle.main) == expected else {
    fatalError("Signed App Group entitlements did not round-trip")
}
print("PASS: signed App Group parsing with renamed groups")
SWIFT
cat > "$task_tmp/groups.plist" <<'PLIST'
<?xml version="1.0" encoding="UTF-8"?><plist version="1.0"><dict><key>com.apple.security.application-groups</key><array><string>group.com.music.laya</string><string>group.test.renamed</string></array></dict></plist>
PLIST
xcrun swiftc "$task_tmp/SignedApplicationGroups.swift" "$task_tmp/main.swift" -o "$task_tmp/groups"
codesign --force --sign - --entitlements "$task_tmp/groups.plist" "$task_tmp/groups"
"$task_tmp/groups"

xcrun clang -fobjc-arc -framework Foundation "$task_root/iosApp/tests/NowPlayingLaunchTests.m" -o "$task_tmp/nowplaying"
"$task_tmp/nowplaying"


xcrun swiftc "$task_root/iosApp/LayaWidgets/PosterSurfaceRenderer.swift" "$task_root/iosApp/tests/PosterSurfaceRendererTests.swift" -o "$task_tmp/poster-surface"
"$task_tmp/poster-surface"
