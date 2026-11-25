import SwiftUI
import LayaKit

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(QuickActionsAppDelegate.self) private var quickActions
    /// One engine for the app's lifetime; Kotlin drives it.
    static let audioEngine = AVAudioEngineImpl()

    /// Google Cast; created at launch because the SDK wants its context set up early.
    static let castNative = CastNativeImpl()

    private static let customization = CustomizationNative()

    init() {
        IosNative.shared.host = Self.customization
        IosKaraoke.shared.native = KaraokeNativeImpl()
        IosAutomix.shared.native = AutomixNativeImpl()
        _ = iOSApp.castNative // statics are lazy
        // Home-screen widgets: what they draw, and where their buttons' intents land. The engine goes along
        // so a widget tap that background-launched the app can play with no UI (iOS refuses to bring one up).
        IosWidgets.shared.host = WidgetHostImpl()
        IosWidgets.shared.engine = iOSApp.audioEngine
        LayaWidgetCommands.handler = { IosWidgets.shared.perform(command: $0) }
        // Music recognition's lock-screen / Dynamic Island status while it listens.
        // A launch never starts mid-listen, so any one still up was left by the last run.
        let liveActivity = RecognitionLiveActivityImpl()
        liveActivity.end()
        RecognitionNotifier.shared.liveActivity = liveActivity
    }

    var body: some Scene {
        WindowGroup {
            ComposeView().ignoresSafeArea()
                .onOpenURL { url in
                    if IosWidgets.shared.handleUrl(url: url.absoluteString) { return }
                    _ = IosPartyKt.handleInviteUrl(url: url.absoluteString)
                }
        }
    }
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        let host = StatusBarHost()
        host.embed(MainViewControllerKt.MainViewController(engine: iOSApp.audioEngine, cast: iOSApp.castNative, onStatusBarLight: { light in
            host.light = light.boolValue
        }))
        return host
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

/// Owns the status bar style for the Compose screen: light icons over dark content, dark over light.
final class StatusBarHost: UIViewController {
    private weak var content: UIViewController?
    var light = true { didSet { setNeedsStatusBarAppearanceUpdate() } }
    override var preferredStatusBarStyle: UIStatusBarStyle { light ? .lightContent : .darkContent }

    func embed(_ child: UIViewController) {
        // Shows through the (non-opaque) Compose canvas while it resizes on rotation; otherwise systemBackground, white in light mode.
        view.backgroundColor = .black
        addChild(child)
        child.view.frame = view.bounds
        child.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(child.view)
        child.didMove(toParent: self)
        content = child
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        guard let content else { return }
        // Keep the Compose viewport clear of Duo's system status column.
        // Use the current window's native safe rectangle on expanded phones;
        // keep the existing edge-to-edge canvas on iPad and compact phones.
        let safeFrame = view.window.map { view.convert($0.safeAreaLayoutGuide.layoutFrame, from: $0) }
            ?? view.safeAreaLayoutGuide.layoutFrame
        let frame = AdaptiveContentBounds.frame(in: view.bounds, safeFrame: safeFrame,
            isPhone: traitCollection.userInterfaceIdiom == .phone)
        if content.view.frame != frame { content.view.frame = frame }
    }

    override func viewSafeAreaInsetsDidChange() {
        super.viewSafeAreaInsetsDidChange()
        view.setNeedsLayout()
    }
}
