// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import Testing
@testable import sidetube

struct AppConfigTests {
    @Test func versionStringHasShortAndBuild() {
        let v = AppConfig.appVersion
        #expect(v.contains("("))
        #expect(v.hasSuffix(")"))
    }
}
