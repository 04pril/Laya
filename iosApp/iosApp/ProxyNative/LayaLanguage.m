#import <Foundation/Foundation.h>
#import <objc/runtime.h>

// Compose resources and Locale.current both read NSLocale.preferredLanguages.
// Only this process is affected; no system language setting is changed.
@implementation NSLocale (LayaAppLanguage)
+ (NSArray<NSString *> *)laya_preferredLanguages {
    NSString *language = [NSUserDefaults.standardUserDefaults stringForKey:@"app_language_override"];
    if ([language isEqualToString:@"ko"] || [language isEqualToString:@"en"]) return @[language];
    return [self laya_preferredLanguages];
}
+ (void)load {
    static dispatch_once_t once;
    dispatch_once(&once, ^{
        method_exchangeImplementations(class_getClassMethod(self, @selector(preferredLanguages)),
                                       class_getClassMethod(self, @selector(laya_preferredLanguages)));
    });
}
@end
