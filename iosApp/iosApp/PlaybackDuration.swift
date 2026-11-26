import AudioToolbox
import Foundation

enum PlaybackDuration {
    /// Cap AVFoundation's playback range only when its timeline materially exceeds the media length.
    static func endpoint(native: Double, known: Double?) -> Double? {
        guard let known, known.isFinite, known > 0, native.isFinite,
              native - known > max(0.5, known * 0.01) else { return nil }
        return known
    }

    /// An estimate from an older download is used only when it identifies the known doubled AAC timeline.
    static func correctsDoubledTimeline(native: Double, file: Double) -> Bool {
        native.isFinite && file.isFinite && file > 0 && abs(native - file * 2) < max(0.25, file * 0.02)
    }

    /// AudioToolbox reads the AAC packet timing independently of AVPlayer's fragmented MP4 timeline.
    /// AudioFileOpenURL can read on-disk metadata, so callers run it off the main thread.
    static func estimateAAC(in url: URL) -> Double? {
        var file: AudioFileID?
        guard AudioFileOpenURL(url as CFURL, .readPermission, 0, &file) == noErr, let file else { return nil }
        defer { AudioFileClose(file) }

        var format = AudioStreamBasicDescription()
        var size = UInt32(MemoryLayout.size(ofValue: format))
        guard AudioFileGetProperty(file, kAudioFilePropertyDataFormat, &size, &format) == noErr,
              [kAudioFormatMPEG4AAC, kAudioFormatMPEG4AAC_HE, kAudioFormatMPEG4AAC_HE_V2].contains(format.mFormatID)
        else { return nil }

        var duration = 0.0
        size = UInt32(MemoryLayout.size(ofValue: duration))
        guard AudioFileGetProperty(file, kAudioFilePropertyEstimatedDuration, &size, &duration) == noErr,
              duration.isFinite, duration > 0 else { return nil }
        return duration
    }
}
