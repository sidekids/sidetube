import Foundation
import Testing
@testable import sidetube

struct WheelRotationTrackerTests {
    @Test func clockwiseStepsAccumulate() {
        var tracker = WheelRotationTracker(stepAngle: 24)
        tracker.begin(at: 10)
        #expect(tracker.update(to: 20) == 0)     // 10° gesammelt
        #expect(tracker.update(to: 40) == 1)     // 30° → 1 Schritt, Rest 6°
        #expect(tracker.update(to: 58) == 1)     // 6+18 = 24 → 1 Schritt, Rest 0
        #expect(tracker.update(to: 130) == 3)    // 72° → 3 Schritte
    }

    @Test func counterClockwiseIsNegative() {
        var tracker = WheelRotationTracker(stepAngle: 24)
        tracker.begin(at: 100)
        #expect(tracker.update(to: 50) == -2)
    }

    @Test func wrapAroundAtZeroDegrees() {
        var tracker = WheelRotationTracker(stepAngle: 24)
        tracker.begin(at: 350)
        #expect(tracker.update(to: 20) == 1)     // +30°, nicht −330°
        tracker.begin(at: 5)
        #expect(tracker.update(to: 340) == -1)   // −25°, nicht +335°
    }

    @Test func firstUpdateWithoutBeginOnlyPrimes() {
        var tracker = WheelRotationTracker()
        #expect(tracker.update(to: 90) == 0)
        #expect(tracker.update(to: 150) == 2)
    }
}

struct ClickWheelGeometryTests {
    let center = CGPoint(x: 100, y: 100)

    @Test func angleIsClockwiseFromRight() {
        #expect(ClickWheelGeometry.angle(of: CGPoint(x: 150, y: 100), center: center) == 0)
        #expect(ClickWheelGeometry.angle(of: CGPoint(x: 100, y: 150), center: center) == 90)   // unten
        #expect(ClickWheelGeometry.angle(of: CGPoint(x: 50, y: 100), center: center) == 180)
        #expect(ClickWheelGeometry.angle(of: CGPoint(x: 100, y: 50), center: center) == 270)   // oben
    }

    @Test func segments() {
        func seg(_ x: CGFloat, _ y: CGFloat) -> ClickWheelGeometry.Segment? {
            ClickWheelGeometry.segment(at: CGPoint(x: x, y: y), center: center, outerRadius: 100, innerRadius: 34)
        }
        #expect(seg(100, 100) == .center)
        #expect(seg(110, 110) == .center)
        #expect(seg(100, 30) == .menu)
        #expect(seg(100, 170) == .playPause)
        #expect(seg(30, 100) == .previous)
        #expect(seg(170, 100) == .next)
        #expect(seg(250, 250) == nil)
    }
}

struct WheelMenuModelTests {
    @Test func movementClampsAtEnds() {
        let model = WheelMenuModel(count: 3)
        model.move(by: -1)
        #expect(model.selectedIndex == 0)
        model.move(by: 5)
        #expect(model.selectedIndex == 2)
        model.move(by: -1)
        #expect(model.selectedIndex == 1)
    }

    @Test func countChangeKeepsIndexValid() {
        let model = WheelMenuModel(count: 5, selectedIndex: 4)
        model.setCount(2)
        #expect(model.selectedIndex == 1)
        model.setCount(0)
        #expect(model.selectedIndex == 0)
        model.move(by: 1)
        #expect(model.selectedIndex == 0)
    }
}
