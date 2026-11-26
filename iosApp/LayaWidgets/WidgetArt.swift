import CoreImage
import CoreImage.CIFilterBuiltins
import UIKit

/// Android's MediaWidgetArt on Core Image: the same pictures from the same numbers, so the widgets look alike.
///
/// - `media`: the cover filling the widget, its bottom dissolving into a blur for the transport, under a scrim.
///   Four progressively blurrier copies drawn back over it, each masked by a gradient starting lower than the last,
///   so the blur accelerates downwards and its start can't be seen.
/// - `body`: the cover blurred until only its colours are left, darkened for white text (the 4×1 and library widgets).
enum WidgetArt {
    // MediaWidgetArt's tuning, unchanged.
    static let blurRegionScale: CGFloat = 2
    static let blurSigmas: [CGFloat] = [0.035, 0.070, 0.110, 0.155]
    static let stops: [CGFloat] = [0.28, 0.50, 0.70, 0.86]
    static let stopFeather: CGFloat = 0.26
    static let scrimScale: CGFloat = 1.2
    /// pill(): three passes of a box radius a tenth of a 64 px working image, as a Gaussian sigma of its width.
    static let bodySigmaOfWidth: CGFloat = sqrt(3 * (6 * 6 + 6) / 3) / 64
    static let bodyAlpha: CGFloat = 210 / 255

    /// Android's placeholder: what stands in for a cover there isn't one of.
    static let placeholder: [(UIColor, CGFloat)] = [
        (UIColor(rgb: 0x2E3446), 0), (UIColor(rgb: 0x1B2130), 0.45), (UIColor(rgb: 0x07090E), 1),
    ]

    private static let context = CIContext(options: [.cacheIntermediates: false])

    /// The cover at [size] points with the transport band's blur and scrim, [band] points tall.
    static func media(cover: UIImage?, size: CGSize, band: CGFloat, scale: CGFloat) -> UIImage? {
        let w = size.width * scale, h = size.height * scale, bandPx = band * scale
        let rect = CGRect(x: 0, y: 0, width: w, height: h)
        var image = cover.flatMap { filled($0, rect) } ?? gradient(rect, placeholder)
        let region = min(bandPx * blurRegionScale, h)
        let top = h - region // measured from the top, as Android draws
        let base = image.clampedToExtent()
        for (i, sigma) in blurSigmas.enumerated() {
            let blurred = base.applyingGaussianBlur(sigma: Double(sigma * bandPx)).cropped(to: rect)
            let start = top + stops[i] * region
            let end = max(top + min(stops[i] + stopFeather, 1) * region, start + 1)
            image = blurred.applyingFilter("CIBlendWithMask", parameters: [
                kCIInputBackgroundImageKey: image,
                kCIInputMaskImageKey: verticalAlpha(rect, from: start, to: end),
            ])
        }
        let scrimTop = max(h - bandPx * scrimScale, 0)
        image = gradient(CGRect(x: 0, y: 0, width: w, height: h - scrimTop), [
            (.clear, 0), (UIColor(white: 0, alpha: 0x40 / 255), 0.45), (UIColor(white: 0, alpha: 0xB8 / 255), 1),
        ]).composited(over: image)
        return render(image, rect, scale)
    }

    /// The cover blurred to colour at [size], darkened top to bottom, over the stand-in dark the Android widget's
    /// translucent body shows when the wallpaper behind it is black.
    static func body(cover: UIImage?, size: CGSize, scale: CGFloat) -> UIImage? {
        let w = size.width * scale, h = size.height * scale
        let rect = CGRect(x: 0, y: 0, width: w, height: h)
        var image: CIImage
        if let cover, let fill = filled(cover, rect) {
            image = fill.clampedToExtent().applyingGaussianBlur(sigma: Double(bodySigmaOfWidth * w)).cropped(to: rect)
        } else {
            image = gradient(rect, placeholder)
        }
        image = gradient(rect, [(UIColor(white: 0, alpha: 0x40 / 255), 0), (UIColor(white: 0, alpha: 0x70 / 255), 1)])
            .composited(over: image)
        image = image.applyingFilter("CIColorMatrix", parameters: ["inputAVector": CIVector(x: 0, y: 0, z: 0, w: bodyAlpha)])
            .composited(over: CIImage(color: CIColor(color: UIColor(rgb: 0x141619))).cropped(to: rect))
        return render(image, rect, scale)
    }

    static func poster(cover: UIImage?) -> UIImage? {
        guard let cover, let cg = cover.cgImage else { return nil }
        return liquidWash(cg, accent: liquidAccent(from: cover))
    }

    /// Liquid Artwork, worked out on the CPU in plain 8-bit sRGB, as Android's ColorMediaWidget.colorSurface does:
    /// the cover blurred on a 96 px grid (three box passes, radius 11 = 0.115 of the side), saturated 1.6x and
    /// taken down 1.5 EV, then the accent glow from the top and the shade at the foot.
    ///
    /// Not Core Image. Through CIContext the blurred-cover layer came out empty in Laya's extension (the same code
    /// works in BitChord's), so only the two gradients were left over the system's own widget background: grey in
    /// one build, white in light mode and dark in dark mode in the next. Every pixel here is opaque.
    static func liquidWash(_ cover: CGImage, accent: UIColor) -> UIImage? {
        let n = 96
        guard let srgb = CGColorSpace(name: CGColorSpace.sRGB) else { return nil }
        var px = [UInt8](repeating: 0, count: n * n * 4)
        let ok: Bool = px.withUnsafeMutableBytes { raw in
            guard let small = CGContext(data: raw.baseAddress, width: n, height: n, bitsPerComponent: 8, bytesPerRow: n * 4,
                                        space: srgb, bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue) else { return false }
            small.setFillColor(CGColor(srgbRed: 70 / 255, green: 75 / 255, blue: 80 / 255, alpha: 1))
            small.fill(CGRect(x: 0, y: 0, width: n, height: n))
            small.interpolationQuality = .high
            small.draw(cover, in: CGRect(x: 0, y: 0, width: n, height: n))
            return true
        }
        guard ok else { return nil }
        var channels = (0..<3).map { c in (0..<(n * n)).map { Int(px[$0 * 4 + c]) } }
        for c in 0..<3 { for _ in 0..<3 { channels[c] = boxBlur(channels[c], n: n, radius: 11) } }
        let gain = pow(2.0, -1.5)
        for i in 0..<(n * n) {
            let r = Double(channels[0][i]) / 255, g = Double(channels[1][i]) / 255, b = Double(channels[2][i]) / 255
            let gray = r * 0.2126 + g * 0.7152 + b * 0.0722
            for (c, v) in [r, g, b].enumerated() {
                let saturated = min(max(gray + (v - gray) * 1.6, 0), 1)
                px[i * 4 + c] = UInt8(min(max((pow(pow(saturated, 2.2) * gain, 1 / 2.2) * 255).rounded(), 0), 255))
            }
            px[i * 4 + 3] = 255
        }
        guard let provider = CGDataProvider(data: Data(px) as CFData),
              let base = CGImage(width: n, height: n, bitsPerComponent: 8, bitsPerPixel: 32, bytesPerRow: n * 4, space: srgb,
                                 bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.noneSkipLast.rawValue), provider: provider,
                                 decode: nil, shouldInterpolate: true, intent: .defaultIntent) else { return nil }

        let side = 360
        let size = CGFloat(side)
        guard let out = CGContext(data: nil, width: side, height: side, bitsPerComponent: 8, bytesPerRow: side * 4,
                                  space: srgb, bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue) else { return nil }
        out.interpolationQuality = .high
        out.draw(base, in: CGRect(x: 0, y: 0, width: size, height: size))
        // Core Graphics' origin is the bottom left: the top of the card is y = size.
        var ar: CGFloat = 0, ag: CGFloat = 0, ab: CGFloat = 0, aa: CGFloat = 0
        accent.getRed(&ar, green: &ag, blue: &ab, alpha: &aa)
        if aa > 0, let glow = CGGradient(colorsSpace: srgb, colors: [CGColor(srgbRed: ar, green: ag, blue: ab, alpha: 0.7),
                                                                    CGColor(srgbRed: ar, green: ag, blue: ab, alpha: 0)] as CFArray,
                                       locations: [0, 1]) {
            let center = CGPoint(x: size * 0.58, y: size)
            out.drawRadialGradient(glow, startCenter: center, startRadius: 0, endCenter: center, endRadius: size * 0.82,
                                   options: .drawsBeforeStartLocation)
        }
        if let shade = CGGradient(colorsSpace: srgb, colors: [CGColor(srgbRed: 0, green: 0, blue: 0, alpha: 0.25),
                                                             CGColor(srgbRed: 0, green: 0, blue: 0, alpha: 0)] as CFArray,
                                  locations: [0, 1]) {
            let center = CGPoint(x: size * 0.54, y: -size * 0.02)
            out.drawRadialGradient(shade, startCenter: center, startRadius: 0, endCenter: center, endRadius: size * 0.80,
                                   options: .drawsBeforeStartLocation)
        }
        return out.makeImage().map { UIImage(cgImage: $0) }
    }

    /// One box pass across each row and then each column, edges clamped (Android's MediaWidgetArt.boxPass).
    private static func boxBlur(_ src: [Int], n: Int, radius: Int) -> [Int] {
        let window = radius * 2 + 1
        var rows = [Int](repeating: 0, count: n * n)
        for y in 0..<n {
            var sum = 0
            for i in -radius...radius { sum += src[y * n + min(max(i, 0), n - 1)] }
            for x in 0..<n {
                rows[y * n + x] = sum / window
                sum += src[y * n + min(x + radius + 1, n - 1)] - src[y * n + max(x - radius, 0)]
            }
        }
        var cols = [Int](repeating: 0, count: n * n)
        for x in 0..<n {
            var sum = 0
            for i in -radius...radius { sum += rows[min(max(i, 0), n - 1) * n + x] }
            for y in 0..<n {
                cols[y * n + x] = sum / window
                sum += rows[min(y + radius + 1, n - 1) * n + x] - rows[max(y - radius, 0) * n + x]
            }
        }
        return cols
    }

    private static func liquidAccent(from image: UIImage) -> UIColor {
        let side = 32
        var bytes = [UInt8](repeating: 0, count: side * side * 4)
        guard let cg = image.cgImage, let ctx = CGContext(data: &bytes, width: side, height: side,
            bitsPerComponent: 8, bytesPerRow: side * 4, space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { return .clear }
        ctx.draw(cg, in: CGRect(x: 0, y: 0, width: side, height: side))
        let count = 24
        var weights = [CGFloat](repeating: 0, count: count)
        var hues = weights
        for index in stride(from: 0, to: bytes.count, by: 4) {
            var h: CGFloat = 0, s: CGFloat = 0, b: CGFloat = 0
            UIColor(red: CGFloat(bytes[index]) / 255, green: CGFloat(bytes[index + 1]) / 255,
                    blue: CGFloat(bytes[index + 2]) / 255, alpha: 1).getHue(&h, saturation: &s, brightness: &b, alpha: nil)
            guard s > 0.35, b > 0.2 else { continue }
            let bin = min(Int(h * CGFloat(count)), count - 1)
            let weight = s * s * b
            weights[bin] += weight
            hues[bin] += h * weight
        }
        guard let dominant = weights.indices.max(by: { weights[$0] < weights[$1] }), weights[dominant] > 0 else { return .clear }
        let secondary = weights.indices.filter {
            let distance = abs($0 - dominant)
            return min(distance, count - distance) > 2
        }.max(by: { weights[$0] < weights[$1] })
        guard let secondary, weights[secondary] > weights[dominant] * 0.06 else { return .clear }
        return UIColor(hue: hues[secondary] / weights[secondary], saturation: 0.48, brightness: 0.52, alpha: 1)
    }

    /// [src] centre-cropped to fill [rect] (fillCentreCropped).
    private static func filled(_ src: UIImage, _ rect: CGRect) -> CIImage? {
        guard let cg = src.cgImage else { return nil }
        let img = CIImage(cgImage: cg)
        let s = max(rect.width / img.extent.width, rect.height / img.extent.height)
        let scaled = img.transformed(by: CGAffineTransform(scaleX: s, y: s))
        let dx = (scaled.extent.width - rect.width) / 2, dy = (scaled.extent.height - rect.height) / 2
        return scaled.transformed(by: CGAffineTransform(translationX: -dx - scaled.extent.minX, y: -dy - scaled.extent.minY)).cropped(to: rect)
    }

    /// A vertical gradient filling [rect] from its top, with stops as fractions of its height: one gradient with all
    /// its stops, as Android's LinearGradient. Its y runs down here.
    private static func gradient(_ rect: CGRect, _ stops: [(UIColor, CGFloat)]) -> CIImage {
        let w = max(Int(rect.width.rounded()), 1), h = max(Int(rect.height.rounded()), 1)
        let space = CGColorSpaceCreateDeviceRGB()
        guard let ctx = CGContext(data: nil, width: 1, height: h, bitsPerComponent: 8, bytesPerRow: 0, space: space,
                                  bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue),
              let g = CGGradient(colorsSpace: space, colors: stops.map { $0.0.cgColor } as CFArray, locations: stops.map { $0.1 }) else {
            return CIImage.empty()
        }
        // Bitmap contexts' y runs up: from its top (y = h) to its bottom.
        ctx.drawLinearGradient(g, start: CGPoint(x: 0, y: h), end: .zero, options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
        guard let column = ctx.makeImage() else { return CIImage.empty() }
        // Clamped first: stretched alone, the column's edge pixels blend with the nothing beside them.
        return CIImage(cgImage: column).clampedToExtent()
            .transformed(by: CGAffineTransform(scaleX: CGFloat(w), y: 1).translatedBy(x: rect.minX / CGFloat(w), y: rect.minY))
            .cropped(to: rect)
    }

    /// Alpha 0 above [from] and 1 below [to], both measured from the top of [rect]: a level's mask.
    private static func verticalAlpha(_ rect: CGRect, from: CGFloat, to: CGFloat) -> CIImage {
        let g = CIFilter.linearGradient()
        g.point0 = CGPoint(x: 0, y: rect.height - from); g.point1 = CGPoint(x: 0, y: rect.height - to)
        g.color0 = CIColor(red: 0, green: 0, blue: 0, alpha: 0); g.color1 = CIColor(red: 1, green: 1, blue: 1, alpha: 1)
        return g.outputImage!.cropped(to: rect)
    }

    private static func render(_ image: CIImage, _ rect: CGRect, _ scale: CGFloat) -> UIImage? {
        guard let cg = context.createCGImage(image, from: rect) else { return nil }
        return UIImage(cgImage: cg, scale: scale, orientation: .up)
    }
}

extension UIColor {
    convenience init(rgb: Int, alpha: CGFloat = 1) {
        self.init(red: CGFloat((rgb >> 16) & 0xFF) / 255, green: CGFloat((rgb >> 8) & 0xFF) / 255, blue: CGFloat(rgb & 0xFF) / 255, alpha: alpha)
    }
}
