package no.nav.su.se.bakover.web.services.klage.klageinstans

import no.nav.su.se.bakover.common.infrastructure.job.RunCheckFactory
import no.nav.su.se.bakover.common.infrastructure.job.StoppableJob
import no.nav.su.se.bakover.common.infrastructure.job.startStoppableJob
import no.nav.su.se.bakover.service.klage.KlageinstanshendelseService
import org.slf4j.LoggerFactory
import java.time.Duration

/** Job for å prosessere de meldinger vi får fra Klageinstans */
internal class KlageinstanshendelseJob(
    private val stoppableJob: StoppableJob,
) : StoppableJob by stoppableJob {
    companion object {
        fun startJob(
            klageinstanshendelseService: KlageinstanshendelseService,
            initialDelay: Duration,
            periode: Duration,
            runCheckFactory: RunCheckFactory,
        ): KlageinstanshendelseJob {
            startStoppableJob(
                jobName = "Håndter utfall fra Klageinstans",
                initialDelay = initialDelay,
                intervall = periode,
                log = LoggerFactory.getLogger(KlageinstanshendelseJob::class.java),
                runJobCheck = listOf(runCheckFactory.leaderPod()),
            ) {
                klageinstanshendelseService.håndterUtfallFraKlageinstans(KlageinstanshendelseDto::toDomain)
            }.let {
                return KlageinstanshendelseJob(it)
            }
        }
    }
}
