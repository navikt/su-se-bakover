package no.nav.su.se.bakover.web.services.avstemming

import arrow.core.firstOrNone
import no.nav.su.se.bakover.common.domain.job.JobbResultat
import no.nav.su.se.bakover.common.domain.tid.idag
import no.nav.su.se.bakover.common.domain.tid.zoneIdOslo
import no.nav.su.se.bakover.common.infrastructure.job.RunCheckFactory
import no.nav.su.se.bakover.common.infrastructure.job.StoppableJob
import no.nav.su.se.bakover.common.infrastructure.job.startStoppableJobMedResultat
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
            varsleOmTomKjøreplan: Boolean,
        ): KonsistensavstemmingJob {
            val log = LoggerFactory.getLogger(KonsistensavstemmingJob::class.java)

            val jobName = KonsistensavstemmingJob::class.simpleName!!
            return startStoppableJobMedResultat(
                jobName = jobName,
                initialDelay = initialDelay,
                intervall = periode,
                log = log,
                runJobCheck = listOf(runCheckFactory.leaderPod(), runCheckFactory.manTilFredag0600til2100()),
            ) {
                run(
                    avstemmingService = avstemmingService,
                    jobName = jobName,
                    kjøreplan = kjøreplan,
                    clock = clock,
                    log = log,
                    varsleOmTomKjøreplan = varsleOmTomKjøreplan,
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
            varsleOmTomKjøreplan: Boolean = false,
        ): JobbResultat {
            val feil = mutableListOf<String>()
            val idag = idag(clock.withZone(zoneIdOslo))
            val varslingsdato = idag.plusMonths(2)
            val sistePlanlagteDato = kjøreplan.maxOrNull()
            if (sistePlanlagteDato == null && varsleOmTomKjøreplan) {
                log.error("Kjøreplanen for konsistensavstemming er tom. Nye datoer må hentes fra økonomiområdet.")
            } else if (sistePlanlagteDato != null && sistePlanlagteDato.isBefore(varslingsdato)) {
                val melding =
                    "Kjøreplanen for konsistensavstemming har ingen datoer på eller etter $varslingsdato. " +
                        "Siste planlagte dato er $sistePlanlagteDato. Nye datoer må hentes fra økonomiområdet."
                log.error(melding)
            }
            kjøreplan.firstOrNone { it == idag }
                .fold(
                    {
                        log.info("Kjøreplan: $kjøreplan inneholder ikke dato: $idag, hopper over konsistensavstemming.")
                    },
                    {
                        Fagområde.entries.forEach { fagområde ->
                            if (!avstemmingService.konsistensavstemmingUtførtForOgPåDato(
                                    idag,
                                    fagområde,
                                )
                            ) {
                                log.info("Kjøreplan: $kjøreplan inneholder dato: $idag, utfører konsistensavstemming.")
                                avstemmingService.konsistensavstemming(idag, fagområde)
                                    .fold(
                                        {
                                            feil.add("$jobName feilet for $fagområde: $it")
                                            log.error("$jobName feilet: $it")
                                        },
                                        { log.info("$jobName fullført. Detaljer: id:${it.id}, løpendeFraOgMed:${it.løpendeFraOgMed}, opprettetTilOgMed:${it.opprettetTilOgMed}") },
                                    )
                            } else {
                                log.info("Konsistensavstemming allerede utført for dato: $idag")
                            }
                        }
                    },
                )
            return if (feil.isEmpty()) JobbResultat.Ok else JobbResultat.DelvisFeilet(feil.joinToString("; "))
        }
    }
}
