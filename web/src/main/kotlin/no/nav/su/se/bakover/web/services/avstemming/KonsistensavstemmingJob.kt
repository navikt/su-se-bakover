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
                )
            }.let {
                KonsistensavstemmingJob(it)
            }
        }

        /*
            Grunnen til at vi ikke varsler tidligere enn etter siste kjøring er at vi vanligvis får ny liste av økonomi i desember
            og i desember kjøres ingen avstemming tydeligvis så da slipper vi å spamme ned loggene unødvendig
         */
        private fun varsleHvisSisteMånedMedKjøredatoEllerSenere(kjøreplan: Set<LocalDate>, log: Logger, idag: LocalDate) {
            val sistePlanlagteDato = kjøreplan.max()
            if (!idag.isAfter(sistePlanlagteDato)) {
                log.error("Kjøreplan: $kjøreplan inneholder ikke dato etter: $idag, siste planlagte dato er: $sistePlanlagteDato. Konsistensavstemming vil ikke bli utført fremover.")
            }
        }

        fun run(
            avstemmingService: AvstemmingService,
            jobName: String,
            kjøreplan: Set<LocalDate>,
            clock: Clock,
            log: Logger,
        ): JobbResultat {
            val feil = mutableListOf<String>()
            val idag = idag(clock.withZone(zoneIdOslo))
            varsleHvisSisteMånedMedKjøredatoEllerSenere(kjøreplan, log, idag)
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
