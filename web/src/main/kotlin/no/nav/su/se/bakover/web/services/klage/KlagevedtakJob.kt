package no.nav.su.se.bakover.web.services.klage

import arrow.core.Either
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.domain.klage.Klagevedtak
import no.nav.su.se.bakover.domain.nais.LeaderPodLookup
import no.nav.su.se.bakover.service.klage.KlagevedtakService
import no.nav.su.se.bakover.web.services.erLeaderPod
import org.slf4j.LoggerFactory
import java.net.InetAddress
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.concurrent.fixedRateTimer

class KlagevedtakJob(
    private val klagevedtakService: KlagevedtakService,
    private val leaderPodLookup: LeaderPodLookup,
) {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val jobName = "Håndter utfall fra Klageinstans"
    private val periode = Duration.of(10, ChronoUnit.MINUTES).toMillis()

    private val hostName = InetAddress.getLocalHost().hostName

    companion object {
        fun mapper(id: UUID, json: String): Klagevedtak.Uprosessert {
            val klagevedtak = deserialize<FattetKlagevedtak>(json)

            return Klagevedtak.Uprosessert(
                id = id,
                eventId = klagevedtak.eventId,
                utfall = Klagevedtak.Utfall.valueOf(klagevedtak.utfall),
                klageId = UUID.fromString(klagevedtak.kildeReferanse),
                vedtaksbrevReferanse = klagevedtak.vedtaksbrevReferanse,
            )
        }
    }

    fun schedule() {
        log.info(
            "Starter skeduleringsjobb '$jobName' med intervall: $periode ms. Mitt hostnavn er $hostName. Jeg er ${
            if (leaderPodLookup.erLeaderPod(hostname = hostName)) "" else "ikke "
            }leder.",
        )

        fixedRateTimer(
            name = jobName,
            daemon = true,
            period = periode,
        ) {
            Either.catch {
                if (leaderPodLookup.erLeaderPod(hostname = hostName)) {
                    log.debug("Kjører skeduleringsjobb '$jobName'")
                    klagevedtakService.håndterUtfallFraKlageinstans(::mapper)
                    log.debug("Fullførte skeduleringsjobb '$jobName'")
                }
            }.mapLeft {
                log.error("Skeduleringsjobb '$jobName' feilet med stacktrace:", it)
            }
        }
    }
}
