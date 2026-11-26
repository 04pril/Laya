import SwiftUI
import WidgetKit

// Poster cards added beside the original three widgets. The wide card follows WidgetPod's medium
// now-playing widget: Liquid Artwork, curved cover, track/artist and Apple Music glyph.

struct PosterMediaSurface: View {
    var art: PosterArtwork?

    // A literal bitmap keeps even the no-art placeholder out of system accent
    // and appearance colour conversion, just like the album wash.
    private static let fallbackWash: UIImage = {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = true
        format.preferredRange = .standard
        return UIGraphicsImageRenderer(size: CGSize(width: 1, height: 1), format: format).image { context in
            context.cgContext.setFillColor(UIColor(white: 0.45, alpha: 1).cgColor)
            context.cgContext.fill(CGRect(x: 0, y: 0, width: 1, height: 1))
        }
    }()

    // BitChord's PosterBackground: the wash itself, as the widget's container background.
    var body: some View {
        Image(uiImage: art?.surface ?? Self.fallbackWash)
            .resizable()
            .accessibilityHidden(true)
    }
}

struct PosterCover: View {
    var art: PosterArtwork?
    var side: CGFloat
    var cornerRadius: CGFloat? = nil

    /// WidgetPod's curved cover is about 11.5% of the art side.
    private var radius: CGFloat { cornerRadius ?? min(max(side * 0.115, 8), side * 0.2) }

    var body: some View {
        Group {
            if let cover = art?.cover {
                Image(uiImage: cover).resizable().layaArtwork().scaledToFill()
            } else {
                Image(systemName: "music.note").resizable().scaledToFit().padding(16).foregroundStyle(.white.opacity(0.7))
            }
        }
        .frame(width: side, height: side)
        .clipShape(RoundedRectangle(cornerRadius: radius, style: .circular))
        .shadow(color: .black.opacity(0.22), radius: max(4, side * 0.045), y: max(2, side * 0.03))
        .accessibilityLabel("Album artwork")
    }
}

/// Apple Music player icon in Glyph style: two beamed eighth notes, using the reference silhouette.
struct PosterNote: View {
    var art: PosterArtwork?
    var size: CGFloat

    var body: some View {
        Image(uiImage: UIImage(named: "WidgetMusicGlyph") ?? UIImage())
            .renderingMode(.template)
            .resizable()
            .scaledToFit()
            .foregroundStyle(Color(red: 1, green: 0.96, blue: 0.77).opacity(art?.cover == nil ? 0.55 : 1))
            .frame(width: size, height: size)
            .accessibilityHidden(true)
    }
}

/// The reference’s small card is its own layout: upper-left artwork, upper-right glyph,
/// then single-line title and artist aligned left above a full-width transport row.
struct PosterSquareWidgetView: View {
    var snapshot: LayaWidgetSnapshot
    var art: PosterArtwork?


    var body: some View {
        let face = PosterFace(snapshot: snapshot)
        GeometryReader { proxy in
            let scale = min(proxy.size.width, proxy.size.height) / 158
            Group {
                    ZStack(alignment: .topLeading) {
                        PosterCover(art: art, side: 67 * scale, cornerRadius: 10.5 * scale)
                            .position(x: 49 * scale, y: 49 * scale)
                        Text(face.title)
                            .font(.system(size: 12.5 * scale, weight: .semibold))
                            .foregroundStyle(.white)
                            .lineLimit(1)
                            .truncationMode(.tail)
                            .frame(width: 127 * scale, height: 18 * scale, alignment: .leading)
                            .position(x: 79 * scale, y: 95 * scale)
                        if face.hasTrack, let artist = face.artist {
                            Text(artist)
                                .font(.system(size: 12.5 * scale, weight: .regular))
                                .foregroundStyle(Color.white.opacity(0.88))
                                .lineLimit(1)
                                .frame(width: 127 * scale, height: 18 * scale, alignment: .leading)
                                .position(x: 79 * scale, y: 111 * scale)
                        }
                        PosterControl(command: "previous", symbol: "backward.fill", label: "Previous",
                                      opacity: face.opacity(.previous), isLive: face.hasTrack, glyph: 16.5 * scale,
                                      height: 36 * scale, fillsWidth: false, weight: .regular)
                            .position(x: 26 * scale, y: 135.5 * scale)
                        PosterControl(command: "toggle", symbol: face.toggleSymbol, label: face.toggleLabel,
                                      opacity: face.opacity(.togglePlayPause), isLive: face.hasTrack, glyph: 26 * scale,
                                      height: 36 * scale, fillsWidth: false, weight: .regular)
                            .position(x: 79 * scale, y: 135.5 * scale)
                        PosterControl(command: "next", symbol: "forward.fill", label: "Next",
                                      opacity: face.opacity(.next), isLive: face.hasTrack, glyph: 16.5 * scale,
                                      height: 36 * scale, fillsWidth: false, weight: .regular)
                            .position(x: 132 * scale, y: 135.5 * scale)
                        PosterNote(art: art, size: 22 * scale)
                            .position(x: 132 * scale, y: 26 * scale)
                    }
                    .frame(width: 158 * scale, height: 158 * scale)
                    .position(x: proxy.size.width / 2, y: proxy.size.height / 2)
                }
        }
        .dynamicTypeSize(.large)
        .containerBackground(for: .widget) { PosterMediaSurface(art: art) }
        .overlay {
            ContainerRelativeShape()
                .strokeBorder(Color.white.opacity(0.16), lineWidth: 0.6)
                .allowsHitTesting(false)
        }
        .widgetURL(openPlayerURL)
    }
}

/// WidgetPod reference: default artwork, curved corners, Liquid Artwork, title and artist.
/// Measured in a 338×158 medium widget; explicit bounds keep long titles and Dynamic Type
/// from pushing the controls into the artwork or out to the edges.
struct PosterWideWidgetView: View {
    var snapshot: LayaWidgetSnapshot
    var art: PosterArtwork?


    var body: some View {
        let face = PosterFace(snapshot: snapshot)
        GeometryReader { proxy in
            let scale = min(proxy.size.width / 338, proxy.size.height / 158)
            Group {
                    ZStack(alignment: .topLeading) {
                        PosterCover(art: art, side: 135.5 * scale)
                            .position(x: 79.25 * scale, y: 79 * scale)
                        VStack(spacing: 2 * scale) {
                            Text(face.title)
                                .font(.system(size: 14 * scale, weight: .semibold))
                                .foregroundStyle(.white)
                                .multilineTextAlignment(.center)
                                .lineLimit(2)
                                .fixedSize(horizontal: false, vertical: true)
                                .frame(width: 148 * scale)
                            if face.hasTrack, let artist = face.artist {
                                Text(artist)
                                    .font(.system(size: 13.5 * scale, weight: .regular))
                                    .foregroundStyle(Color.white.opacity(0.88))
                                    .lineLimit(1)
                                    .frame(width: 148 * scale)
                            }
                        }
                        .fixedSize(horizontal: false, vertical: true)
                        .frame(width: 148 * scale)
                        .position(x: 243 * scale, y: 64 * scale)
                        PosterControl(command: "previous", symbol: "backward.fill", label: "Previous",
                                      opacity: face.opacity(.previous), isLive: face.hasTrack, glyph: 18 * scale,
                                      height: 38 * scale, fillsWidth: false, weight: .regular)
                            .position(x: 183 * scale, y: 119 * scale)
                        PosterControl(command: "toggle", symbol: face.toggleSymbol, label: face.toggleLabel,
                                      opacity: face.opacity(.togglePlayPause), isLive: face.hasTrack, glyph: 26 * scale,
                                      height: 38 * scale, fillsWidth: false, weight: .regular)
                            .position(x: 243 * scale, y: 119 * scale)
                        PosterControl(command: "next", symbol: "forward.fill", label: "Next",
                                      opacity: face.opacity(.next), isLive: face.hasTrack, glyph: 18 * scale,
                                      height: 38 * scale, fillsWidth: false, weight: .regular)
                            .position(x: 303 * scale, y: 119 * scale)
                        PosterNote(art: art, size: 22 * scale)
                            .position(x: 312 * scale, y: 25 * scale)
                    }
                    .frame(width: 338 * scale, height: 158 * scale)
                    .position(x: proxy.size.width / 2, y: proxy.size.height / 2)
                }
        }
        .dynamicTypeSize(.large)
        .containerBackground(for: .widget) { PosterMediaSurface(art: art) }
        .overlay {
            ContainerRelativeShape()
                .strokeBorder(Color.white.opacity(0.16), lineWidth: 0.6)
                .allowsHitTesting(false)
        }
        .widgetURL(openPlayerURL)
    }
}


struct PosterArtwork {
    let cover: UIImage?
    let surface: UIImage
}

struct PosterFace {
    let snapshot: LayaWidgetSnapshot
    var title: String { snapshot.hasTrack ? snapshot.title : "Laya" }
    var artist: String? { snapshot.artist.isEmpty ? nil : snapshot.artist }
    var hasTrack: Bool { snapshot.hasTrack }
    var toggleSymbol: String { snapshot.isPlaying ? "pause.fill" : "play.fill" }
    var toggleLabel: String { snapshot.isPlaying ? "Pause" : "Play" }
    enum Action { case previous, next, togglePlayPause }
    func opacity(_ action: Action) -> Double {
        switch action {
        case .previous: return snapshot.hasTrack && snapshot.hasPrevious ? 1 : 90.0 / 255
        case .next: return snapshot.hasTrack && snapshot.hasNext ? 1 : 90.0 / 255
        case .togglePlayPause: return 1
        }
    }
}

private struct PosterControl: View {
    let command: String
    let symbol: String
    let label: String
    let opacity: Double
    let isLive: Bool
    let glyph: CGFloat
    let height: CGFloat
    let fillsWidth: Bool
    let weight: Font.Weight
    var body: some View {
        let icon = Image(systemName: symbol)
            .font(.system(size: glyph, weight: weight))
            .foregroundStyle(.white).opacity(opacity)
            .frame(width: height, height: height).contentShape(Rectangle())
        if isLive {
            Button(intent: LayaWidgetIntent(command)) { icon }.buttonStyle(.plain).accessibilityLabel(label)
        } else { icon.accessibilityLabel(label) }
    }
}

extension Image {
    @ViewBuilder func layaArtwork() -> some View {
        if #available(iOS 18.0, *) { renderingMode(.original).widgetAccentedRenderingMode(.fullColor) }
        else { renderingMode(.original) }
    }
}

struct ColorMediumWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "LayaColorMedium", provider: LayaProvider(face: { _ in .poster })) { entry in
            PosterWideWidgetView(snapshot: entry.snapshot, art: posterArt(entry))
        }
        .configurationDisplayName("WidgetPod · 4×2")
        .description("Liquid Artwork, curved cover and playback controls.")
        .supportedFamilies([.systemMedium]).contentMarginsDisabled()
    }
}

struct ColorSmallWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "LayaColorSmall", provider: LayaProvider(face: { _ in .poster })) { entry in
            PosterSquareWidgetView(snapshot: entry.snapshot, art: posterArt(entry))
        }
        .configurationDisplayName("WidgetPod · 2×2")
        .description("Liquid Artwork, artwork and playback controls.")
        .supportedFamilies([.systemSmall]).contentMarginsDisabled()
    }
}

private func posterArt(_ entry: LayaEntry) -> PosterArtwork? {
    guard let surface = entry.face else { return nil }
    return PosterArtwork(cover: entry.art, surface: surface)
}
