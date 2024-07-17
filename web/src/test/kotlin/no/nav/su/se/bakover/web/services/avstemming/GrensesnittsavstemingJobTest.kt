package no.nav.su.se.bakover.web.services.avstemming

import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.common.infrastructure.job.RunCheckFactory
import no.nav.su.se.bakover.common.nais.LeaderPodLookupFeil
import no.nav.su.se.bakover.service.avstemming.AvstemmingService
import no.nav.su.se.bakover.test.fixedClock
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
import java.util.Date

/**
 * TODO jah: Disse starter de faktiske jobbene. Når de kjøres som en test-suite kan vi få potensielle timing issues.
 *   En mulighet kan være å fake fixedRateTimer slik at den kjører synkront, men vi kan se på det hvis disse begynner feile.
 */
internal class GrensesnittsavstemingJobTest {
    @Test
    fun `skal ikke kjøre dersom vi ikke er leader`() {
        val avstemmingService = mock<AvstemmingService>()
        GrensesnittsavstemingJob.startJob(
            avstemmingService = avstemmingService,
            runCheckFactory = RunCheckFactory(
                leaderPodLookup = mock {
                    on { amITheLeader(any()) } doReturn false.right()
                },
                applicationConfig = ApplicationConfig.createLocalConfig(),
                clock = fixedClock,
            ),
            periode = Duration.ofDays(1),
            starttidspunkt = Date(),
        ).also {
            Thread.sleep(100)
            verifyNoInteractions(avstemmingService)
            it.stop()
        }
    }

    @Test
    fun `eksekverer hvis leader`() {
        val avstemmingService = mock<AvstemmingService>()
        GrensesnittsavstemingJob.startJob(
            avstemmingService = avstemmingService,
            runCheckFactory = RunCheckFactory(
                leaderPodLookup = mock {
                    on { amITheLeader(any()) } doReturn true.right()
                },
                applicationConfig = ApplicationConfig.createLocalConfig(),
                clock = fixedClock,
            ),
            periode = Duration.ofDays(1),
            starttidspunkt = Date(),
        ).let {
            verify(avstemmingService, timeout(1000)).grensesnittsavstemming(fagområde = Fagområde.SUUFORE)
            verifyNoMoreInteractions(avstemmingService)
            it.stop()
        }
    }

    @Test
    fun `eksekverer ikke hvis leaderelection svarer med feil`() {
        val avstemmingService = mock<AvstemmingService>()
        GrensesnittsavstemingJob.startJob(
            avstemmingService = avstemmingService,
            runCheckFactory = RunCheckFactory(
                leaderPodLookup = mock {
                    on { amITheLeader(any()) } doReturn LeaderPodLookupFeil.UkjentSvarFraLeaderElectorContainer.left()
                },
                applicationConfig = ApplicationConfig.createLocalConfig(),
                clock = fixedClock,
            ),
            periode = Duration.ofDays(1),
            starttidspunkt = Date(),
        ).let {
            Thread.sleep(100)
            verifyNoInteractions(avstemmingService)
            it.stop()
        }
    }
}
