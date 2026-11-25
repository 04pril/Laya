import CoreGraphics

/// A conservative viewport for expanded phones whose system UI can occupy a side edge.
enum AdaptiveContentBounds {
    static func frame(in bounds: CGRect, safeFrame: CGRect, isPhone: Bool) -> CGRect {
        guard isPhone, min(bounds.width, bounds.height) >= 600,
              [safeFrame.minX, safeFrame.minY, safeFrame.width, safeFrame.height].allSatisfy({ $0.isFinite }) else {
            return bounds
        }
        let clipped = bounds.intersection(safeFrame)
        return clipped.isNull || clipped.isEmpty ? bounds : clipped
    }
}
