import CoreGraphics
import Foundation

/// A single media bitmap: WidgetKit must not classify/tint the wash separately
/// from the album artwork. Use explicit 8-bit sRGB and opaque pixels, avoiding
/// dynamic colours, extended-range images and appearance-dependent materials.
enum PosterSurfaceRenderer {
    static func render(cover: CGImage?, wash: CGImage?, size: CGSize, small: Bool, scale: CGFloat) -> CGImage? {
        guard size.width > 0, size.height > 0, scale > 0 else { return nil }
        let width = Int(ceil(size.width * scale))
        let height = Int(ceil(size.height * scale))
        guard let space = CGColorSpace(name: CGColorSpace.sRGB),
              let context = CGContext(data: nil, width: width, height: height, bitsPerComponent: 8,
                bytesPerRow: width * 4, space: space, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { return nil }
        context.setFillColor(CGColor(srgbRed: 0.45, green: 0.45, blue: 0.45, alpha: 1))
        context.fill(CGRect(x: 0, y: 0, width: width, height: height))
        context.scaleBy(x: scale, y: scale)
        if let wash { context.draw(wash, in: CGRect(origin: .zero, size: size)) }

        if let cover {
            // The same measured 338x158 / 158x158 layout as BitChord. Convert
            // its top-left positions into Core Graphics' bottom-left coordinates.
            let designWidth: CGFloat = small ? 158 : 338
            let unit = min(size.width / designWidth, size.height / 158)
            let side = (small ? 67 : 135.5) * unit
            let centerX = (size.width - designWidth * unit) / 2 + (small ? 49 : 79.25) * unit
            let centerY = (size.height - 158 * unit) / 2 + (small ? 49 : 79) * unit
            let rect = CGRect(x: centerX - side / 2, y: size.height - centerY - side / 2, width: side, height: side)
            let radius = small ? 10.5 * unit : min(max(side * 0.115, 8), side * 0.2)
            let shape = CGPath(roundedRect: rect, cornerWidth: radius, cornerHeight: radius, transform: nil)
            context.saveGState()
            context.setShadow(offset: CGSize(width: 0, height: -max(2, side * 0.03)),
                blur: max(4, side * 0.045), color: CGColor(gray: 0, alpha: 0.22))
            context.setFillColor(CGColor(gray: 0, alpha: 1))
            context.addPath(shape)
            context.fillPath()
            context.restoreGState()
            context.saveGState()
            context.addPath(shape)
            context.clip()
            let fill = max(side / CGFloat(cover.width), side / CGFloat(cover.height))
            let artSize = CGSize(width: CGFloat(cover.width) * fill, height: CGFloat(cover.height) * fill)
            context.interpolationQuality = .high
            context.draw(cover, in: CGRect(x: rect.midX - artSize.width / 2, y: rect.midY - artSize.height / 2,
                width: artSize.width, height: artSize.height))
            context.restoreGState()
        }
        return context.makeImage()
    }
}
