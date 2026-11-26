import AVFoundation
import Foundation

@main struct PlaybackDurationTests {
    static func main() throws {
        // A healthy timeline, including normal rounding around the end, should advance by itself.
        precondition(PlaybackDuration.endpoint(native: 214.25, known: 214.25) == nil)
        precondition(PlaybackDuration.endpoint(native: 214.45, known: 214.25) == nil)
        precondition(PlaybackDuration.endpoint(native: 200, known: 214.25) == nil)
        precondition(PlaybackDuration.endpoint(native: .nan, known: 214.25) == nil)
        precondition(PlaybackDuration.endpoint(native: 428.5, known: 214.25) == 214.25)
        precondition(PlaybackDuration.endpoint(native: 428.5, known: nil) == nil)

        // An old download has no saved duration. Only use its estimated timing for the doubled case.
        precondition(PlaybackDuration.correctsDoubledTimeline(native: 428.5, file: 214.25))
        precondition(!PlaybackDuration.correctsDoubledTimeline(native: 214.25, file: 214.25))
        precondition(!PlaybackDuration.correctsDoubledTimeline(native: 360, file: 214.25))
        precondition(!PlaybackDuration.correctsDoubledTimeline(native: 428.5, file: 0))

        let url = FileManager.default.temporaryDirectory.appendingPathComponent("laya-\(UUID()).m4a")
        defer { try? FileManager.default.removeItem(at: url) }
        try writeAAC(to: url)
        guard let seconds = PlaybackDuration.estimateAAC(in: url), abs(seconds - 2) < 0.1 else {
            fatalError("AudioToolbox did not read the AAC duration")
        }
        print("PASS: safe playback endpoints and independent local AAC duration")
    }

    private static func writeAAC(to url: URL) throws {
        let file = try AVAudioFile(forWriting: url, settings: [
            AVFormatIDKey: kAudioFormatMPEG4AAC,
            AVSampleRateKey: 44_100,
            AVNumberOfChannelsKey: 1,
        ])
        let buffer = AVAudioPCMBuffer(pcmFormat: file.processingFormat, frameCapacity: 88_200)!
        buffer.frameLength = buffer.frameCapacity
        buffer.floatChannelData?[0].update(repeating: 0, count: Int(buffer.frameLength))
        try file.write(from: buffer)
    }
}
