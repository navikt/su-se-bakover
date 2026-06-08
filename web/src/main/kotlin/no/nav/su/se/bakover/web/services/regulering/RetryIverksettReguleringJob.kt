package no.nav.su.se.bakover.web.services.regulering

import no.nav.su.se.bakover.common.infrastructure.job.RunCheckFactory
import no.nav.su.se.bakover.common.infrastructure.job.StoppableJob
import no.nav.su.se.bakover.common.infrastructure.job.startStoppableJob
import no.nav.su.se.bakover.domain.regulering.ReguleringRetryService
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Month

/**
 * Jobb som finner iverksatte reguleringer der utbetalingen ikke ble sendt til Oppdrag (IBM MQ)
 * og prøver å sende dem på nytt. Kjører kun i juni og kun på leader-pod.
 *
 * ## Bakgrunn
 * Under G-regulering opprettes utbetalingen og reguleringen lagres i databasen i én transaksjon,
 * *før* meldingen sendes til Oppdrag over IBM MQ. Hvis MQ-sendingen feiler etter at transaksjonen
 * er committed, settes `erSendtTilOppdrag = false` på reguleringen slik at denne jobben kan
 * plukke den opp og sende på nytt.
 *
 * ## Identifikatorkjede: Regulering → Vedtak → Utbetaling
 * - `IverksattRegulering.id` (`ReguleringId`) er primærnøkkelen for reguleringen.
 * - Vedtaket knyttes til reguleringen via `behandling_vedtak.reguleringId` (kolonnen er lagt til i V128).
 *   Hentes med [no.nav.su.se.bakover.vedtak.application.VedtakService.hentForReguleringId].
 * - Utbetalingen knyttes til vedtaket via `VedtakInnvilgetRegulering.utbetalingId` (`UUID30`),
 *   som igjen peker på raden i `utbetaling`-tabellen.
 *   Hentes og sendes med [økonomi.application.utbetaling.UtbetalingService.sendUkvittertUtbetaling].
 */
internal class RetryIverksettReguleringJob(
    private val stoppableJob: StoppableJob,
) : StoppableJob by stoppableJob {
    companion object {
        fun startJob(
            initialDelay: Duration,
            periode: Duration,
            reguleringRetryService: ReguleringRetryService,
            runCheckFactory: RunCheckFactory,
        ): RetryIverksettReguleringJob {
            val log = LoggerFactory.getLogger(RetryIverksettReguleringJob::class.java)
            val jobName = RetryIverksettReguleringJob::class.simpleName!!
            return startStoppableJob(
                jobName = jobName,
                log = log,
                initialDelay = initialDelay,
                intervall = periode,
                runJobCheck = listOf(
                    runCheckFactory.leaderPod(),
                    runCheckFactory.kunIMåneder(Month.JUNE),
                ),
            ) {
                log.info("Kjører $jobName")
                reguleringRetryService.retrySendUtbetalingForIkkeOversendte()
                log.info("Jobb $jobName er fullført")
            }.let { RetryIverksettReguleringJob(it) }
        }
    }
}
