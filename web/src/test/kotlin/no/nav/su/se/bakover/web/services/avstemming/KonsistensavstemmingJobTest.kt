package no.nav.su.se.bakover.web.services.avstemming

import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.common.infrastructure.job.RunCheckFactory
import no.nav.su.se.bakover.common.nais.LeaderPodLookupFeil
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemming
import no.nav.su.se.bakover.service.avstemming.AvstemmingService
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import økonomi.domain.Fagområde
import java.time.Duration
import java.time.LocalDate

/**
 * TODO jah: Disse starter de faktiske jobbene. Når de kjøres som en test-suite kan vi få potensielle timing issues.
 *   En mulighet kan være å fake fixedRateTimer slik at den kjører synkront, men vi kan se på det hvis disse begynner feile.
 */
internal class KonsistensavstemmingJobTest {

    @Test
    fun `eksekverer ikke hvis ikke kjøreplan ikke inneholder dagens dato`() {
        val avstemmingService = mock<AvstemmingService>()
        KonsistensavstemmingJob.startJob(
            avstemmingService = avstemmingService,
            kjøreplan = emptySet(),
            clock = fixedClock,
            runCheckFactory = RunCheckFactory(
                leaderPodLookup = mock {
                    on { amITheLeader(any()) } doReturn true.right()
                },
                applicationConfig = ApplicationConfig.createLocalConfig(),
                clock = fixedClock,
            ),
            periode = Duration.ofDays(1),
            initialDelay = Duration.ZERO,
        ).also {
            Thread.sleep(1000)
            verifyNoInteractions(avstemmingService)
            it.stop()
        }
    }

    @Test
    fun `eksekveres kun hvis pod er leader`() {
        val avstemmingService = mock<AvstemmingService>()
        KonsistensavstemmingJob.startJob(
            avstemmingService = avstemmingService,
            kjøreplan = setOf(LocalDate.now(fixedClock)),
            clock = fixedClock,
            runCheckFactory = RunCheckFactory(
                leaderPodLookup = mock {
                    on { amITheLeader(any()) } doReturn false.right()
                },
                applicationConfig = ApplicationConfig.createLocalConfig(),
                clock = fixedClock,
            ),
            periode = Duration.ofDays(1),
            initialDelay = Duration.ZERO,
        ).also {
            Thread.sleep(1000)
            verifyNoInteractions(avstemmingService)
            it.stop()
        }
    }

    @Test
    fun `eksekveres ikke hvis allerede utført for aktuell dato`() {
        val avstemmingService = mock<AvstemmingService> {
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
        }
        KonsistensavstemmingJob.startJob(
            avstemmingService = avstemmingService,
            kjøreplan = setOf(LocalDate.now(fixedClock)),
            clock = fixedClock,
            runCheckFactory = RunCheckFactory(
                leaderPodLookup = mock {
                    on { amITheLeader(any()) } doReturn true.right()
                },
                applicationConfig = ApplicationConfig.createLocalConfig(),
                clock = fixedClock,
            ),
            periode = Duration.ofDays(1),
            initialDelay = Duration.ZERO,
        ).also {
            verify(avstemmingService, timeout(1000L)).konsistensavstemmingUtførtForOgPåDato(
                LocalDate.now(fixedClock),
                Fagområde.SUUFORE,
            )
            verifyNoMoreInteractions(avstemmingService)
            it.stop()
        }
    }

    @Test
    fun `eksekveres hvis enda ikke utført for aktuell dato`() {
        val avstemmingService = mock<AvstemmingService> {
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
        }
        KonsistensavstemmingJob.startJob(
            avstemmingService = avstemmingService,
            kjøreplan = setOf(LocalDate.now(fixedClock)),
            clock = fixedClock,
            runCheckFactory = RunCheckFactory(
                leaderPodLookup = mock {
                    on { amITheLeader(any()) } doReturn true.right()
                },
                applicationConfig = ApplicationConfig.createLocalConfig(),
                clock = fixedClock,
            ),
            periode = Duration.ofDays(1),
            initialDelay = Duration.ZERO,
        ).also {
            verify(avstemmingService, timeout(1000L)).konsistensavstemmingUtførtForOgPåDato(
                LocalDate.now(fixedClock),
                Fagområde.SUUFORE,
            )
            verify(avstemmingService, timeout(10L)).konsistensavstemming(LocalDate.now(fixedClock), Fagområde.SUUFORE)
            verifyNoMoreInteractions(avstemmingService)
            it.stop()
        }
    }

    @Test
    fun `eksekverer ikke hvis leader-election svarer med feil`() {
        val avstemmingService = mock<AvstemmingService>()
        KonsistensavstemmingJob.startJob(
            avstemmingService = avstemmingService,
            kjøreplan = setOf(LocalDate.now(fixedClock)),
            clock = fixedClock,
            runCheckFactory = RunCheckFactory(
                leaderPodLookup = mock {
                    on { amITheLeader(any()) } doReturn LeaderPodLookupFeil.UkjentSvarFraLeaderElectorContainer.left()
                },
                applicationConfig = ApplicationConfig.createLocalConfig(),
                clock = fixedClock,
            ),
            periode = Duration.ofDays(1),
            initialDelay = Duration.ZERO,
        ).also {
            Thread.sleep(100)
            verifyNoInteractions(avstemmingService)
            it.stop()
        }
    }
}
