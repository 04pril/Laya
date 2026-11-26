import Foundation
import LayaKit

/// The Swift engine's side of the app log: the lines "Copy log" exports (TrackLog), so a user's report shows what
/// the player, the loader and the stream cache did, not only what Kotlin saw. Lines carry no track id and so appear
/// in the log of whichever track was playing at the time.
///
/// Rules: never a URL (an addon link carries its owner's token; a host is fine), and never per tick or per chunk:
/// TrackLog holds a bounded window, and noise pushes the lines that matter out of it.
func nativeLog(_ message: String, warn: Bool = false) {
    let log = TrackLog.shared
    // Once the app has started, TrackLog echoes to the console itself.
    if log.echo == nil { print(message) }
    if warn {
        log.w(tag: "Laya", message: message, about: nil)
    } else {
        log.d(tag: "Laya", message: message, about: nil)
    }
}
