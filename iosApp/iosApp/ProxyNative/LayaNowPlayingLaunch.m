#import "LayaNowPlayingLaunch.h"
#import <dlfcn.h>

// Same launch-association repair as BitChord, invoked directly by our own audio
// engine instead of swizzling MediaPlayer. The optional SPI is resolved at runtime.
// ABI: WebKit Source/WebCore/platform/cocoa/MediaRemoteSoftLink.h
// https://github.com/WebKit/WebKit/blob/main/Source/WebCore/platform/cocoa/MediaRemoteSoftLink.h
static void *(*LayaLocalOrigin)(void);
static void (*LayaSetParent)(void *, CFStringRef);
static NSString *LayaInstalledBundleID;
static BOOL LayaAssociationFailed;

void LayaBindNowPlayingApplication(void) {
#ifndef LAYA_NOW_PLAYING_TEST
    static dispatch_once_t once;
    dispatch_once(&once, ^{
        NSDictionary *info = [NSDictionary dictionaryWithContentsOfFile:
            [NSBundle.mainBundle.bundlePath stringByAppendingPathComponent:@"Info.plist"]];
        LayaInstalledBundleID = [info[@"CFBundleIdentifier"] copy];
        // Retain the handle for the process lifetime. Missing SPI is a no-op.
        void *framework = dlopen("/System/Library/PrivateFrameworks/MediaRemote.framework/MediaRemote", RTLD_LAZY | RTLD_LOCAL);
        if (framework) {
            LayaLocalOrigin = dlsym(framework, "MRMediaRemoteGetLocalOrigin");
            LayaSetParent = dlsym(framework, "MRMediaRemoteSetParentApplication");
        }
    });
#endif
    if (!LayaInstalledBundleID.length || !LayaLocalOrigin || !LayaSetParent || LayaAssociationFailed) return;
    @try {
        void *origin = LayaLocalOrigin();
        if (origin) LayaSetParent(origin, (__bridge CFStringRef)LayaInstalledBundleID);
    } @catch (NSException *exception) {
        // An OS-specific failure must never interrupt audio or metadata updates.
        LayaAssociationFailed = YES;
    }
}
