package no.nav.su.se.bakover.web.services.klage.klageinstans

import arrow.core.Either
import no.nav.su.se.bakover.domain.nais.LeaderPodLookup
import no.nav.su.se.bakover.service.klage.KlageinstanshendelseService
import no.nav.su.se.bakover.web.services.erLeaderPod
import org.slf4j.LoggerFactory
import java.net.InetAddress
import java.time.Duration
import kotlin.concurrent.fixedRateTimer

/*
* Job for å prosessere de meldinger vi får fra Klageinstans
* */
class KlageinstanshendelseJob(
    private val klageinstanshendelseService: KlageinstanshendelseService,
    private val leaderPodLookup: LeaderPodLookup,
    private val initialDelay: Duration,
    private val periode: Duration,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    private val jobName = "Håndter utfall fra Klageinstans"

    private val hostName = InetAddress.getLocalHost().hostName

    fun schedule() {
        log.info("Starter skeduleringsjobb '$jobName' med periode: $periode ms. Mitt hostnavn er $hostName.")

        fixedRateTimer(
            name = jobName,
            daemon = true,
            period = periode.toMillis(),
            initialDelay = initialDelay.toMillis(),
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
