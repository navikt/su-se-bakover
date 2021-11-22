package no.nav.su.se.bakover.web.services.avstemming

import arrow.core.Either
import arrow.core.firstOrNone
import no.nav.su.se.bakover.common.ApplicationConfig
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.domain.nais.LeaderPodLookup
import no.nav.su.se.bakover.service.avstemming.AvstemmingService
import no.nav.su.se.bakover.web.services.erLeaderPod
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.time.Clock
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.concurrent.fixedRateTimer

internal class KonsistensavstemmingJob(
    private val avstemmingService: AvstemmingService,
    private val leaderPodLookup: LeaderPodLookup,
    private val jobConfig: ApplicationConfig.JobConfig.Konsistensavstemming,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val jobName = this::class.simpleName!!

    // Kjører hver fjerde time for å være rimelig sikker på at jobben faktisk blir kjørt
    private val periode = Duration.of(4, ChronoUnit.HOURS).toMillis()
    private val initialDelay = Duration.of(5, ChronoUnit.MINUTES).toMillis()

    fun schedule() {
        log.info("Schedulerer jobb for konsistensavstemming med start om: $initialDelay ms, intervall: $periode ms")
        fixedRateTimer(
            name = jobName,
            daemon = true,
            initialDelay = initialDelay,
            period = periode,
        ) {
            Konsistensavstemming(
                avstemmingService = avstemmingService,
                leaderPodLookup = leaderPodLookup,
                jobName = jobName,
                jobConfig = jobConfig,
                clock = clock,
            ).run()
        }
    }

    class Konsistensavstemming(
        val avstemmingService: AvstemmingService,
        val leaderPodLookup: LeaderPodLookup,
        val jobName: String,
        val jobConfig: ApplicationConfig.JobConfig.Konsistensavstemming,
        val clock: Clock,
    ) {
        private val log = LoggerFactory.getLogger(this::class.java)

        fun run() {
            Either.catch {
                val idag = idag(clock.withZone(zoneIdOslo))
                jobConfig.kjøreplan.firstOrNone { it == idag }
                    .fold(
                        {
                            log.info("Kjøreplan: ${jobConfig.kjøreplan} inneholder ikke dato: $idag, hopper over konsistensavstemming.")
                        },
                        {
                            if (leaderPodLookup.erLeaderPod()) {
                                if (!avstemmingService.konsistensavstemmingUtførtForOgPåDato(idag)) {
                                    MDC.put("X-Correlation-ID", UUID.randomUUID().toString())
                                    log.info("Kjøreplan: ${jobConfig.kjøreplan} inneholder dato: $idag, utfører konsistensavstemming.")
                                    avstemmingService.konsistensavstemming(idag)
                                        .fold(
                                            { log.error("$jobName feilet: $it") },
                                            { log.info("$jobName fullført. Detaljer: id:${it.id}, løpendeFraOgMed:${it.løpendeFraOgMed}, opprettetTilOgMed:${it.opprettetTilOgMed}") },
                                        )
                                } else {
                                    log.info("Konsistensavstemming allerede utført for dato: $idag")
                                }
                            }
                        },
                    )
            }.mapLeft {
                log.error("$jobName' feilet med stacktrace:", it)
            }
        }
    }
}
