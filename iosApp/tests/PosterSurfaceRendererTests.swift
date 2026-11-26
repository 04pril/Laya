import AppKit
import CoreGraphics
import Foundation
import ImageIO
import UniformTypeIdentifiers

@main
struct PosterSurfaceRendererTests {
    static let space = CGColorSpace(name: CGColorSpace.displayP3)!
    static func solid(_ colour: CGColor) -> CGImage {
        let context = CGContext(data: nil, width: 32, height: 32, bitsPerComponent: 8, bytesPerRow: 128,
            space: space, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
        context.setFillColor(colour)
        context.fill(CGRect(x: 0, y: 0, width: 32, height: 32))
        return context.makeImage()!
    }
    static func bytes(_ image: CGImage) -> Data { image.dataProvider!.data! as Data }
    static func opaque(_ image: CGImage) {
        precondition(image.bitsPerComponent == 8 && image.bitsPerPixel == 32, "Use fixed standard-range pixels")
        precondition(image.colorSpace?.name == CGColorSpace.displayP3, "Use explicit Display P3")
        let data = bytes(image)
        for y in 0..<image.height {
            for x in 0..<image.width {
                precondition(data[y * image.bytesPerRow + x * 4 + 3] == 255,
                    "No pixel may reveal a light/dark system plate: \(x),\(y)")
            }
        }
    }
    static func save(_ image: CGImage, at path: String) {
        let output = CGImageDestinationCreateWithURL(URL(fileURLWithPath: path) as CFURL, UTType.png.identifier as CFString, 1, nil)!
        CGImageDestinationAddImage(output, image, nil)
        precondition(CGImageDestinationFinalize(output))
    }
    static func load(_ path: String) -> CGImage {
        let source = CGImageSourceCreateWithURL(URL(fileURLWithPath: path) as CFURL, nil)!
        return CGImageSourceCreateImageAtIndex(source, 0, nil)!
    }
    static func main() {
        let cover = solid(CGColor(srgbRed: 0.9, green: 0.25, blue: 0.12, alpha: 1))
        let wash = solid(CGColor(srgbRed: 0.12, green: 0.45, blue: 0.5, alpha: 0.65))
        for (small, size, scale) in [(false, CGSize(width: 338, height: 158), 2.0),
                                    (true, CGSize(width: 158, height: 158), 2.0),
                                    (false, CGSize(width: 338.25, height: 158.25), 1.7)] {
            var reference: Data?
            for mode in [NSAppearance.Name.aqua, .darkAqua] {
                NSAppearance(named: mode)!.performAsCurrentDrawingAppearance {
                    let image = PosterSurfaceRenderer.render(cover: cover, wash: wash, size: size, small: small, scale: scale)!
                    opaque(image)
                    if let reference { precondition(bytes(image) == reference, "Appearance must not alter media pixels") }
                    reference = bytes(image)
                }
            }
        }
        let placeholder = PosterSurfaceRenderer.render(cover: nil, wash: nil, size: CGSize(width: 158, height: 158), small: true, scale: 2)!
        opaque(placeholder)
        precondition(PosterSurfaceRenderer.render(cover: nil, wash: nil, size: .zero, small: true, scale: 2) == nil)
        precondition(PosterSurfaceRenderer.render(cover: nil, wash: nil, size: CGSize(width: 158, height: 158), small: true, scale: 0) == nil)
        let wideRed = CGColor(colorSpace: space, components: [1, 0, 0, 1])!
        let wideWash = PosterSurfaceRenderer.render(cover: nil, wash: solid(wideRed), size: CGSize(width: 32, height: 32), small: true, scale: 1)!
        precondition(bytes(wideWash).prefix(4).elementsEqual([255, 0, 0, 255]), "Preserve a Display P3 primary outside sRGB")
        print("PASS: poster media preserves Display P3 and uses identical opaque 8-bit pixels in light/dark at both sizes and fractional bounds")
        if CommandLine.arguments.count == 4 {
            let cover = load(CommandLine.arguments[1]), wash = load(CommandLine.arguments[2]), directory = CommandLine.arguments[3]
            for (name, small, size) in [("medium", false, CGSize(width: 338, height: 158)), ("small", true, CGSize(width: 158, height: 158))] {
                let image = PosterSurfaceRenderer.render(cover: cover, wash: wash, size: size, small: small, scale: 2)!
                opaque(image)
                save(image, at: directory + "/surface-" + name + ".png")
            }
            print("Saved native compositor proofs (not an iOS WidgetKit screenshot)")
        }
    }
}
