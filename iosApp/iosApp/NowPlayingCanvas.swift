import AVFoundation
import MediaPlayer
import UIKit

/// Gives MediaPlayer a local clip and a preview of its first frame. The artwork's
/// request handlers retain the file for as long as the system can ask for it.
@available(iOS 26.0, *)
enum NowPlayingCanvas {
    private final class Clip {
        let url: URL
        init(_ url: URL) { self.url = url }
        deinit { try? FileManager.default.removeItem(at: url) }
    }

    static var key: String? {
        let keys = MPNowPlayingInfoCenter.supportedAnimatedArtworkKeys
        if keys.contains(MPNowPlayingInfoProperty3x4AnimatedArtwork) { return MPNowPlayingInfoProperty3x4AnimatedArtwork }
        if keys.contains(MPNowPlayingInfoProperty1x1AnimatedArtwork) { return MPNowPlayingInfoProperty1x1AnimatedArtwork }
        return nil
    }

    static func artwork(from remote: URL, key: String) async throws -> MPMediaItemAnimatedArtwork {
        let source = try await download(remote)
        let size = key == MPNowPlayingInfoProperty3x4AnimatedArtwork
            ? CGSize(width: 480, height: 640) : CGSize(width: 480, height: 480)
        let clip = try await crop(source, to: size)
        try Task.checkCancellation()
        let generator = AVAssetImageGenerator(asset: AVURLAsset(url: clip.url))
        generator.appliesPreferredTrackTransform = true
        generator.maximumSize = size
        generator.requestedTimeToleranceAfter = .zero
        generator.requestedTimeToleranceBefore = .zero
        let firstFrame = try await generator.image(at: .zero).image
        return MPMediaItemAnimatedArtwork(
            artworkID: UUID().uuidString,
            previewImageRequestHandler: { _, completion in completion(UIImage(cgImage: firstFrame)) },
            videoAssetFileURLRequestHandler: { _, completion in completion(clip.url) }
        )
    }

    private static func download(_ remote: URL) async throws -> Clip {
        guard remote.scheme == "https", remote.pathExtension.lowercased() != "m3u8" else {
            throw URLError(.unsupportedURL)
        }
        var request = URLRequest(url: remote)
        request.timeoutInterval = 12
        let (url, response) = try await URLSession.shared.download(for: request)
        defer { try? FileManager.default.removeItem(at: url) }
        let size = try url.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0
        guard let response = response as? HTTPURLResponse, (200..<300).contains(response.statusCode),
              response.url?.scheme == "https", size > 0, size <= 24 * 1024 * 1024 else {
            throw URLError(.cannotDecodeContentData)
        }
        let clip = Clip(localURL())
        try FileManager.default.moveItem(at: url, to: clip.url)
        return clip
    }

    private static func crop(_ source: Clip, to size: CGSize) async throws -> Clip {
        let asset = AVURLAsset(url: source.url)
        guard let video = try await asset.loadTracks(withMediaType: .video).first else {
            throw URLError(.cannotDecodeContentData)
        }
        let (naturalSize, transform) = try await video.load(.naturalSize, .preferredTransform)
        let bounds = CGRect(origin: .zero, size: naturalSize).applying(transform).standardized
        guard bounds.width > 0, bounds.height > 0 else { throw URLError(.cannotDecodeContentData) }
        let scale = max(size.width / bounds.width, size.height / bounds.height)
        let offset = CGAffineTransform(translationX: (size.width - bounds.width * scale) / 2,
                                       y: (size.height - bounds.height * scale) / 2)
        let fitted = transform
            .concatenating(CGAffineTransform(translationX: -bounds.minX, y: -bounds.minY))
            .concatenating(CGAffineTransform(scaleX: scale, y: scale))
            .concatenating(offset)

        let duration = try await asset.load(.duration)
        guard duration.seconds.isFinite, duration.seconds > 0 else { throw URLError(.cannotDecodeContentData) }
        let timeRange = CMTimeRange(start: .zero, duration: CMTimeMinimum(duration, CMTime(seconds: 18, preferredTimescale: 600)))
        let composition = AVMutableComposition()
        guard let track = composition.addMutableTrack(withMediaType: .video, preferredTrackID: kCMPersistentTrackID_Invalid) else {
            throw URLError(.cannotDecodeContentData)
        }
        try track.insertTimeRange(timeRange, of: video, at: .zero)
        let layer = AVMutableVideoCompositionLayerInstruction(assetTrack: track)
        layer.setTransform(fitted, at: .zero)
        let instruction = AVMutableVideoCompositionInstruction()
        instruction.timeRange = timeRange
        instruction.layerInstructions = [layer]
        let videoComposition = AVMutableVideoComposition()
        videoComposition.renderSize = size
        videoComposition.frameDuration = CMTime(value: 1, timescale: 30)
        videoComposition.instructions = [instruction]

        guard let session = AVAssetExportSession(asset: composition, presetName: AVAssetExportPresetHighestQuality) else {
            throw URLError(.cannotDecodeContentData)
        }
        session.videoComposition = videoComposition
        let clip = Clip(localURL())
        try await session.export(to: clip.url, as: .mp4)
        return clip
    }

    private static func localURL() -> URL {
        FileManager.default.temporaryDirectory.appendingPathComponent("laya-canvas-\(UUID()).mp4")
    }
}
