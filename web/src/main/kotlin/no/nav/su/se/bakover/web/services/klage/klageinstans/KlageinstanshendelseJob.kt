package no.nav.su.se.bakover.web.services.klage.klageinstans

import arrow.core.Either
import no.nav.su.se.bakover.domain.nais.LeaderPodLookup
import no.nav.su.se.bakover.service.klage.KlageinstanshendelseService
import no.nav.su.se.bakover.web.services.erLeaderPod
import org.slf4j.LoggerFactory
import java.net.InetAddress
import java.time.Duration
import java.time.temporal.ChronoUnit
import kotlin.concurrent.fixedRateTimer

/*
* Job for å prosessere de meldinger vi får fra Klageinstans
* */
class KlageinstanshendelseJob(
    private val klageinstanshendelseService: KlageinstanshendelseService,
    private val leaderPodLookup: LeaderPodLookup,
    private val initialDelay: Long,
) {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val jobName = "Håndter utfall fra Klageinstans"
    private val periode = Duration.of(10, ChronoUnit.MINUTES).toMillis()

    private val hostName = InetAddress.getLocalHost().hostName

    fun schedule() {
        log.info("Starter skeduleringsjobb '$jobName' med intervall: $periode ms. Mitt hostnavn er $hostName.")

        fixedRateTimer(
            name = jobName,
            daemon = true,
            period = periode,
            initialDelay = initialDelay,
        ) {
            Either.catch {
                if (leaderPodLookup.erLeaderPod(hostname = hostName)) {
                    log.debug("Kjører skeduleringsjobb '$jobName'")
                    klageinstanshendelseService.håndterUtfallFraKlageinstans(KlageinstanshendelseDto::toDomain)
                    log.debug("Fullførte skeduleringsjobb '$jobName'")
                }
            }.mapLeft {
                log.error("Skeduleringsjobb '$jobName' feilet med stacktrace:", it)
            }
        }
    }
}
