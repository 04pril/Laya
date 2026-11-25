import CoreGraphics

@main struct AdaptiveContentBoundsTests {
    static func main() {
        let folded = CGRect(x: 0, y: 0, width: 418, height: 912)
        precondition(AdaptiveContentBounds.frame(in: folded, safeFrame: folded.insetBy(dx: 20, dy: 40), isPhone: true) == folded)
        let tablet = CGRect(x: 0, y: 0, width: 1280, height: 960)
        precondition(AdaptiveContentBounds.frame(in: tablet, safeFrame: tablet.insetBy(dx: 20, dy: 24), isPhone: false) == tablet)
        for (bounds, safe) in [
            (CGRect(x: 0, y: 0, width: 699, height: 1017), CGRect(x: 0, y: 0, width: 573, height: 983)),
            (CGRect(x: 0, y: 0, width: 1017, height: 699), CGRect(x: 0, y: 0, width: 891, height: 665)),
            (CGRect(x: 0, y: 0, width: 1004, height: 1427), CGRect(x: 0, y: 120, width: 1004, height: 1273)),
            (CGRect(x: 0, y: 0, width: 1427, height: 1004), CGRect(x: 0, y: 0, width: 1301, height: 970)),
        ] {
            precondition(AdaptiveContentBounds.frame(in: bounds, safeFrame: safe, isPhone: true) == safe)
            // Returning to a full safe rectangle must not keep the previous inset.
            precondition(AdaptiveContentBounds.frame(in: bounds, safeFrame: bounds, isPhone: true) == bounds)
        }
        precondition(AdaptiveContentBounds.frame(in: tablet, safeFrame: .null, isPhone: true) == tablet)
        precondition(AdaptiveContentBounds.frame(in: tablet, safeFrame: .zero, isPhone: true) == tablet)
        print("PASS: expanded-phone viewport adapts to changing system regions; compact phones and iPad retain edge-to-edge content")
    }
}
