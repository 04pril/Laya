import Foundation

/// Makes the encoder delay explicit on a Google fragmented AAC header that already has an edit list.
/// With the edit list alone, AVFoundation also discards 2,112 samples of implicit AAC priming.
enum GoogleAACPriming {
    static let headerBytes = 4_096

    static func normalize(_ data: inout Data) -> Bool {
        var header = Array(data.prefix(headerBytes))
        guard let ftyp = box(in: header, at: 0, end: header.count), ftyp.type == "ftyp", ftyp.body.count >= 8,
              word(in: header, at: ftyp.body.lowerBound) == 0x64617368, // dash
              let moov = box(in: header, at: ftyp.range.upperBound, end: header.count), moov.type == "moov",
              let movie = children(in: header, range: moov.body), movie.contains(where: { $0.type == "mvex" }),
              movie.filter({ $0.type == "trak" }).count == 1,
              let trak = movie.first(where: { $0.type == "trak" }),
              let track = children(in: header, range: trak.body),
              let edts = track.first(where: { $0.type == "edts" }),
              let edits = children(in: header, range: edts.body), edits.count == 1,
              let elst = edits.first, elst.type == "elst", elst.body.count == 20,
              word(in: header, at: elst.body.lowerBound) == 0, // version 0
              word(in: header, at: elst.body.lowerBound + 4) == 1,
              word(in: header, at: elst.body.lowerBound + 12) == 1_600,
              word(in: header, at: elst.body.lowerBound + 16) == 0x00010000, // media rate 1
              let mdia = track.first(where: { $0.type == "mdia" }),
              let minf = child("minf", of: mdia, in: header),
              let stbl = child("stbl", of: minf, in: header),
              let samples = children(in: header, range: stbl.body),
              !samples.contains(where: { $0.type == "sgpd" || $0.type == "sbgp" }),
              let stsd = samples.first(where: { $0.type == "stsd" }), stsd.body.count >= 16,
              word(in: header, at: stsd.body.lowerBound + 4) == 1,
              box(in: header, at: stsd.body.lowerBound + 8, end: stsd.body.upperBound)?.type == "mp4a",
              let stsz = samples.first(where: { $0.type == "stsz" }), stsz.body.count == 12,
              word(in: header, at: stsz.body.lowerBound + 8) == 0, // The actual sample runs live in the fragments.
              let udta = movie.last, udta.type == "udta", isGoogleEncoderTag(udta, in: header)
        else { return false }

        // The initial sample table is empty. Declare the AAC roll description and leave its sample mapping empty.
        let groups: [UInt8] = [
            0, 0, 0, 26, 0x73, 0x67, 0x70, 0x64, // sgpd
            1, 0, 0, 0, 0x72, 0x6f, 0x6c, 0x6c, 0, 0, 0, 2, 0, 0, 0, 1, 0xff, 0xff,
            0, 0, 0, 20, 0x73, 0x62, 0x67, 0x70, // sbgp
            0, 0, 0, 0, 0x72, 0x6f, 0x6c, 0x6c, 0, 0, 0, 0,
        ]
        let insertion = stbl.range.upperBound
        let freeBytes = udta.range.count - groups.count
        guard insertion <= udta.range.lowerBound, freeBytes >= 8 else { return false }
        // Reuse the encoder tag's space so the size of moov and all byte-range offsets stay fixed.
        for parent in [trak, mdia, minf, stbl] {
            header.replaceSubrange(parent.range.lowerBound..<(parent.range.lowerBound + 4),
                                   with: sizeBytes(parent.range.count + groups.count))
        }
        let free = sizeBytes(freeBytes) + Array("free".utf8) + [UInt8](repeating: 0, count: freeBytes - 8)
        let between = header[insertion..<udta.range.lowerBound]
        header.replaceSubrange(insertion..<udta.range.upperBound, with: groups + between + free)
        data.replaceSubrange(data.startIndex..<data.index(data.startIndex, offsetBy: moov.range.upperBound),
                             with: header[..<moov.range.upperBound])
        return true
    }

    private struct Box {
        let type: String
        let range: Range<Int>
        var body: Range<Int> { (range.lowerBound + 8)..<range.upperBound }
    }

    private static func isGoogleEncoderTag(_ udta: Box, in bytes: [UInt8]) -> Bool {
        // This exact udta contains only the Google encoder name. Preserve other metadata by leaving such files alone.
        guard let metadata = children(in: bytes, range: udta.body), metadata.count == 1,
              let meta = metadata.first, meta.type == "meta", meta.body.count >= 4,
              let fields = children(in: bytes, range: (meta.body.lowerBound + 4)..<meta.body.upperBound),
              fields.map(\.type) == ["hdlr", "ilst"],
              let tags = children(in: bytes, range: fields[1].body), tags.count == 1, let tag = tags.first,
              bytes[(tag.range.lowerBound + 4)..<(tag.range.lowerBound + 8)].elementsEqual([0xa9, 0x74, 0x6f, 0x6f]),
              let value = child("data", of: tag, in: bytes), value.range.count == tag.body.count,
              value.body.count == 14
        else { return false }
        return bytes[(value.body.lowerBound + 8)..<value.body.upperBound].elementsEqual("Google".utf8)
    }

    private static func child(_ type: String, of parent: Box, in bytes: [UInt8]) -> Box? {
        children(in: bytes, range: parent.body)?.first(where: { $0.type == type })
    }

    private static func children(in bytes: [UInt8], range: Range<Int>) -> [Box]? {
        var children: [Box] = []
        var offset = range.lowerBound
        while offset < range.upperBound {
            guard let next = box(in: bytes, at: offset, end: range.upperBound) else { return nil }
            children.append(next)
            offset = next.range.upperBound
        }
        return children
    }

    private static func box(in bytes: [UInt8], at offset: Int, end: Int) -> Box? {
        guard end - offset >= 8 else { return nil }
        let size = Int(word(in: bytes, at: offset))
        guard size >= 8, size <= end - offset else { return nil }
        return Box(type: String(decoding: bytes[(offset + 4)..<(offset + 8)], as: UTF8.self),
                   range: offset..<(offset + size))
    }

    private static func word(in bytes: [UInt8], at offset: Int) -> UInt32 {
        bytes[offset..<(offset + 4)].reduce(0) { ($0 << 8) | UInt32($1) }
    }

    private static func sizeBytes(_ size: Int) -> [UInt8] {
        withUnsafeBytes(of: UInt32(size).bigEndian, Array.init)
    }
}
