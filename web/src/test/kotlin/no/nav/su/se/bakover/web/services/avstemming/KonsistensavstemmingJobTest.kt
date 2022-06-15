package no.nav.su.se.bakover.web.services.avstemming

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.domain.nais.LeaderPodLookupFeil
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemming
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Fagområde
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.web.services.erLeaderPod
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import java.time.LocalDate

internal class KonsistensavstemmingJobTest {

    @Test
    fun `eksekverer ikke hvis ikke kjøreplan ikke inneholder dagens dato`() {
        KonsistensavstemmingJob.Konsistensavstemming(
            avstemmingService = mock(),
            leaderPodLookup = mock {
                on { amITheLeader(any()) } doReturn false.right()
            },
            jobName = "konsistensavstemming",
            kjøreplan = emptySet(),
            clock = fixedClock,
        ).let {
            it.run()
            verifyNoInteractions(it.leaderPodLookup)
            verifyNoInteractions(it.avstemmingService)
        }
    }

    @Test
    fun `eksekveres kun hvis pod er leader`() {
        KonsistensavstemmingJob.Konsistensavstemming(
            avstemmingService = mock(),
            leaderPodLookup = mock {
                on { amITheLeader(any()) } doReturn false.right()
            },
            jobName = "konsistensavstemming",
            kjøreplan = setOf(LocalDate.now(fixedClock)),
            clock = fixedClock,
        ).let {
            it.run()
            verify(it.leaderPodLookup).amITheLeader(any())
            verifyNoInteractions(it.avstemmingService)
            it.leaderPodLookup.erLeaderPod() shouldBe false
        }
    }

    @Test
    fun `eksekveres ikke hvis allerede utført for aktuell dato`() {
        KonsistensavstemmingJob.Konsistensavstemming(
            avstemmingService = mock {
                on { konsistensavstemming(any(), any()) } doReturn Avstemming.Konsistensavstemming.Ny(
                    id = UUID30.randomUUID(),
                    opprettet = fixedTidspunkt,
                    løpendeFraOgMed = fixedTidspunkt,
                    opprettetTilOgMed = fixedTidspunkt,
                    utbetalinger = listOf(),
                    avstemmingXmlRequest = null,
                    fagområde = Fagområde.SUUFORE,
                ).right()
                on { konsistensavstemmingUtførtForOgPåDato(LocalDate.now(fixedClock), Fagområde.SUUFORE) } doReturn true
            },
            leaderPodLookup = mock {
                on { amITheLeader(any()) } doReturn true.right()
            },
            jobName = "konsistensavstemming",
            kjøreplan = setOf(LocalDate.now(fixedClock)),
            clock = fixedClock,
        ).let {
            it.run()
            verify(it.leaderPodLookup).amITheLeader(any())
            verify(it.avstemmingService).konsistensavstemmingUtførtForOgPåDato(LocalDate.now(fixedClock), Fagområde.SUUFORE)
            verifyNoMoreInteractions(it.avstemmingService)
            it.leaderPodLookup.erLeaderPod() shouldBe true
        }
    }

    @Test
    fun `eksekveres hvis enda ikke utført for aktuell dato`() {
        KonsistensavstemmingJob.Konsistensavstemming(
            avstemmingService = mock {
                on { konsistensavstemming(any(), any()) } doReturn Avstemming.Konsistensavstemming.Ny(
                    id = UUID30.randomUUID(),
                    opprettet = fixedTidspunkt,
                    løpendeFraOgMed = fixedTidspunkt,
                    opprettetTilOgMed = fixedTidspunkt,
                    utbetalinger = listOf(),
                    avstemmingXmlRequest = null,
                    fagområde = Fagområde.SUUFORE,
                ).right()
                on { konsistensavstemmingUtførtForOgPåDato(LocalDate.now(fixedClock), Fagområde.SUUFORE) } doReturn false
            },
            leaderPodLookup = mock {
                on { amITheLeader(any()) } doReturn true.right()
            },
            jobName = "konsistensavstemming",
            kjøreplan = setOf(LocalDate.now(fixedClock)),
            clock = fixedClock,
        ).let {
            it.run()
            verify(it.leaderPodLookup).amITheLeader(any())
            verify(it.avstemmingService).konsistensavstemmingUtførtForOgPåDato(LocalDate.now(fixedClock), Fagområde.SUUFORE)
            verify(it.avstemmingService).konsistensavstemming(LocalDate.now(fixedClock), Fagområde.SUUFORE)
            it.leaderPodLookup.erLeaderPod() shouldBe true
        }
    }

    @Test
    fun `eksekverer ikke hvis leader-election svarer med feil`() {
        KonsistensavstemmingJob.Konsistensavstemming(
            avstemmingService = mock(),
            leaderPodLookup = mock {
                on { amITheLeader(any()) } doReturn LeaderPodLookupFeil.UkjentSvarFraLeaderElectorContainer.left()
            },
            jobName = "konsistensavstemming",
            kjøreplan = setOf(LocalDate.now(fixedClock)),
            clock = fixedClock,
        ).let {
            it.run()
            verify(it.leaderPodLookup).amITheLeader(any())
            verifyNoInteractions(it.avstemmingService)
            it.leaderPodLookup.erLeaderPod() shouldBe false
        }
    }
}
