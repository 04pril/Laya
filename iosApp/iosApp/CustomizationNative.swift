import AVFoundation
import LayaKit
import UIKit
import WebKit

/// Proxy UI stays native; the same transport core configures Ktor's NSURLSessions and downloads.
final class CustomizationNative: NSObject, IosCustomizationNative {
    private let launchProxySettings = UserDefaults.standard.dictionary(forKey: "LayaWireGuard") ?? [:]
    private var symbolPNGs: [String: String] = [:]

    func outputSymbol() -> String {
        let name = AVAudioSession.sharedInstance().currentRoute.outputs.first?.portName.lowercased() ?? ""
        var symbol = "headphones"
        if name.contains("airpods") || name.contains("에어팟") {
            if name.contains("max") || name.contains("맥스") { symbol = "airpods.max" }
            else if name.contains("pro") || name.contains("프로") { symbol = "airpods.pro" }
            else if name.contains("4") { symbol = "airpods.gen4" }
            else if name.contains("3") { symbol = "airpods.gen3" }
            else { symbol = "airpods" }
        }
        return UIImage(systemName: symbol) == nil ? "headphones" : symbol
    }
    func outputSymbolPng() -> String? {
        let symbol = outputSymbol()
        if let cached = symbolPNGs[symbol] { return cached }
        let configuration = UIImage.SymbolConfiguration(pointSize: 26, weight: .medium)
        guard let glyph = UIImage(systemName: symbol, withConfiguration: configuration)?
            .withTintColor(.white, renderingMode: .alwaysOriginal) else { return nil }
        let format = UIGraphicsImageRendererFormat()
        format.scale = 3
        format.opaque = false
        let image = UIGraphicsImageRenderer(size: glyph.size, format: format).image { _ in
            glyph.draw(at: .zero)
        }
        guard let encoded = image.pngData()?.base64EncodedString() else { return nil }
        symbolPNGs[symbol] = encoded
        return encoded
    }

    func proxyConfiguredReady() -> Bool { YTMUWGSettingEnabled() && proxyReady() }

    func proxyReady() -> Bool {
        let current = UserDefaults.standard.dictionary(forKey: "LayaWireGuard") ?? [:]
        guard NSDictionary(dictionary: current).isEqual(to: launchProxySettings) else { return false }
        if !YTMUWGSettingEnabled() { return true }
        let state = YTMUWGCurrentState()
        return state.rawValue == 4 || state.rawValue == 6
    }
    func showProxy() {
        DispatchQueue.main.async {
            let controller = YTMUWGSettingsController()
            controller.navigationItem.rightBarButtonItem = UIBarButtonItem(
                systemItem: .done, primaryAction: UIAction { [weak controller] _ in controller?.dismiss(animated: true) })
            var presenter = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
                .flatMap(\.windows).first(where: \.isKeyWindow)?.rootViewController
            while let next = presenter?.presentedViewController { presenter = next }
            presenter?.present(UINavigationController(rootViewController: controller), animated: true)
        }
    }

    func configureBrowser(store: WKWebsiteDataStore) -> Bool { YTMUWGConfigureBrowserDataStore(store) }

    func applyLanguage(language: String) {
        guard ["ko", "en"].contains(language) else { return }
        UserDefaults.standard.set(language, forKey: "app_language_override")
        UserDefaults.standard.set([language], forKey: "AppleLanguages")
    }

    func setCarScreenOn(enabled: Bool) {
        CarScreenAwake.shared.enabled = enabled
        CarScreenAwake.shared.update()
    }
}

/// Car audio routes and a connected car screen cover CarPlay without a CarPlay browsing scene.
final class CarScreenAwake {
    static let shared = CarScreenAwake()
    var enabled = false
    private var observers: [NSObjectProtocol] = []
    private init() {
        for name in [AVAudioSession.routeChangeNotification, UIScreen.didConnectNotification,
                     UIScreen.didDisconnectNotification, UIApplication.didBecomeActiveNotification,
                     UIApplication.willResignActiveNotification, UIApplication.didEnterBackgroundNotification] {
            observers.append(NotificationCenter.default.addObserver(forName: name, object: nil, queue: .main) { [weak self] _ in self?.update() })
        }
    }
    func update() {
        let car = AVAudioSession.sharedInstance().currentRoute.outputs.contains { $0.portType == .carAudio }
            || UIScreen.screens.contains { $0.traitCollection.userInterfaceIdiom == .carPlay }
        IosScreenAwake.shared.car(keep: enabled && car && UIApplication.shared.applicationState == .active)
    }
}
