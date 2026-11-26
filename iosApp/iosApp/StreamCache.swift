import CryptoKit
import Foundation

/// Android's AudioCache for hosts that are not googlevideo: one file per URL in Caches/, filled by ONE sequential
/// GET (`Range: bytes=0-`, resumed from the last byte on a drop, 429 backed off), shared by every reader of that URL:
/// the AVPlayer item's ChunkedResourceLoader, the PcmReader behind Automix / karaoke / version alignment, and seeks.
/// Addon CDNs (Cloudflare on tracks.monochrome.st) answer HTTP 429 to anything more.
final class StreamCache {
    static let shared = StreamCache()
    /// AppSettings.DEFAULT_CACHE_LIMIT_BYTES: Android's default audio cache size.
    static let capBytes: Int64 = 512 << 20

    /// All download state lives on this queue; readers on other threads go through the entry's lock.
    fileprivate let queue = DispatchQueue(label: "laya.stream-cache")
    private let dir: URL
    private var entries: [String: Entry] = [:]
    private let lock = NSLock()

    private init() {
        dir = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0].appendingPathComponent("laya-stream")
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
    }

    /// The entry for [url], with a reference held until `release()`. The download runs while any reference is held.
    func acquire(_ url: URL, headers: [String: String]) -> Entry {
        lock.lock()
        let key = url.absoluteString
        let entry: Entry
        if let have = entries[key] { entry = have } else {
            evict()
            let name = SHA256.hash(data: Data(key.utf8)).prefix(12).map { String(format: "%02x", $0) }.joined()
            entry = Entry(cache: self, url: url, headers: headers, path: dir.appendingPathComponent(name + ".bin"), id: String(name.prefix(6)))
            entries[key] = entry
        }
        lock.unlock()
        entry.retain()
        return entry
    }

    /// Whether [url] is a FLAC this run has already fetched to its end.
    func isCompleteFlac(_ url: URL) -> Bool {
        lock.lock()
        let entry = entries[url.absoluteString]
        lock.unlock()
        guard let entry, entry.state.complete else { return false }
        return entry.flacInfo != nil
    }

    /// Oldest files out until the folder fits the cap; files a reader holds are kept. Called with `lock` held.
    private func evict() {
        let keys: [URLResourceKey] = [.fileSizeKey, .contentModificationDateKey]
        // The cached files only: a length record (.len) is tiny and goes with its file below.
        let files = ((try? FileManager.default.contentsOfDirectory(at: dir, includingPropertiesForKeys: keys)) ?? [])
            .filter { $0.pathExtension == "bin" }
        var info = files.map { f -> (url: URL, size: Int64, date: Date) in
            let v = try? f.resourceValues(forKeys: Set(keys))
            return (f, Int64(v?.fileSize ?? 0), v?.contentModificationDate ?? .distantPast)
        }.sorted { $0.date < $1.date }
        var total = info.reduce(0) { $0 + $1.size }
        let inUse = Set(entries.values.filter { $0.inUse }.map { $0.path })
        while total > Self.capBytes, !info.isEmpty {
            let old = info.removeFirst()
            if inUse.contains(old.url) { continue }
            try? FileManager.default.removeItem(at: old.url)
            try? FileManager.default.removeItem(at: Entry.lengthRecord(for: old.url))
            entries = entries.filter { $0.value.path != old.url }
            total -= old.size
        }
    }

    final class Entry: NSObject, URLSessionDataDelegate {
        fileprivate let path: URL
        private unowned let cache: StreamCache
        private let url: URL
        private let headers: [String: String]
        private let id: String
        private let lock = NSLock()
        // Guarded by `lock`: read by any thread, written on the cache queue.
        private var _written: Int64 = 0
        private var _length: Int64?
        private var _status = 0 // a refusal the retries gave up on
        private var _complete = false
        private var refs = 0
        private var waiters: [Int: (need: Int64?, queue: DispatchQueue, block: () -> Void)] = [:]
        private var nextToken = 0
        /// Opened on demand and closed once nobody holds the entry, so idle entries don't pin descriptors. Guarded by `lock`.
        private var rfd: Int32 = -1
        // Cache queue only.
        private var session: URLSession?
        private var task: URLSessionDataTask?
        private var lengthCheck: UUID?
        /// Opened when a download starts, closed when the entry goes idle.
        private var wfd: Int32 = -1
        private var skip: Int64 = 0
        private var requestedOffset: Int64 = 0
        private var attempt = 0
        private var progressed = false
        private var connections = 0
        private static let retryDelays: [Double] = [0.5, 1, 2, 4]
        /// Under this much on disk, a failure is still the track failing to start (see retry).
        private static let startBytes: Int64 = 256 * 1024
        private static let startRetries = 2
        private static let throttleDelays: [Double] = [1, 2, 4, 8, 16]

        fileprivate init(cache: StreamCache, url: URL, headers: [String: String], path: URL, id: String) {
            self.cache = cache; self.url = url; self.headers = headers; self.path = path; self.id = id
            super.init()
            if !FileManager.default.fileExists(atPath: path.path) {
                FileManager.default.createFile(atPath: path.path, contents: nil)
            } // else keeps what a previous run left
            var st = stat()
            if stat(path.path, &st) == 0 { _written = Int64(st.st_size) }
            // A file an earlier run fetched to its end starts complete: asking the host for what follows it gets a 416,
            // and Akamai (Qobuz) sends that without the "bytes */total" that would prove the file whole, so a 250 MB
            // track already on disk failed in a tenth of a second on every play.
            let record = Self.lengthRecord(for: path)
            if let text = try? String(contentsOf: record, encoding: .utf8), let length = Int64(text), length > 0 {
                if length == _written { _length = length; _complete = true } else { try? FileManager.default.removeItem(at: record) }
            }
        }

        /// Beside the cached file: its full length, written once the download reached the end.
        fileprivate static func lengthRecord(for path: URL) -> URL { path.deletingPathExtension().appendingPathExtension("len") }

        deinit { if wfd >= 0 { close(wfd) }; if rfd >= 0 { close(rfd) } }

        var inUse: Bool { lock.lock(); defer { lock.unlock() }; return refs > 0 }
        var state: (written: Int64, length: Int64?, complete: Bool, status: Int) {
            lock.lock(); defer { lock.unlock() }
            return (_written, _length, _complete, _status)
        }
        /// FLAC's STREAMINFO, once the first 26 bytes are on disk.
        var flacInfo: ChunkedResourceLoader.FlacInfo? { ChunkedResourceLoader.parseFlac(read(at: 0, count: 26)) }

        /// Up to [count] bytes at [offset] that are on disk now (fewer, or none, when the download has not got there).
        func read(at offset: Int64, count: Int) -> Data {
            lock.lock(); defer { lock.unlock() }
            let n = Int(max(0, min(Int64(count), _written - offset)))
            guard n > 0 else { return Data() }
            if rfd < 0 { rfd = open(path.path, O_RDONLY) }
            guard rfd >= 0 else { return Data() }
            var data = Data(count: n)
            let fd = rfd
            let got = data.withUnsafeMutableBytes { pread(fd, $0.baseAddress, n, off_t(offset)) }
            // A peek at an idle entry (isCompleteFlac) leaves nothing open behind it.
            if refs <= 0 { close(rfd); rfd = -1 }
            return got > 0 ? data.prefix(got) : Data()
        }

        /// Waits for the byte count, or for the content length when `need` is nil. A terminal result wakes either.
        func wait(for need: Int64?, on queue: DispatchQueue, _ block: @escaping () -> Void) -> Int {
            lock.lock()
            nextToken += 1
            let token = nextToken
            if canWake(need) {
                lock.unlock()
                queue.async(execute: block)
            } else {
                waiters[token] = (need, queue, block)
                lock.unlock()
            }
            return token
        }

        func cancelWait(_ token: Int) { lock.lock(); waiters[token] = nil; lock.unlock() }

        /// Called with the entry lock held.
        private func canWake(_ need: Int64?) -> Bool {
            _complete || _status != 0 || (need.map { _written >= $0 } ?? (_length != nil))
        }

        fileprivate func retain() {
            lock.lock()
            refs += 1
            // A new reader gets a fresh try: an earlier give-up (a dropped network, an expired link) must not fail this
            // URL for the rest of the run. Cleared here, under the lock, before the caller's first look at `state`.
            let retry = _status != 0 && !_complete
            if retry { _status = 0 }
            lock.unlock()
            try? FileManager.default.setAttributes([.modificationDate: Date()], ofItemAtPath: path.path)
            cache.queue.async {
                if retry { self.attempt = 0 }
                self.startIfNeeded()
            }
        }

        func release() {
            lock.lock(); refs -= 1; let idle = refs <= 0; lock.unlock()
            // Nobody reads: stop downloading and let go of the file (it stays; the next reader resumes from its end).
            guard idle else { return }
            cache.queue.async {
                self.lock.lock()
                let stillIdle = self.refs <= 0
                if stillIdle, self.rfd >= 0 { close(self.rfd); self.rfd = -1 }
                self.lock.unlock()
                guard stillIdle else { return } // re-acquired meanwhile
                self.lengthCheck = nil
                self.task?.cancel(); self.task = nil
                if self.wfd >= 0 { close(self.wfd); self.wfd = -1 }
            }
        }

        private func wake() {
            lock.lock()
            let due = waiters.filter { canWake($0.value.need) }
            due.keys.forEach { waiters[$0] = nil }
            lock.unlock()
            due.values.forEach { $0.queue.async(execute: $0.block) }
        }

        // MARK: download (cache queue)

        private func startIfNeeded() {
            let s = state
            lock.lock(); let held = refs > 0; lock.unlock()
            guard held, task == nil, !s.complete, s.status == 0 else { return }
            if let length = s.length, s.written >= length { return finish() }
            if wfd < 0 { wfd = open(path.path, O_WRONLY | O_APPEND | O_CREAT, 0o644) }
            guard wfd >= 0 else { return fail(NSURLErrorCannotWriteToFile) }
            if session == nil {
                let config = URLSessionConfiguration.default
                config.timeoutIntervalForRequest = 20
                config.httpMaximumConnectionsPerHost = 1
                let ops = OperationQueue()
                ops.underlyingQueue = cache.queue
                ops.maxConcurrentOperationCount = 1
                session = URLSession(configuration: config, delegate: self, delegateQueue: ops)
            }
            var request = URLRequest(url: url)
            // A one-byte cache repeatedly failed to resume in a Qobuz report. Fetch from zero and skip the cached byte.
            requestedOffset = s.written == 1 ? 0 : s.written
            for (name, value) in headers { request.setValue(value, forHTTPHeaderField: name) }
            request.setValue("bytes=\(requestedOffset)-", forHTTPHeaderField: "Range")
            connections += 1
            progressed = false
            skip = 0
            nativeLog("StreamCache[\(id)]: connection #\(connections) \(url.host ?? "?") bytes=\(requestedOffset)- (attempt \(attempt))")
            task = session?.dataTask(with: request)
            task?.resume()
        }

        private func finish() {
            lock.lock(); _complete = true; let length = _length ?? _written; lock.unlock()
            try? Data(String(length).utf8).write(to: Self.lengthRecord(for: path), options: .atomic)
            nativeLog("StreamCache[\(id)]: complete, \(state.written) bytes in \(connections) connection(s)")
            task = nil
            session?.finishTasksAndInvalidate(); session = nil
            wake()
        }

        private func fail(_ status: Int) {
            nativeLog("StreamCache[\(id)]: giving up, \(status > 0 ? "HTTP" : "transport code") \(status)", warn: true)
            lock.lock(); _status = status; lock.unlock()
            task = nil
            wake()
        }

        /// A headerless 416 cannot distinguish a whole old cache file from a refused resume.
        private func checkLengthAfter416(written: Int64) {
            guard let session else { return fail(416) }
            let check = UUID()
            lengthCheck = check
            var request = URLRequest(url: url)
            request.httpMethod = "HEAD"
            for (name, value) in headers where name.lowercased() != "range" {
                request.setValue(value, forHTTPHeaderField: name)
            }
            request.setValue("identity", forHTTPHeaderField: "Accept-Encoding")
            task = session.dataTask(with: request) { [weak self] _, response, error in
                guard let self else { return }
                self.cache.queue.async {
                    guard self.lengthCheck == check else { return }
                    self.lengthCheck = nil
                    guard error == nil, let http = response as? HTTPURLResponse,
                          http.statusCode == 200, http.expectedContentLength == written else { return self.fail(416) }
                    self.lock.lock(); self._length = written; self.lock.unlock()
                    self.finish()
                }
            }
            task?.resume()
        }

        /// Asks again from the last byte. Attempts count only failures without progress; 429 waits at least as long as Retry-After.
        private func retry(status: Int, retryAfter: Double = 0, error: NSError? = nil) {
            task = nil
            let delays = status == 429 ? Self.throttleDelays : Self.retryDelays
            if progressed { attempt = 0 }
            let reason = error.map { "\($0.domain) \($0.code)" } ?? (status == 0 ? "incomplete response" : "HTTP \(status)")
            // Still at the start (next to nothing on disk): someone is waiting on silence, and the source behind this
            // stream gets its turn only once this gives up. A Qobuz file that sent 1 byte and dropped took five tries,
            // 18 s, before falling back. Two retries there; the full ladder mid-track, and always for 429 (throttling).
            let atStart = state.written < Self.startBytes && status != 429
            let allowed = atStart ? min(Self.startRetries, delays.count) : delays.count
            guard attempt < allowed else {
                nativeLog("StreamCache[\(id)]: last failure: \(reason)", warn: true)
                let transportCode = error.flatMap { $0.domain == NSURLErrorDomain ? $0.code : nil }
                return fail(status == 0 ? (transportCode ?? NSURLErrorNetworkConnectionLost) : status)
            }
            let delay = max(delays[attempt], retryAfter)
            attempt += 1
            nativeLog("StreamCache[\(id)]: broke at \(state.written) (\(reason)), resuming in \(delay)s", warn: true)
            cache.queue.asyncAfter(deadline: .now() + delay) { self.startIfNeeded() }
        }

        func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive response: URLResponse,
                        completionHandler: @escaping (URLSession.ResponseDisposition) -> Void) {
            guard dataTask === task else { return completionHandler(.cancel) }
            let http = response as? HTTPURLResponse
            let status = http?.statusCode ?? 0
            let written = state.written
            if status == 416, written > 0, requestedOffset == written { // already have it all (a finished file from an earlier run)
                completionHandler(.cancel)
                guard let total = Self.total(http?.value(forHTTPHeaderField: "Content-Range")) ?? state.length else {
                    return checkLengthAfter416(written: written)
                }
                guard total == written else { return fail(status) }
                lock.lock(); _length = total; lock.unlock()
                return finish()
            }
            guard (200...299).contains(status) else {
                completionHandler(.cancel)
                if status == 429 || status >= 500 || status == 0 {
                    return retry(status: status, retryAfter: http?.value(forHTTPHeaderField: "Retry-After").flatMap(Double.init) ?? 0)
                }
                return fail(status)
            }
            skip = status == 200 ? written : written - requestedOffset
            let length = Self.total(http?.value(forHTTPHeaderField: "Content-Range"))
                ?? (status == 200 && response.expectedContentLength > 0 ? response.expectedContentLength : nil)
            if let length { lock.lock(); _length = length; lock.unlock() }
            wake() // readers waiting only for the length
            completionHandler(.allow)
        }

        func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive data: Data) {
            guard dataTask === task else { return }
            var chunk = data
            if skip > 0 {
                let drop = Int(min(skip, Int64(chunk.count)))
                chunk = chunk.subdata(in: drop..<chunk.count)
                skip -= Int64(drop)
            }
            if chunk.isEmpty { return }
            let ok = chunk.withUnsafeBytes { write(wfd, $0.baseAddress, chunk.count) } == chunk.count
            guard ok else { dataTask.cancel(); task = nil; return fail(NSURLErrorCannotWriteToFile) }
            lock.lock(); _written += Int64(chunk.count); lock.unlock()
            progressed = true
            if state.written >> 22 != (state.written - Int64(chunk.count)) >> 22 { print("StreamCache[\(id)]: \(state.written >> 20) MB of \(state.length.map { String($0 >> 20) } ?? "?")") }
            wake()
        }

        func urlSession(_ session: URLSession, task finished: URLSessionTask, didCompleteWithError error: Error?) {
            guard finished === task else { return }
            let s = state
            if let error, (error as NSError).code == NSURLErrorCancelled { return }
            if error == nil, s.length == nil || s.written >= s.length! {
                lock.lock(); _length = _length ?? _written; lock.unlock()
                return finish()
            }
            retry(status: 0, error: error as NSError?)
        }

        /// "bytes 0-1023/4567890" or "bytes */4567890" -> 4567890
        private static func total(_ range: String?) -> Int64? {
            guard let range, let slash = range.lastIndex(of: "/") else { return nil }
            return Int64(range[range.index(after: slash)...])
        }
    }
}
