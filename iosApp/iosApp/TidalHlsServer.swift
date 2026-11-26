import Foundation
import Network

/// Serves a Tidal HLS index to AVPlayer on loopback. Every segment is fetched by Laya's
/// URLSession so the configured app proxy also applies to the media bytes.
final class TidalHlsServer {
    static let shared = TidalHlsServer()

    private struct Playlist {
        let text: Data
        let segments: [URL]
        let requiresByteRanges: Bool
        /// Sent with every upstream fetch: a YouTube stream wants its minting client's headers (none for Tidal).
        var headers: [String: String] = [:]
        var network = NetworkReport()
    }

    private struct NetworkReport {
        var requests = 0
        var replies = 0
        var proxied = 0
        var direct = 0
        var slowestMs = 0
        var bytes: Int64 = 0
        var lastFailure: String?
        var slowFetches: [String] = []
    }

    private final class MetricsCollector: NSObject, URLSessionTaskDelegate {
        let record: (Int, URLSessionTaskMetrics) -> Void
        init(record: @escaping (Int, URLSessionTaskMetrics) -> Void) { self.record = record }

        func urlSession(_ session: URLSession, task: URLSessionTask, didFinishCollecting metrics: URLSessionTaskMetrics) {
            record(task.taskIdentifier, metrics)
        }
    }

    private let queue = DispatchQueue(label: "laya.tidal-hls")
    private var listener: NWListener?
    private var port: NWEndpoint.Port?
    private var playlists: [String: Playlist] = [:]
    private var playlistOrder: [String] = []
    private var tasks: [ObjectIdentifier: URLSessionTask] = [:]
    private var observedTasks: [Int: (id: String, segment: Int)] = [:]
    private var shouldObserve: Bool { LayaAppIdentity.scheme == "layatest" }
    private lazy var session: URLSession = {
        let config = URLSessionConfiguration.ephemeral
        config.timeoutIntervalForRequest = 12
        config.timeoutIntervalForResource = 18
        config.requestCachePolicy = .reloadIgnoringLocalCacheData
        let collector = shouldObserve ? MetricsCollector { [weak self] id, metrics in
            self?.queue.async { [weak self] in self?.recordMetrics(task: id, metrics: metrics) }
        } : nil
        return URLSession(configuration: config, delegate: collector, delegateQueue: nil)
    }()

    func start() {
        queue.async { self.startIfNeeded() }
    }

    private func startIfNeeded() {
        guard listener == nil else { return }
        let parameters = NWParameters.tcp
        parameters.requiredLocalEndpoint = .hostPort(host: "127.0.0.1", port: .any)
        guard let listener = try? NWListener(using: parameters) else { return }
        self.listener = listener
        listener.stateUpdateHandler = { [weak self, weak listener] state in
            guard let self, let listener else { return }
            switch state {
            case .ready: self.port = listener.port
            case .failed, .cancelled:
                self.port = nil
                self.listener = nil
            default: break
            }
        }
        listener.newConnectionHandler = { [weak self] connection in self?.accept(connection) }
        listener.start(queue: queue)
    }

    func register(playlist: String, segments: [String], headers: [String: String] = [:]) -> String? {
        let urls = segments.compactMap { raw -> URL? in
            guard let url = URL(string: raw), url.scheme == "https", url.host != nil,
                  url.user == nil, url.password == nil, url.fragment == nil else { return nil }
            return url
        }
        guard urls.count == segments.count, urls.count > 1 else { return nil }
        return queue.sync {
            startIfNeeded()
            guard let port else { return nil }
            let id = UUID().uuidString
            let requiresByteRanges = urls.allSatisfy { $0 == urls.first }
            playlists[id] = Playlist(text: Data(playlist.utf8), segments: urls, requiresByteRanges: requiresByteRanges, headers: headers)
            playlistOrder.append(id)
            if playlistOrder.count > 64 { playlists.removeValue(forKey: playlistOrder.removeFirst()) }
            return "http://127.0.0.1:\(port.rawValue)/tidal/\(id)/playlist.m3u8"
        }
    }

    func owns(_ url: URL) -> Bool {
        guard url.scheme == "http", url.host == "127.0.0.1", url.lastPathComponent == "playlist.m3u8" else {
            return false
        }
        return queue.sync {
            url.port == port.map { Int($0.rawValue) } &&
                playlists[url.deletingLastPathComponent().lastPathComponent] != nil
        }
    }

    func diagnostics(for url: URL) -> [String] {
        queue.sync {
            let id = url.deletingLastPathComponent().lastPathComponent
            guard let playlist = playlists[id] else { return ["tidal HLS: no retained network observations"] }
            let report = playlist.network
            let unknown = max(0, report.replies - report.proxied - report.direct)
            return [
                "tidal HLS: segments=\(playlist.segments.count - 1) requests=\(report.requests) received=\(report.replies)",
                "tidal network (URLSession): proxy=\(report.proxied) direct=\(report.direct) unclassified=\(unknown)",
                "tidal network: bytesReceived=\(report.bytes) slowestRequestMs=\(report.slowestMs)",
            ] + (report.lastFailure.map { ["tidal latest fetch failure: \($0)"] } ?? []) + report.slowFetches
        }
    }

    private func recordMetrics(task: Int, metrics: URLSessionTaskMetrics) {
        guard let observed = observedTasks.removeValue(forKey: task),
              let transfer = metrics.transactionMetrics.last,
              transfer.resourceFetchType == .networkLoad,
              let reply = transfer.response as? HTTPURLResponse, [200, 206].contains(reply.statusCode) else { return }
        let ms = Int(metrics.taskInterval.duration * 1000)
        let route = transfer.isProxyConnection ? "proxy" : "direct"
        if transfer.isProxyConnection { playlists[observed.id]?.network.proxied += 1 }
        else { playlists[observed.id]?.network.direct += 1 }
        playlists[observed.id]?.network.bytes += transfer.countOfResponseBodyBytesReceived
        if let slowest = playlists[observed.id]?.network.slowestMs, ms > slowest {
            playlists[observed.id]?.network.slowestMs = ms
        }
        if ms >= 750, let count = playlists[observed.id]?.network.slowFetches.count {
            if count >= 12 { playlists[observed.id]?.network.slowFetches.removeFirst() }
            playlists[observed.id]?.network.slowFetches.append(
                "tidal slow fetch: segment=\(observed.segment) elapsedMs=\(ms) route=\(route)"
            )
        }
    }

    private func accept(_ connection: NWConnection) {
        let key = ObjectIdentifier(connection)
        connection.stateUpdateHandler = { [weak self] state in
            switch state {
            case .failed, .cancelled: self?.tasks.removeValue(forKey: key)?.cancel()
            default: break
            }
        }
        connection.start(queue: queue)
        readRequest(connection, accumulated: Data())
        queue.asyncAfter(deadline: .now() + 22) { [weak connection] in connection?.cancel() }
    }

    private func readRequest(_ connection: NWConnection, accumulated: Data) {
        connection.receive(minimumIncompleteLength: 1, maximumLength: 8192) { [weak self] data, _, complete, error in
            guard let self, error == nil, let data else { connection.cancel(); return }
            var request = accumulated
            request.append(data)
            if request.count > 8192 { self.respond(connection, status: "400 Bad Request"); return }
            if let end = request.range(of: Data("\r\n\r\n".utf8)) {
                self.handle(connection, request: String(decoding: request[..<end.lowerBound], as: UTF8.self))
            } else if complete {
                connection.cancel()
            } else {
                self.readRequest(connection, accumulated: request)
            }
        }
    }

    private func handle(_ connection: NWConnection, request: String) {
        let lines = request.components(separatedBy: "\r\n")
        let line = lines.first?.split(separator: " ") ?? []
        guard line.count == 3, line[0] == "GET" || line[0] == "HEAD" else {
            respond(connection, status: "405 Method Not Allowed")
            return
        }
        let head = line[0] == "HEAD"
        let path = line[1].split(separator: "/")
        guard path.count == 3, path[0] == "tidal", let playlist = playlists[String(path[1])] else {
            respond(connection, status: "404 Not Found")
            return
        }
        let playlistId = String(path[1])
        let file = path[2]
        if file == "playlist.m3u8" {
            respond(connection, type: "application/vnd.apple.mpegurl", body: playlist.text, head: head)
            return
        }
        guard file.hasPrefix("segment-"), file.hasSuffix(".m4s"),
              let index = Int(file.dropFirst(8).dropLast(4)), playlist.segments.indices.contains(index) else {
            respond(connection, status: "404 Not Found")
            return
        }

        var upstream = URLRequest(url: playlist.segments[index])
        upstream.httpMethod = head ? "HEAD" : "GET"
        for (name, value) in playlist.headers where name.lowercased() != "range" { upstream.setValue(value, forHTTPHeaderField: name) }
        upstream.setValue("identity", forHTTPHeaderField: "Accept-Encoding")
        let range = lines.dropFirst().first(where: { $0.lowercased().hasPrefix("range:") })?
            .dropFirst(6).trimmingCharacters(in: .whitespaces)
        if let range, range.range(of: #"^bytes=[0-9]*-[0-9]*$"#, options: .regularExpression) != nil {
            upstream.setValue(range, forHTTPHeaderField: "Range")
        } else if playlist.requiresByteRanges && !head {
            respond(connection, status: "416 Range Not Satisfiable")
            return
        }

        let key = ObjectIdentifier(connection)
        let completed: (Data?, URLResponse?, Error?) -> Void = { [weak self] data, response, error in
            guard let self else { return }
            self.queue.async {
                guard self.tasks.removeValue(forKey: key) != nil else { return }
                guard let response = response as? HTTPURLResponse, response.url?.scheme == "https",
                      [200, 206].contains(response.statusCode),
                      !playlist.requiresByteRanges || head || response.statusCode == 206,
                      let data else {
                    if self.shouldObserve {
                        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
                        let code = (error as NSError?)?.code ?? 0
                        self.playlists[playlistId]?.network.lastFailure = "segment=\(index) HTTP=\(status) errorCode=\(code)"
                    }
                    self.respond(connection, status: "502 Bad Gateway")
                    return
                }
                if self.shouldObserve && !head { self.playlists[playlistId]?.network.replies += 1 }
                self.respond(connection, status: response.statusCode == 206 ? "206 Partial Content" : "200 OK",
                             type: "audio/mp4", body: data, head: head,
                             length: head && response.expectedContentLength >= 0 ? response.expectedContentLength : nil,
                             range: response.value(forHTTPHeaderField: "Content-Range"))
            }
        }
        let task: URLSessionTask
        if head {
            task = session.dataTask(with: upstream) { _, response, error in completed(Data(), response, error) }
        } else {
            task = session.downloadTask(with: upstream) { file, response, error in
                // An audio fragment should be small. Download to disk before allocating a response buffer.
                let bytes = file.flatMap { url -> Data? in
                    guard let size = try? url.resourceValues(forKeys: [.fileSizeKey]).fileSize,
                          size <= 8 * 1024 * 1024 else { return nil }
                    return try? Data(contentsOf: url)
                }
                completed(bytes, response, error)
            }
        }
        tasks[key] = task
        if shouldObserve && !head {
            if observedTasks.count >= 256 { observedTasks.removeAll(keepingCapacity: true) }
            observedTasks[task.taskIdentifier] = (playlistId, index)
            playlists[playlistId]?.network.requests += 1
        }
        task.resume()
    }

    private func respond(_ connection: NWConnection, status: String = "200 OK", type: String = "text/plain",
                         body: Data = Data(), head: Bool = false, length: Int64? = nil, range: String? = nil) {
        var header = "HTTP/1.1 \(status)\r\nContent-Type: \(type)\r\nContent-Length: \(length ?? Int64(body.count))\r\n"
        if let range { header += "Content-Range: \(range)\r\n" }
        header += "Connection: close\r\n\r\n"
        var response = Data(header.utf8)
        if !head { response.append(body) }
        connection.send(content: response, completion: .contentProcessed { _ in connection.cancel() })
    }
}
