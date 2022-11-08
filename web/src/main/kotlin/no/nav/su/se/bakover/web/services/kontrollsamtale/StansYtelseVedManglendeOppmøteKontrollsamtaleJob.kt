package no.nav.su.se.bakover.web.services.kontrollsamtale

import arrow.core.Either
import no.nav.su.se.bakover.common.igår
import no.nav.su.se.bakover.common.jobs.infrastructure.RunCheckFactory
import no.nav.su.se.bakover.common.jobs.infrastructure.shouldRun
import no.nav.su.se.bakover.service.kontrollsamtale.UtløptFristForKontrollsamtaleService
import org.jetbrains.kotlin.utils.addToStdlib.ifTrue
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import kotlin.concurrent.fixedRateTimer

/**
 * @param ordinærÅpningstidOppdrag begrenser jobben til å kjøre i et tidsintervall hvor vi kan forvente at OS/UR er tilgjengelig for å unngå unødvendige feil ved simulering/oversendelse av utbetalinger.
 */
internal class StansYtelseVedManglendeOppmøteKontrollsamtaleJob(
    private val intervall: Duration,
    private val initialDelay: Duration,
    private val service: UtløptFristForKontrollsamtaleService,
    private val clock: Clock,
    private val runCheckFactory: RunCheckFactory,
) {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val jobName = "StansYtelseVedManglendeOppmøteTilKontrollsamtaleJob"

    fun schedule() {
        log.info("Starter skeduleringsjobb '$jobName' med intervall: $intervall.")

        fixedRateTimer(
            name = jobName,
            daemon = true,
            period = intervall.toMillis(),
            initialDelay = initialDelay.toMillis(),
        ) {
            Either.catch {
                listOf(
                    runCheckFactory.åpningstidStormaskin(),
                    runCheckFactory.leaderPod(),
                    runCheckFactory.unleashToggle("supstonad.automatisk.stans.manglende.oppmote.kontrollsamtale"),
                ).shouldRun().ifTrue {
                    service.håndterUtløpsdato(igår(clock))
                }
            }.mapLeft {
                log.error("Skeduleringsjobb '$jobName' feilet med stacktrace:", it)
            }
        }
    }
}
