// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.kid

import androidx.lifecycle.viewModelScope
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Test
import xyz.steier.sidetube.core.db.KidProfileEntity

/**
 * Android's first kid-mode profile switcher (2026-09-13), built to close the parity gap the
 * child previously always seeing `profiles.firstOrNull()` regardless of which profile a parent
 * had selected before.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KidProfileSwitchTest {
    private val other = KidProfileEntity("q", "Other", bedtimeEnabled = false)

    @Test fun `switching selects the other profile and remembers it`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = KidFixture(otherProfiles = listOf(other))
        val vm = f.model()
        try {
            runCurrent()
            assertThat(vm.state.value.profile?.id).isEqualTo(f.profile.id)
            assertThat(vm.state.value.profiles.map { it.id }).containsExactly(f.profile.id, other.id)

            vm.selectProfile(other.id)
            assertThat(vm.state.value.profile?.id).isEqualTo(other.id)
            assertThat(f.profilePrefs.lastProfileId).isEqualTo(other.id)
        } finally { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }

    @Test fun `a whitelist emission after switching does not revert the selection to the first profile`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = KidFixture(otherProfiles = listOf(other))
        val vm = f.model()
        try {
            runCurrent()
            vm.selectProfile(other.id)
            assertThat(vm.state.value.profile?.id).isEqualTo(other.id)

            f.rejectB(); runCurrent()   // any whitelist-list emission while "other" is active
            assertThat(vm.state.value.profile?.id).isEqualTo(other.id)
        } finally { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }

    @Test fun `a remembered profile id is restored on a fresh ViewModel instance`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = KidFixture(otherProfiles = listOf(other))
        val first = f.model()
        try {
            runCurrent()
            first.selectProfile(other.id)
        } finally { first.viewModelScope.cancel() }

        val relaunched = f.model()
        try {
            runCurrent()
            assertThat(relaunched.state.value.profile?.id).isEqualTo(other.id)
        } finally { relaunched.viewModelScope.cancel(); Dispatchers.resetMain() }
    }
}
