import Testing
@testable import sidetube

struct AppConfigTests {
    @Test func versionStringHasShortAndBuild() {
        let v = AppConfig.appVersion
        #expect(v.contains("("))
        #expect(v.hasSuffix(")"))
    }
}
