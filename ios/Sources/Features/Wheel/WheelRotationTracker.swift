import Foundation

/// Wandelt fortlaufende Winkel (Grad, 0…360 um den Radmittelpunkt) in Rastschritte um.
/// Positive Schritte = im Uhrzeigersinn. Überlauf bei 360°→0° wird korrigiert; Teilwinkel werden gesammelt.
struct WheelRotationTracker {
    var stepAngle: Double = 24
    private var lastAngle: Double?
    private var accumulated: Double = 0

    init(stepAngle: Double = 24) {
        self.stepAngle = stepAngle
    }

    mutating func begin(at angle: Double) {
        lastAngle = angle
        accumulated = 0
    }

   /// Liefert die seit dem letzten Aufruf vollendeten Rastschritte (kann 0 sein).
    mutating func update(to angle: Double) -> Int {
        guard let previous = lastAngle else {
            begin(at: angle)
            return 0
        }
        var delta = angle - previous
        if delta > 180 { delta -= 360 }
        if delta < -180 { delta += 360 }
        lastAngle = angle
        accumulated += delta
        let steps = Int((accumulated / stepAngle).rounded(.towardZero))
        accumulated -= Double(steps) * stepAngle
        return steps
    }

    mutating func end() {
        lastAngle = nil
        accumulated = 0
    }
}

/// Geometrie des Rads: Mittelknopf, Ring-Segmente.
enum ClickWheelGeometry {
    enum Segment: Equatable { case center, menu, next, playPause, previous }

   /// Winkel in Grad, 0° = rechts, im Uhrzeigersinn (Bildschirmkoordinaten: y nach unten), Bereich 0…360.
    static func angle(of point: CGPoint, center: CGPoint) -> Double {
        let degrees = atan2(point.y - center.y, point.x - center.x) * 180 / .pi
        return degrees < 0 ? degrees + 360 : degrees
    }

    static func segment(at point: CGPoint, center: CGPoint, outerRadius: CGFloat, innerRadius: CGFloat) -> Segment? {
        let distance = hypot(point.x - center.x, point.y - center.y)
        if distance <= innerRadius { return .center }
        guard distance <= outerRadius * 1.05 else { return nil }
        switch angle(of: point, center: center) {
        case 45..<135: return .playPause   // unten
        case 135..<225: return .previous   // links
        case 225..<315: return .menu   // oben
        default: return .next   // rechts
        }
    }
}
