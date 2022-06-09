package no.nav.su.se.bakover.web.services.avstemming

import arrow.core.Either
import arrow.core.firstOrNone
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.domain.nais.LeaderPodLookup
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Fagområde
import no.nav.su.se.bakover.service.avstemming.AvstemmingService
import no.nav.su.se.bakover.web.services.erLeaderPod
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.util.UUID
import kotlin.concurrent.fixedRateTimer

/**
 * @param periode Kjører hver fjerde time for å være rimelig sikker på at jobben faktisk blir kjørt
 */
internal class KonsistensavstemmingJob(
    private val avstemmingService: AvstemmingService,
    private val leaderPodLookup: LeaderPodLookup,
    private val kjøreplan: Set<LocalDate>,
    private val initialDelay: Duration,
    private val periode: Duration,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    private val jobName = this::class.simpleName!!

    fun schedule() {
        log.info("Schedulerer jobb for konsistensavstemming med start om: $initialDelay, periode: $periode")
        fixedRateTimer(
            name = jobName,
            daemon = true,
            initialDelay = initialDelay.toMillis(),
            period = periode.toMillis(),
        ) {
            Konsistensavstemming(
                avstemmingService = avstemmingService,
                leaderPodLookup = leaderPodLookup,
                jobName = jobName,
                kjøreplan = kjøreplan,
                clock = clock,
            ).run()
        }
    }

    class Konsistensavstemming(
        val avstemmingService: AvstemmingService,
        val leaderPodLookup: LeaderPodLookup,
        val jobName: String,
        val kjøreplan: Set<LocalDate>,
        val clock: Clock,
    ) {
        private val log = LoggerFactory.getLogger(this::class.java)

        fun run() {
            Either.catch {
                val idag = idag(clock.withZone(zoneIdOslo))
                kjøreplan.firstOrNone { it == idag }
                    .fold(
                        {
                            log.info("Kjøreplan: $kjøreplan inneholder ikke dato: $idag, hopper over konsistensavstemming.")
                        },
                        {
                            if (leaderPodLookup.erLeaderPod()) {
                                Fagområde.values().forEach { fagområde ->
                                    when (fagområde) {
                                        Fagområde.SUALDER -> {
                                            // TODO("simulering_utbetaling_alder legg til ALDER for konsistensavstemming")
                                        }
                                        Fagområde.SUUFORE -> {
                                            if (!avstemmingService.konsistensavstemmingUtførtForOgPåDato(idag, fagområde)) {
                                                MDC.put("X-Correlation-ID", UUID.randomUUID().toString())
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
                            }
                        },
                    )
            }.mapLeft {
                log.error("$jobName' feilet med stacktrace:", it)
            }
        }
    }
}
