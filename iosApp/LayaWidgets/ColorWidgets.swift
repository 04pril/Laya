import SwiftUI
import WidgetKit

extension Image {
    @ViewBuilder func layaArtwork() -> some View {
        if #available(iOS 18.0, *) { widgetAccentedRenderingMode(.fullColor) }
        else { self }
    }
}

private struct ColorWidgetView: View {
    let entry: LayaEntry
    let small: Bool
    private var snapshot: LayaWidgetSnapshot { entry.snapshot }

    private func cover(_ size: CGFloat) -> some View {
        Group {
            if let art = entry.art { Image(uiImage: art).resizable().layaArtwork().scaledToFill() }
            else { Image(systemName: "music.note").resizable().scaledToFit().padding(14).foregroundStyle(.white.opacity(0.8)) }
        }
        .frame(width: size, height: size)
        .clipShape(RoundedRectangle(cornerRadius: size * 0.115))
        .shadow(color: .black.opacity(0.25), radius: 5, y: 3)
    }

    private var title: some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(snapshot.hasTrack ? snapshot.title : "Laya").font(.system(size: small ? 14 : 16, weight: .semibold)).lineLimit(small ? 1 : 2)
            Text(snapshot.hasTrack ? snapshot.artist : "Play your music").font(.system(size: 12)).foregroundStyle(.white.opacity(0.75)).lineLimit(1)
        }.frame(maxWidth: .infinity, alignment: .leading)
    }

    private var controls: some View {
        HStack(spacing: small ? 12 : 20) {
            WidgetButton(glyph: "ic_widget_previous", command: "previous", size: 30, inset: 5, enabled: snapshot.hasTrack && snapshot.hasPrevious)
            WidgetButton(glyph: snapshot.isPlaying ? "ic_widget_pause" : "ic_widget_play", command: "toggle", size: 36, inset: 5, enabled: snapshot.hasTrack)
            WidgetButton(glyph: "ic_widget_next", command: "next", size: 30, inset: 5, enabled: snapshot.hasTrack && snapshot.hasNext)
        }
    }

    var body: some View {
        ZStack {
            // Also drawn as content: removing a widget's container background must not erase its artwork.
            if let wash = entry.face { Image(uiImage: wash).resizable().layaArtwork().scaledToFill().opacity(0.7) }
            LinearGradient(colors: [.black.opacity(0.08), .black.opacity(0.65)], startPoint: .top, endPoint: .bottom)
            if small {
                VStack(alignment: .leading, spacing: 8) {
                    HStack { cover(48); Spacer(); Image(systemName: "music.note").font(.system(size: 22, weight: .semibold)).opacity(0.8) }
                    title
                    controls.frame(maxWidth: .infinity)
                }.padding(14)
            } else {
                HStack(spacing: 16) {
                    cover(112)
                    VStack(alignment: .leading, spacing: 9) { title; controls }
                }.padding(16)
            }
        }
        .foregroundStyle(.white)
        .clipped()
        .containerBackground(for: .widget) { Color(red: 0.13, green: 0.16, blue: 0.22) }
        .widgetURL(openPlayerURL)
    }
}

struct ColorMediumWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "LayaColorMedium", provider: LayaProvider(face: { _ in .body })) {
            ColorWidgetView(entry: $0, small: false)
        }
        .configurationDisplayName("Color · 4×2")
        .description("Album colors, artwork and playback controls.")
        .supportedFamilies([.systemMedium])
        .contentMarginsDisabled()
    }
}

struct ColorSmallWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "LayaColorSmall", provider: LayaProvider(face: { _ in .body })) {
            ColorWidgetView(entry: $0, small: true)
        }
        .configurationDisplayName("Color · 2×2")
        .description("A compact card with album colors and playback controls.")
        .supportedFamilies([.systemSmall])
        .contentMarginsDisabled()
    }
}
