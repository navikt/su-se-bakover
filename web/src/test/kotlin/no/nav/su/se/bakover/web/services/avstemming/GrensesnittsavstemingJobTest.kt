package no.nav.su.se.bakover.web.services.avstemming

import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.ApplicationConfig
import no.nav.su.se.bakover.common.infrastructure.nais.LeaderPodLookupFeil
import no.nav.su.se.bakover.common.jobs.infrastructure.RunCheckFactory
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Fagområde
import no.nav.su.se.bakover.test.fixedClock
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions

internal class GrensesnittsavstemingJobTest {
    @Test
    fun `eksekverer ikke hvis ikke leader`() {
        GrensesnittsavstemingJob.Grensesnittsavstemming(
            avstemmingService = mock(),
            jobName = "job",
            runCheckFactory = RunCheckFactory(
                leaderPodLookup = mock {
                    on { amITheLeader(any()) } doReturn false.right()
                },
                applicationConfig = ApplicationConfig.createLocalConfig(),
                clock = fixedClock,
                toggleService = mock(),
            ),
        ).let {
            it.run()
            verifyNoInteractions(it.avstemmingService)
        }
    }

    @Test
    fun `eksekverer hvis leader`() {
        GrensesnittsavstemingJob.Grensesnittsavstemming(
            avstemmingService = mock(),
            jobName = "job",
            runCheckFactory = RunCheckFactory(
                leaderPodLookup = mock {
                    on { amITheLeader(any()) } doReturn true.right()
                },
                applicationConfig = ApplicationConfig.createLocalConfig(),
                clock = fixedClock,
                toggleService = mock(),
            ),
        ).let {
            it.run()
            verify(it.avstemmingService).grensesnittsavstemming(fagområde = Fagområde.SUUFORE)
        }
    }

    @Test
    fun `eksekverer ikke hvis leaderelection svarer med feil`() {
        GrensesnittsavstemingJob.Grensesnittsavstemming(
            avstemmingService = mock(),
            jobName = "job",
            runCheckFactory = RunCheckFactory(
                leaderPodLookup = mock {
                    on { amITheLeader(any()) } doReturn LeaderPodLookupFeil.UkjentSvarFraLeaderElectorContainer.left()
                },
                applicationConfig = ApplicationConfig.createLocalConfig(),
                clock = fixedClock,
                toggleService = mock(),
            ),
        ).let {
            it.run()
            verifyNoInteractions(it.avstemmingService)
        }
    }
}
