#import <Foundation/Foundation.h>
#define LAYA_NOW_PLAYING_TEST 1
#include "../iosApp/ProxyNative/LayaNowPlayingLaunch.m"

static NSUInteger calls;
static NSString *target;
static void *origin(void) { return (void *)0x1234; }
static void *absentOrigin(void) { return NULL; }
static void associate(void *value, CFStringRef bundleID) {
    NSCAssert(value == (void *)0x1234, @"Origin ABI");
    target = [(__bridge NSString *)bundleID copy]; calls++;
}
static void fail(void *value, CFStringRef bundleID) {
    [NSException raise:@"FixtureFailure" format:@"Unavailable on this OS"];
}
int main(void) { @autoreleasepool {
    LayaLocalOrigin = origin; LayaSetParent = associate;
    for (NSString *bundleID in @[@"com.music.laya", @"com.music.layatest", @"resigned.custom.app"]) {
        LayaInstalledBundleID = bundleID;
        LayaBindNowPlayingApplication();
        NSCAssert([target isEqual:bundleID], @"Use actual installed identity for every variant");
    }
    LayaSetParent = NULL; LayaBindNowPlayingApplication();
    NSCAssert(calls == 3, @"Missing optional API is harmless");
    LayaSetParent = associate; LayaLocalOrigin = absentOrigin; LayaBindNowPlayingApplication();
    NSCAssert(calls == 3, @"Missing origin is harmless");
    LayaLocalOrigin = origin; LayaInstalledBundleID = nil; LayaBindNowPlayingApplication();
    NSCAssert(calls == 3, @"Missing identity cannot bind another app");
    LayaInstalledBundleID = @"com.music.layatest"; LayaSetParent = fail;
    LayaBindNowPlayingApplication();
    NSCAssert(LayaAssociationFailed, @"Isolate native API exceptions");
    LayaSetParent = associate; LayaBindNowPlayingApplication();
    NSCAssert(calls == 3, @"Do not retry a failed SPI every metadata update");
    puts("PASS: Now Playing identities, missing API/origin and exception isolation (mock transport)");
} return 0; }
