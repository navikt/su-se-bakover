package økonomi.infrastructure.kvittering.consumer

import arrow.core.Either
import arrow.core.flatMap
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.dataformat.xml.JacksonXmlModule
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.common.infrastructure.correlation.withCorrelationId
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.vedtak.KunneIkkeFerdigstilleVedtakMedUtbetaling
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.service.vedtak.FerdigstillVedtakService
import org.slf4j.LoggerFactory
import økonomi.domain.kvittering.Kvittering
import økonomi.infrastructure.kvittering.consumer.UtbetalingKvitteringResponse.Companion.toKvitteringResponse
import java.time.Clock

class UtbetalingKvitteringConsumer(
    private val utbetalingService: UtbetalingService,
    private val ferdigstillVedtakService: FerdigstillVedtakService,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    companion object {
        val xmlMapper = XmlMapper(JacksonXmlModule().apply { setDefaultUseWrapper(false) }).apply {
            registerModule(KotlinModule.Builder().build())
            registerModule(JavaTimeModule())
            disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        }
    }

    fun onMessage(xmlMessage: String) {
        withCorrelationId {
            val kvitteringResponse: UtbetalingKvitteringResponse = xmlMessage.toKvitteringResponse(xmlMapper)

            val kvittering: Kvittering = kvitteringResponse.toKvittering(xmlMessage, clock)
            if (!kvittering.erKvittertOk()) {
                log.error("Mottok en kvittering fra oppdragssystemet som ikke var OK: $kvittering, dette bør muligens følges opp!")
            }

            log.info("Oppdaterer utbetaling og ferdigstiller innvilgelse med kvittering fra Oppdrag")
            utbetalingService.oppdaterMedKvittering(
                utbetalingId = kvitteringResponse.utbetalingsId(),
                kvittering = kvittering,
            )
                .flatMap { ferdigstillInnvilgelse(it) }
                .mapLeft {
                    /**
                     * //TODO finn en bedre løsning på dette?
                     * Prøver på nytt etter litt delay dersom utbetalingen/vedtaket ikke finnes.
                     * Vi har en race condition (spesielt på vedtak), hvor kvitteringen fra Oppdrag av og til kommer før vi har persistert vedtaket.
                     */
                    val delayMs = 1000L
                    log.info("Noe gikk galt i prosessering av kvittering fra Oppdrag, venter ${delayMs}ms før retry")
                    runBlocking {
                        delay(delayMs)
                    }
                    utbetalingService.oppdaterMedKvittering(
                        utbetalingId = kvitteringResponse.utbetalingsId(),
                        kvittering = kvittering,
                    )
                        .flatMap { ferdigstillInnvilgelse(it) }
                        .mapLeft {
                            throw RuntimeException("Kunne ikke oppdatere kvittering eller vedtak ved prossessering av kvittering: $it")
                        }
                }
        }
    }

    private fun ferdigstillInnvilgelse(
        utbetaling: Utbetaling.OversendtUtbetaling.MedKvittering,
    ): Either<KunneIkkeFerdigstilleVedtakMedUtbetaling, Unit> {
        return ferdigstillVedtakService.ferdigstillVedtakEtterUtbetaling(utbetaling)
    }
}
