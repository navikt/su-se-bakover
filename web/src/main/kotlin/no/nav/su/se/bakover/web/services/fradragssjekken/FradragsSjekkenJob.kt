package no.nav.su.se.bakover.web.services.fradragssjekken

import no.nav.su.se.bakover.common.infrastructure.job.RunCheckFactory
import no.nav.su.se.bakover.common.infrastructure.job.StoppableJob
import no.nav.su.se.bakover.common.infrastructure.job.startStoppableJob
import no.nav.su.se.bakover.common.tid.periode.Måned
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.time.YearMonth

/**
 * Slik som denne er satt opp nå så vil den kjøre tidligst mulig hver måned. [requireGyldigKjøringForMåned]
 * Tar seg av validering om den har lov til å kjøre for måned.
 */

val JULI2026 = YearMonth.of(2026, 7)
internal class FradragsSjekkenJob(
    private val stoppableJob: StoppableJob,
) : StoppableJob by stoppableJob {
    companion object {
        fun startJob(
            initialDelay: Duration,
            periode: Duration,
            fradragsSjekkenService: FradragsjobbenService,
            runJobCheck: RunCheckFactory,
            clock: Clock,
        ): FradragsSjekkenJob {
            val log = LoggerFactory.getLogger(FradragsSjekkenJob::class.java)
            val jobName = FradragsSjekkenJob::class.simpleName!!
            return startStoppableJob(
                jobName = jobName,
                log = log,
                initialDelay = initialDelay,
                intervall = periode,
                runJobCheck = listOf(runJobCheck.manTilFredag0600til2100(), runJobCheck.leaderPod()),
            ) {
                val måned = Måned.now(clock)
                if (!måned.årOgMåned.isAfter(JULI2026)) {
                    log.error("Kjører ikke jobben før {} nå er det {}", JULI2026, måned)
                } else {
                    log.info("Kjører FradragsSjekken {} måned {}", jobName, måned)
                    runCatching {
                        fradragsSjekkenService.sjekkLøpendeSakerForFradragIEksterneSystemer(måned).fold(
                            ifLeft = {
                                log.warn("Kunne ikke kjøre jobben nå fordi: {} vi er ", it)
                            },
                            ifRight = {
                                log.info("FradragsSjekken er fullført")
                            },
                        )
                    }.onFailure {
                        log.error("Feil ved kjøring av FradragsSjekken", it)
                    }
                    log.info("Jobb {} er kjørt", jobName)
                }
            }.let { FradragsSjekkenJob(it) }
        }
    }
}
