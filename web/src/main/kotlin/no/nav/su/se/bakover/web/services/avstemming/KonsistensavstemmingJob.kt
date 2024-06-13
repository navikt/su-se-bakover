package no.nav.su.se.bakover.web.services.avstemming

import arrow.core.firstOrNone
import no.nav.su.se.bakover.common.domain.tid.idag
import no.nav.su.se.bakover.common.domain.tid.zoneIdOslo
import no.nav.su.se.bakover.common.infrastructure.job.RunCheckFactory
import no.nav.su.se.bakover.common.infrastructure.job.StoppableJob
import no.nav.su.se.bakover.common.infrastructure.job.startStoppableJob
import no.nav.su.se.bakover.service.avstemming.AvstemmingService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import økonomi.domain.Fagområde
import java.time.Clock
import java.time.Duration
import java.time.LocalDate

internal class KonsistensavstemmingJob(
    private val stoppableJob: StoppableJob,
) : StoppableJob by stoppableJob {

    companion object {
        fun startJob(
            avstemmingService: AvstemmingService,
            kjøreplan: Set<LocalDate>,
            initialDelay: Duration,
            periode: Duration,
            clock: Clock,
            runCheckFactory: RunCheckFactory,
        ): KonsistensavstemmingJob {
            val log = LoggerFactory.getLogger(KonsistensavstemmingJob::class.java)

            val jobName = KonsistensavstemmingJob::class.simpleName!!
            return startStoppableJob(
                jobName = jobName,
                initialDelay = initialDelay,
                intervall = periode,
                log = log,
                runJobCheck = listOf(runCheckFactory.leaderPod()),
            ) {
                run(
                    avstemmingService = avstemmingService,
                    jobName = jobName,
                    kjøreplan = kjøreplan,
                    clock = clock,
                    log = log,
                )
            }.let {
                KonsistensavstemmingJob(it)
            }
        }

        fun run(
            avstemmingService: AvstemmingService,
            jobName: String,
            kjøreplan: Set<LocalDate>,
            clock: Clock,
            log: Logger,
        ) {
            val idag = idag(clock.withZone(zoneIdOslo))
            kjøreplan.firstOrNone { it == idag }
                .fold(
                    {
                        log.info("Kjøreplan: $kjøreplan inneholder ikke dato: $idag, hopper over konsistensavstemming.")
                    },
                    {
                        Fagområde.entries.forEach { fagområde ->
                            when (fagområde) {
                                Fagområde.SUALDER -> {
                                    // TODO("simulering_utbetaling_alder legg til ALDER for konsistensavstemming")
                                }

                                Fagområde.SUUFORE -> {
                                    if (!avstemmingService.konsistensavstemmingUtførtForOgPåDato(
                                            idag,
                                            fagområde,
                                        )
                                    ) {
                                        log.info("Kjøreplan: $kjøreplan inneholder dato: $idag, utfører konsistensavstemming.")
                                        avstemmingService.konsistensavstemming(idag, fagområde)
                                            .fold(
                                                { log.error("$jobName feilet: $it") },
                                                { log.info("$jobName fullført. Detaljer: id:${it.id}, løpendeFraOgMed:${it.løpendeFraOgMed}, opprettetTilOgMed:${it.opprettetTilOgMed}") },
                                            )
                                    } else {
                                        log.info("Konsistensavstemming allerede utført for dato: $idag")
                                    }
                                }
                            }
                        }
                    },
                )
        }
    }
}
