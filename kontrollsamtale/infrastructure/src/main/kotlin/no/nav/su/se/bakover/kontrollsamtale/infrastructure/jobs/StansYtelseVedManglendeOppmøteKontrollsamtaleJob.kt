package no.nav.su.se.bakover.kontrollsamtale.infrastructure.jobs

import no.nav.su.se.bakover.common.infrastructure.job.RunCheckFactory
import no.nav.su.se.bakover.common.infrastructure.job.StoppableJob
import no.nav.su.se.bakover.common.infrastructure.job.startStoppableJob
import no.nav.su.se.bakover.kontrollsamtale.domain.UtløptFristForKontrollsamtaleService
import org.slf4j.LoggerFactory
import java.time.Duration

class StansYtelseVedManglendeOppmøteKontrollsamtaleJob(
    private val stoppableJob: StoppableJob,
) : StoppableJob by stoppableJob {
    companion object {
        /**
         * @param runCheckFactory `ordinærÅpningstidOppdrag` begrenser jobben til å kjøre i et tidsintervall hvor vi kan forvente at OS/UR er tilgjengelig for å unngå unødvendige feil ved simulering/oversendelse av utbetalinger.
         */
        fun startJob(
            intervall: Duration,
            initialDelay: Duration,
            service: UtløptFristForKontrollsamtaleService,
            runCheckFactory: RunCheckFactory,
        ): StansYtelseVedManglendeOppmøteKontrollsamtaleJob {
            return startStoppableJob(
                jobName = "StansYtelseVedManglendeOppmøteTilKontrollsamtaleJob",
                initialDelay = initialDelay,
                intervall = intervall,
                log = LoggerFactory.getLogger(StansYtelseVedManglendeOppmøteKontrollsamtaleJob::class.java),
                runJobCheck = listOf(runCheckFactory.åpningstidStormaskin(), runCheckFactory.leaderPod()),
            ) {
                service.stansStønadsperioderHvorKontrollsamtaleHarUtløptFrist()
            }.let { StansYtelseVedManglendeOppmøteKontrollsamtaleJob(it) }
        }
    }
}
