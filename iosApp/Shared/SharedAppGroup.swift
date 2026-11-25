import Foundation
import Security
import Darwin

/// Resolve the group actually granted to this process after sideload re-signing.
/// A defaults suite alone is not proof that the app and extension share a container.
enum SharedAppGroup {
    static var declaredIdentifier: String { LayaAppIdentity.appGroup }
    static let signedIdentifiers: [String]? = readSignedIdentifiers()
    static var packagedWidgetIdentifiers: [String]? {
        var host = Bundle.main
        if host.bundleURL.pathExtension == "appex" {
            host = Bundle(url: host.bundleURL.deletingLastPathComponent().deletingLastPathComponent()) ?? host
        }
        guard let widget = Bundle(url: host.bundleURL.appending(path: "PlugIns/LayaWidgets.appex")) else { return nil }
        return SignedApplicationGroups.read(widget)
    }
    static let selection: (identifier: String, container: URL)? = {
        var host = Bundle.main
        if host.bundleURL.pathExtension == "appex" {
            host = Bundle(url: host.bundleURL.deletingLastPathComponent().deletingLastPathComponent()) ?? host
        }
        let widget = Bundle(url: host.bundleURL.appendingPathComponent("PlugIns/LayaWidgets.appex"))
        func groups(_ bundle: Bundle) -> [String] {
            if let signed = SignedApplicationGroups.read(bundle) { return signed }
            if bundle.bundleURL == Bundle.main.bundleURL, let own = signedIdentifiers { return own }
            return (bundle.object(forInfoDictionaryKey: "ALTAppGroups") as? [String]) ?? [declaredIdentifier]
        }
        let appGroups = groups(host)
        let shared = Set(widget.map(groups) ?? []).intersection(appGroups)
        let preferred = ["group." + (host.bundleIdentifier ?? "com.music.laya"), declaredIdentifier]
        let candidates = preferred.filter { shared.contains($0) } + shared.sorted()
        for identifier in candidates {
            if let container = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: identifier) {
                return (identifier, container)
            }
        }
        return nil
    }()

    static var defaults: UserDefaults? {
        guard let selection else { return nil }
        return UserDefaults(suiteName: selection.identifier)
    }
    static var containerURL: URL? { selection?.container }
    static var diagnostics: String {
        "bundle=\(Bundle.main.bundleIdentifier ?? "unknown")\nsignedGroups=\(signedIdentifiers.map { $0.joined(separator: ", ") } ?? "unavailable")\npackagedWidgetGroups=\(packagedWidgetIdentifiers.map { $0.joined(separator: ", ") } ?? "unavailable")\nselectedGroup=\(selection?.identifier ?? "none")\nsharedContainer=\(containerURL == nil ? "unavailable" : "available")"
    }

    static func probeContainer() throws {
        guard let container = containerURL else {
            throw NSError(domain: "LayaWidget", code: 1,
                          userInfo: [NSLocalizedDescriptionKey: "App Group shared container is unavailable"])
        }
        let file = container.appending(path: "widget-probe-\(UUID().uuidString).tmp")
        defer { try? FileManager.default.removeItem(at: file) }
        let data = Data(UUID().uuidString.utf8)
        try data.write(to: file, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
        guard try Data(contentsOf: file) == data else {
            throw NSError(domain: "LayaWidget", code: 2,
                          userInfo: [NSLocalizedDescriptionKey: "Shared container read-back failed"])
        }
    }

    private static func readSignedIdentifiers() -> [String]? {
        if let groups = SignedApplicationGroups.read(Bundle.main) { return groups.filter { !$0.isEmpty } }
        // Like the existing sideload MediaRemote patch, resolve Security task introspection
        // dynamically: these symbols are not declared in the public iOS SDK.
        typealias Create = @convention(c) (CFAllocator?) -> Unmanaged<CFTypeRef>?
        typealias Copy = @convention(c) (CFTypeRef, CFString, UnsafeMutablePointer<Unmanaged<CFError>?>?) -> Unmanaged<CFTypeRef>?
        guard let handle = dlopen(nil, RTLD_LAZY) else { return nil }
        defer { dlclose(handle) }
        guard let createSymbol = dlsym(handle, "SecTaskCreateFromSelf"),
              let copySymbol = dlsym(handle, "SecTaskCopyValueForEntitlement"),
              let task = unsafeBitCast(createSymbol, to: Create.self)(nil)?.takeRetainedValue() else { return nil }
        let value = unsafeBitCast(copySymbol, to: Copy.self)(task, "com.apple.security.application-groups" as CFString, nil)?.takeRetainedValue()
        guard let value else { return [] }
        return (value as? [String])?.filter { !$0.isEmpty }
    }
}
