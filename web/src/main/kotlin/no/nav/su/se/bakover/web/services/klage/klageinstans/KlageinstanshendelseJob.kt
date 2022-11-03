package no.nav.su.se.bakover.web.services.klage.klageinstans

import arrow.core.Either
import no.nav.su.se.bakover.common.CorrelationId.Companion.withCorrelationId
import no.nav.su.se.bakover.service.klage.KlageinstanshendelseService
import no.nav.su.se.bakover.web.services.RunCheckFactory
import no.nav.su.se.bakover.web.services.shouldRun
import org.jetbrains.kotlin.utils.addToStdlib.ifTrue
import org.slf4j.LoggerFactory
import java.time.Duration
import kotlin.concurrent.fixedRateTimer

/*
* Job for å prosessere de meldinger vi får fra Klageinstans
* */
internal class KlageinstanshendelseJob(
    private val klageinstanshendelseService: KlageinstanshendelseService,
    private val initialDelay: Duration,
    private val periode: Duration,
    private val runCheckFactory: RunCheckFactory,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    private val jobName = "Håndter utfall fra Klageinstans"

    fun schedule() {
        log.info("Starter skeduleringsjobb '$jobName' med periode: $periode ms")

        fixedRateTimer(
            name = jobName,
            daemon = true,
            period = periode.toMillis(),
            initialDelay = initialDelay.toMillis(),
        ) {
            Either.catch {
                listOf(runCheckFactory.leaderPod())
                    .shouldRun()
                    .ifTrue {
                        withCorrelationId {
                            log.debug("Kjører skeduleringsjobb '$jobName'")
                            klageinstanshendelseService.håndterUtfallFraKlageinstans(KlageinstanshendelseDto::toDomain)
                            log.debug("Fullførte skeduleringsjobb '$jobName'")
                        }
                    }
            }.mapLeft {
                log.error("Skeduleringsjobb '$jobName' feilet med stacktrace:", it)
            }
        }
    }
}
