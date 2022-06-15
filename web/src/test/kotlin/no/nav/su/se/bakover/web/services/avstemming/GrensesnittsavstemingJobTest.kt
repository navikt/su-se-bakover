package no.nav.su.se.bakover.web.services.avstemming

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.nais.LeaderPodLookupFeil
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Fagområde
import no.nav.su.se.bakover.web.services.erLeaderPod
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
            leaderPodLookup = mock {
                on { amITheLeader(any()) } doReturn false.right()
            },
            jobName = "job",
        ).let {
            it.run()
            verify(it.leaderPodLookup).amITheLeader(any())
            verifyNoInteractions(it.avstemmingService)
            it.leaderPodLookup.erLeaderPod() shouldBe false
        }
    }

    @Test
    fun `eksekverer hvis leader`() {
        GrensesnittsavstemingJob.Grensesnittsavstemming(
            avstemmingService = mock(),
            leaderPodLookup = mock {
                on { amITheLeader(any()) } doReturn true.right()
            },
            jobName = "job",
        ).let {
            it.run()
            verify(it.leaderPodLookup).amITheLeader(any())
            verify(it.avstemmingService).grensesnittsavstemming(fagområde = Fagområde.SUUFORE)
            it.leaderPodLookup.erLeaderPod() shouldBe true
        }
    }

    @Test
    fun `eksekverer ikke hvis leaderelection svarer med feil`() {
        GrensesnittsavstemingJob.Grensesnittsavstemming(
            avstemmingService = mock(),
            leaderPodLookup = mock {
                on { amITheLeader(any()) } doReturn LeaderPodLookupFeil.UkjentSvarFraLeaderElectorContainer.left()
            },
            jobName = "job",
        ).let {
            it.run()
            verify(it.leaderPodLookup).amITheLeader(any())
            verifyNoInteractions(it.avstemmingService)
            it.leaderPodLookup.erLeaderPod() shouldBe false
        }
    }
}
