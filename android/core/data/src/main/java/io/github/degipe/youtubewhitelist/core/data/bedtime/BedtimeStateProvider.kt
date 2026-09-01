package io.github.degipe.youtubewhitelist.core.data.bedtime

import io.github.degipe.youtubewhitelist.core.data.repository.KidProfileRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/** Clock seam so the evaluation can be tested without waiting for real time. */
interface BedtimeClock {
    fun now(): LocalDateTime
    fun epochMillis(): Long
}

@Singleton
class SystemBedtimeClock @Inject constructor() : BedtimeClock {
    override fun now(): LocalDateTime = LocalDateTime.now()
    override fun epochMillis(): Long = System.currentTimeMillis()
}

interface BedtimeStateProvider {
    fun observe(profileId: String): Flow<BedtimeState>
}

/**
 * Emits the quiet hours state of a profile. Between two state changes the flow
 * sleeps until the next boundary instead of ticking every second.
 */
@Singleton
class BedtimeStateProviderImpl @Inject constructor(
    private val kidProfileRepository: KidProfileRepository,
    private val clock: BedtimeClock
) : BedtimeStateProvider {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observe(profileId: String): Flow<BedtimeState> =
        kidProfileRepository.getProfileById(profileId)
            .map { profile -> profile?.bedtime }
            .distinctUntilChanged()
            .flatMapLatest { settings ->
                if (settings == null) {
                    flow { emit(BedtimeState.Off) }
                } else {
                    flow {
                        while (true) {
                            val now = clock.now()
                            emit(BedtimeEvaluator.evaluate(settings, now, clock.epochMillis()))
                            val minutes = BedtimeEvaluator.minutesUntilNextChange(settings, now)
                            delay(minutes * 60_000L)
                        }
                    }
                }
            }
            .distinctUntilChanged()
}
