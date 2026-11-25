import Foundation

/// The same executable can be re-signed as an isolated test app without sharing widget links or storage.
enum LayaAppIdentity {
    static var hostBundle: Bundle {
        let bundle = Bundle.main
        if bundle.bundleURL.pathExtension == "appex" {
            return Bundle(url: bundle.bundleURL.deletingLastPathComponent().deletingLastPathComponent()) ?? bundle
        }
        return bundle
    }
    static var scheme: String { hostBundle.object(forInfoDictionaryKey: "LayaURLScheme") as? String ?? "laya" }
    static var appGroup: String { hostBundle.object(forInfoDictionaryKey: "LayaAppGroup") as? String ?? "group.com.music.laya" }
    static func widgetURL(_ command: String) -> URL { URL(string: "\(scheme)://widget/\(command)")! }
}
