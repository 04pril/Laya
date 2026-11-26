import AVFoundation
import Foundation

/// Serves AVPlayer's byte-range requests from googlevideo in bounded chunks,
/// sending the minting client's headers on each.
final class ChunkedResourceLoader: NSObject, AVAssetResourceLoaderDelegate {
    static let scheme = "bitchord-stream"

    let queue = DispatchQueue(label: "laya.resource-loader")
    let proxyURL: URL
    private var onTerminalFailure: ((Int) -> Void)?
    private var terminalFailure: Int?
    /// The stream's size once a response has stated it (or the caller knew it).
    var streamLength: Int64? { knownLength }
    /// FLAC's STREAMINFO as read from the bytes actually served, set when the first chunk lands.
    var flacInfo: FlacInfo? { chunkFlac ?? entry?.flacInfo }
    private var chunkFlac: FlacInfo?

    struct FlacInfo { let sampleRate: Int; let channels: Int; let bitDepth: Int; let totalSamples: Int64 }

    /// "fLaC" + metadata header + STREAMINFO: rate (20 bits), channels-1 (3), bits-1 (5), samples (36).
    static func parseFlac(_ data: Data) -> FlacInfo? {
        let b = [UInt8](data.prefix(26))
        guard b.count >= 26, b[0] == 0x66, b[1] == 0x4C, b[2] == 0x61, b[3] == 0x43 else { return nil }
        let rate = Int(b[18]) << 12 | Int(b[19]) << 4 | Int(b[20]) >> 4
        let samples = Int64(b[21] & 0x0F) << 32 | Int64(b[22]) << 24 | Int64(b[23]) << 16 | Int64(b[24]) << 8 | Int64(b[25])
        return FlacInfo(sampleRate: rate, channels: Int((b[20] >> 1) & 7) + 1,
                        bitDepth: Int((b[20] & 1) << 4 | b[21] >> 4) + 1, totalSamples: samples)
    }

    private let source: URL
    private let headers: [String: String]
    let mimeType: String
    private(set) var knownLength: Int64?
    private let chunkBytes: Int64
    private var session: URLSession!
    private var tasks: [ObjectIdentifier: URLSessionDataTask] = [:]
    private var cancelled: Set<ObjectIdentifier> = []
    /// Set once the session is invalidated; creating a task after that throws.
    private var invalidated = false

    init(source: URL, headers: [String: String], mimeType: String, knownLength: Int64?, chunkBytes: Int64) {
        self.source = source
        self.headers = headers
        self.mimeType = mimeType
        self.knownLength = knownLength
        self.chunkBytes = chunkBytes
        var components = URLComponents(url: source, resolvingAgainstBaseURL: false)!
        components.scheme = ChunkedResourceLoader.scheme
        self.proxyURL = components.url!
        super.init()
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 20
        config.httpMaximumConnectionsPerHost = 2
        self.session = URLSession(configuration: config)
    }

    /// The file's last byte has been handed to AVPlayer: whatever it still waits for after this does not exist.
    private(set) var deliveredEnd = false

    /// googlevideo states the track's length in seconds as `dur=`. AVFoundation misreads some of
    /// those fragmented MP4s (itag 140 from the VISIONOS client: 215.7 s reported as 431.5 s, even
    /// from a local file, while AudioToolbox agrees with `dur`), so this is the length to show.
    /// Addon streams carry none.
    var durationHint: Double? {
        URLComponents(url: source, resolvingAgainstBaseURL: false)?
            .queryItems?.first { $0.name == "dur" }?.value.flatMap(Double.init)
    }

    /// The item reading through this was opened with AVURLAssetPreferPreciseDurationAndTimingKey. Main thread.
    var precise = false
    /// Seeked while not precise: the item's clock has been AVFoundation's estimate since. Main thread.
    var estimatedSeek = false
    /// A FLAC served from StreamCache whose file is now all on disk, so an asset over it can be precise.
    var flacOnDisk: Bool { streaming && flacInfo != nil && (entry?.state.complete ?? false) }

    /// A new loader over the same stream, for an item reopening it.
    func reopened() -> ChunkedResourceLoader {
        ChunkedResourceLoader(source: source, headers: headers, mimeType: mimeType, knownLength: knownLength, chunkBytes: chunkBytes)
    }

    func cancelAll() {
        queue.async {
            self.invalidated = true
            self.tasks.values.forEach { $0.cancel() }
            self.tasks.removeAll()
            self.session.invalidateAndCancel()
            self.releaseStreaming()
        }
    }

    func observeTerminalFailure(_ observer: @escaping (Int) -> Void) {
        queue.async {
            guard !self.invalidated else { return }
            self.onTerminalFailure = observer
            self.notifyRecordedTerminalFailure()
        }
    }

    /// A queued item may become current after the engine ignored its first failure notification.
    func replayTerminalFailure() {
        queue.async { self.notifyRecordedTerminalFailure() }
    }

    private func notifyRecordedTerminalFailure() {
        guard !invalidated, let status = terminalFailure else { return }
        onTerminalFailure?(status)
    }

    func resourceLoader(
        _ resourceLoader: AVAssetResourceLoader,
        shouldWaitForLoadingOfRequestedResource loadingRequest: AVAssetResourceLoadingRequest
    ) -> Bool {
        let key = ObjectIdentifier(loadingRequest)
        cancelled.remove(key)

        if let info = loadingRequest.contentInformationRequest {
            info.contentType = uti(for: mimeType)
            info.isByteRangeAccessSupported = true
            if let length = knownLength {
                info.contentLength = length
            }
        }

        guard let dataRequest = loadingRequest.dataRequest else {
            if knownLength != nil { loadingRequest.finishLoading() } else { fetch(loadingRequest, from: 0, end: 1) }
            return true
        }

        let start = dataRequest.requestedOffset
        var end: Int64
        if dataRequest.requestsAllDataToEndOfResource {
            end = (knownLength ?? Int64.max) - 1
        } else {
            end = start + Int64(dataRequest.requestedLength) - 1
        }
        if let length = knownLength { end = min(end, length - 1) }
        fetch(loadingRequest, from: start, end: end)
        return true
    }

    func resourceLoader(
        _ resourceLoader: AVAssetResourceLoader,
        didCancel loadingRequest: AVAssetResourceLoadingRequest
    ) {
        let key = ObjectIdentifier(loadingRequest)
        cancelled.insert(key)
        tasks.removeValue(forKey: key)?.cancel()
        if let s = serves.removeValue(forKey: key), let t = s.token { entry?.cancelWait(t) }
    }

    /// Fetches [from, end] one chunk at a time, handing each to AVPlayer as it lands.
    /// A chunk that fails for a passing reason (dropped connection, timeout, 5xx, 429) is asked
    /// again before the item is failed: one bad range must not end the whole track.
    private static let retryDelays: [Double] = [0.5, 1, 2, 4]
    private static let throttleDelays: [Double] = [1, 2, 4, 8, 16]
    /// Test hook (env only): this many requests past the first 2 MB fail, as a dropped connection or as LAYA_FAIL_STATUS.
    private static var injectedFailures = Int(ProcessInfo.processInfo.environment["LAYA_FAIL_RANGE"] ?? "") ?? 0
    private static let injectedStatus = Int(ProcessInfo.processInfo.environment["LAYA_FAIL_STATUS"] ?? "")

    /// What went wrong on the wire, for the failure report (AudioEngine.fail): written on `queue`, read on main.
    private let diagnosticsLock = NSLock()
    private var _retries = 0
    private var _lastFailure: String?
    var diagnostics: String {
        diagnosticsLock.lock(); defer { diagnosticsLock.unlock() }
        return "retries=\(_retries)" + (_lastFailure.map { " last=\($0)" } ?? "")
    }
    private func noteFailure(_ what: String) {
        diagnosticsLock.lock(); _lastFailure = what; diagnosticsLock.unlock()
    }
    private func noteRetry() {
        diagnosticsLock.lock(); _retries += 1; diagnosticsLock.unlock()
    }

    /// When the loader was made: the clock its first-bytes line reads from. Queue only, after init.
    private let createdAt = ProcessInfo.processInfo.systemUptime
    private var firstBytesLogged = false
    private var deliveredTotal = 0
    /// A range slower than this is worth a line: a slow start without any failure leaves no other trace.
    private static let slowRangeSec = 4.0

    /// Logs when the player first had real audio to work with (past AVFoundation's few-byte type probe), which is how
    /// long the start waited on the network, and any slow range after. Queue only.
    private func noteDelivered(start: Int64, bytes: Int, tookSec: Double) {
        let host = source.host ?? "?"
        deliveredTotal += bytes
        if !firstBytesLogged {
            guard deliveredTotal >= 64 * 1024 else { return }
            firstBytesLogged = true
            nativeLog(String(format: "Loader: first 64 KB to the player after %.0f ms (last range %ld KB at %lld in %.0f ms, %@%@)",
                             (ProcessInfo.processInfo.systemUptime - createdAt) * 1000, bytes / 1024, start, tookSec * 1000,
                             host, streaming ? ", stream cache" : ""))
        } else if tookSec >= Self.slowRangeSec {
            nativeLog(String(format: "Loader: slow range, %ld KB at %lld took %.1f s (%@)", bytes / 1024, start, tookSec, host), warn: true)
        }
    }

    private func retry(_ loadingRequest: AVAssetResourceLoadingRequest, from start: Int64, end: Int64, attempt: Int) -> Bool {
        guard attempt < Self.retryDelays.count else { return false }
        noteRetry()
        nativeLog("Loader: range at \(start) failed, retry \(attempt + 1)", warn: true)
        queue.asyncAfter(deadline: .now() + Self.retryDelays[attempt]) {
            self.fetch(loadingRequest, from: start, end: end, attempt: attempt + 1)
        }
        return true
    }

    private func fetch(_ loadingRequest: AVAssetResourceLoadingRequest, from start: Int64, end: Int64, attempt: Int = 0) {
        let key = ObjectIdentifier(loadingRequest)
        // Before the streaming branch: a request arriving after cancelAll must not acquire a StreamCache entry it never releases.
        if invalidated || cancelled.contains(key) || loadingRequest.isCancelled { return }
        if streaming { return stream(loadingRequest, from: start, end: end, attempt: attempt) }
        if start > end {
            loadingRequest.finishLoading()
            tasks.removeValue(forKey: key)
            return
        }
        let chunkEnd = min(end, start + chunkBytes - 1)

        var request = URLRequest(url: source)
        request.setValue("bytes=\(start)-\(chunkEnd)", forHTTPHeaderField: "Range")
        for (name, value) in headers { request.setValue(value, forHTTPHeaderField: name) }

        let began = ProcessInfo.processInfo.systemUptime
        let task = session.dataTask(with: request) { [weak self] data, response, error in
            guard let self else { return }
            var error = error
            var injected: Int?
            if start > 2_000_000, Self.injectedFailures > 0 {
                Self.injectedFailures -= 1
                injected = Self.injectedStatus
                if injected == nil { error = NSError(domain: NSURLErrorDomain, code: NSURLErrorNetworkConnectionLost) }
                print("Loader: injected failure at \(start) (attempt \(attempt))")
            }
            self.queue.async {
                if self.cancelled.contains(key) || loadingRequest.isCancelled { return }
                if let error {
                    let ns = error as NSError
                    if ns.code == NSURLErrorCancelled { return }
                    self.noteFailure("\(ns.domain) \(ns.code) at \(start)")
                    if self.retry(loadingRequest, from: start, end: end, attempt: attempt) { return }
                    loadingRequest.finishLoading(with: error)
                    self.tasks.removeValue(forKey: key)
                    return
                }
                let http = response as? HTTPURLResponse
                let status = injected ?? http?.statusCode ?? 0
                guard injected == nil, (200...299).contains(status), let data else {
                    self.noteFailure("HTTP \(status) at \(start)")
                    if status == 429 || status >= 500 || status == 0,
                       self.retry(loadingRequest, from: start, end: end, attempt: attempt) { return }
                    if [403, 404, 410].contains(status) { self.reportTerminalFailure(status) }
                    loadingRequest.finishLoading(with: NSError(
                        domain: "Laya", code: status,
                        userInfo: [NSLocalizedDescriptionKey: "HTTP \(status)"]
                    ))
                    self.tasks.removeValue(forKey: key)
                    return
                }
                if start == 0, self.chunkFlac == nil { self.chunkFlac = Self.parseFlac(data) }
                if self.knownLength == nil, let total = Self.totalLength(http) {
                    self.knownLength = total
                    loadingRequest.contentInformationRequest?.contentLength = total
                }
                if loadingRequest.contentInformationRequest != nil && loadingRequest.dataRequest == nil {
                    loadingRequest.finishLoading()
                    self.tasks.removeValue(forKey: key)
                    return
                }
                loadingRequest.dataRequest?.respond(with: data)
                self.noteDelivered(start: start, bytes: data.count, tookSec: ProcessInfo.processInfo.systemUptime - began)
                let next = start + Int64(data.count)
                let finalEnd = min(end, (self.knownLength ?? Int64.max) - 1)
                if let length = self.knownLength, next >= length { self.deliveredEnd = true }
                if data.isEmpty || next > finalEnd {
                    loadingRequest.finishLoading()
                    self.tasks.removeValue(forKey: key)
                } else {
                    self.fetch(loadingRequest, from: next, end: finalEnd)
                }
            }
        }
        tasks[key] = task
        task.resume()
    }

    // MARK: - Streaming (any host but googlevideo)

    /// PlayerClient.rangeBytesFor gives no bound for hosts other than googlevideo. Those are served from StreamCache's
    /// one sequential download per URL (shared with the Automix / karaoke / alignment readers): bytes already on disk
    /// are answered at once, later ones as they arrive. A seek far ahead waits for the download to reach it.
    /// ponytail: no second ranged request for a far seek (FLAC is 30-100 MB, seconds on Wi-Fi); add one if that wait proves too long.
    private var streaming: Bool { chunkBytes >= Int64.max / 2 }

    private final class Serve {
        let request: AVAssetResourceLoadingRequest
        var next: Int64
        var end: Int64 // inclusive; Int64.max - 1 = to the end of the file
        var token: Int?
        init(_ request: AVAssetResourceLoadingRequest, from: Int64, end: Int64) { self.request = request; next = from; self.end = end }
    }

    private var serves: [ObjectIdentifier: Serve] = [:]
    private var entry: StreamCache.Entry?
    private var released = false

    private func stream(_ lr: AVAssetResourceLoadingRequest, from start: Int64, end: Int64, attempt: Int) {
        if entry == nil { entry = StreamCache.shared.acquire(source, headers: headers) }
        let s = Serve(lr, from: start, end: end)
        serves[ObjectIdentifier(lr)] = s
        pump(s)
    }

    private func finish(_ s: Serve, error: Error? = nil) {
        let key = ObjectIdentifier(s.request)
        serves[key] = nil
        if let error { s.request.finishLoading(with: error) } else { s.request.finishLoading() }
    }

    private func pump(_ s: Serve) {
        let key = ObjectIdentifier(s.request)
        guard !invalidated, serves[key] === s, !cancelled.contains(key), !s.request.isCancelled, let entry else { serves[key] = nil; return }
        s.token = nil
        let st = entry.state
        if knownLength == nil { knownLength = st.length }
        if let length = knownLength {
            s.request.contentInformationRequest?.contentLength = length
            s.end = min(s.end, length - 1)
        }
        if s.request.contentInformationRequest != nil, knownLength == nil, !st.complete {
            if st.status != 0 { return fail(s, st.status) }
            return waitFor(s, entry, need: nil)
        }
        let refused = st.status != 0 && st.written <= s.next
        if refused { return fail(s, st.status) }
        guard let data = s.request.dataRequest else { return finish(s) } // only the type-and-length ask
        let from = s.next
        while s.next <= s.end {
            let chunk = entry.read(at: s.next, count: Int(min(256 * 1024, s.end - s.next + 1)))
            if chunk.isEmpty { break }
            data.respond(with: chunk)
            s.next += Int64(chunk.count)
            if let length = knownLength, s.next >= length { deliveredEnd = true }
        }
        // From disk: no range timing here (StreamCache logs its own connections), only the first-bytes line.
        if s.next > from { noteDelivered(start: from, bytes: Int(s.next - from), tookSec: 0) }
        let now = entry.state
        if s.next > s.end || (now.complete && s.next >= now.written) { return finish(s) }
        if now.status != 0 && now.written <= s.next { return fail(s, now.status) }
        waitFor(s, entry, need: s.next + 1)
    }

    private func waitFor(_ s: Serve, _ entry: StreamCache.Entry, need: Int64?) {
        s.token = entry.wait(for: need, on: queue) { [weak self, weak s] in if let self, let s { self.pump(s) } }
    }

    private func fail(_ s: Serve, _ status: Int) {
        let reason = status > 0 ? "HTTP \(status)" : "transport code \(status)"
        noteFailure("stream \(reason) at \(s.next)")
        reportTerminalFailure(status)
        finish(s, error: NSError(domain: "Laya", code: status, userInfo: [NSLocalizedDescriptionKey: reason]))
    }

    private func reportTerminalFailure(_ status: Int) {
        guard terminalFailure == nil else { return }
        terminalFailure = status
        notifyRecordedTerminalFailure()
    }

    private func releaseStreaming() {
        serves.values.forEach { s in s.token.map { entry?.cancelWait($0) } }
        serves.removeAll()
        if !released { released = true; entry?.release() }
    }

    /// "bytes 0-1023/4567890" → 4567890
    private static func totalLength(_ response: HTTPURLResponse?) -> Int64? {
        guard let range = response?.value(forHTTPHeaderField: "Content-Range"),
              let slash = range.lastIndex(of: "/") else { return nil }
        return Int64(range[range.index(after: slash)...])
    }

    private func uti(for mime: String) -> String {
        let base = mime.split(separator: ";").first.map(String.init)?.trimmingCharacters(in: .whitespaces) ?? ""
        switch base {
        case "audio/mp4", "audio/m4a", "audio/x-m4a": return AVFileType.m4a.rawValue
        case "audio/mpeg": return AVFileType.mp3.rawValue
        case "audio/aac": return "public.aac-audio"
        // Addon streams: FLAC plays natively from iOS 11 (ALAC arrives as audio/mp4 above).
        case "audio/flac", "audio/x-flac": return "org.xiph.flac"
        case "audio/wav", "audio/x-wav": return AVFileType.wav.rawValue
        case "audio/aiff", "audio/x-aiff": return AVFileType.aiff.rawValue
        default: return AVFileType.m4a.rawValue
        }
    }
}
