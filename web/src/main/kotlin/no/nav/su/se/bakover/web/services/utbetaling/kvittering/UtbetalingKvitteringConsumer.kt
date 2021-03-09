package no.nav.su.se.bakover.web.services.utbetaling.kvittering

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.dataformat.xml.JacksonXmlModule
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.service.vedtak.FerdigstillVedtakService
import no.nav.su.se.bakover.web.services.utbetaling.kvittering.UtbetalingKvitteringResponse.Companion.toKvitteringResponse
import org.slf4j.LoggerFactory
import java.time.Clock

class UtbetalingKvitteringConsumer(
    private val utbetalingService: UtbetalingService,
    private val ferdigstillVedtakService: FerdigstillVedtakService,
    private val clock: Clock
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    companion object {
        val xmlMapper = XmlMapper(JacksonXmlModule().apply { setDefaultUseWrapper(false) }).apply {
            registerModule(KotlinModule())
            registerModule(JavaTimeModule())
            disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        }
    }

    internal fun onMessage(xmlMessage: String) {
        val kvitteringResponse = xmlMessage.toKvitteringResponse(xmlMapper)

        val avstemmingsnøkkel = kvitteringResponse.oppdragRequest.avstemming.nokkelAvstemming.let {
            Avstemmingsnøkkel.fromString(it)
        }

        val kvittering: Kvittering = kvitteringResponse.toKvittering(xmlMessage, clock)
        utbetalingService.oppdaterMedKvittering(avstemmingsnøkkel, kvittering)
            .map { ferdigstillInnvilgelse(it) }
            .mapLeft {
                runBlocking {
                    /**
                     * //TODO finn en bedre løsning på dette?
                     * Prøver på nytt etter litt delay dersom utbetalingen ikke finnes. Opplever en del tilfeller
                     * hvor dette skjer, selv om utbetalingen i ettertid finnes i databasen.
                     */
                    val delayMs = 1000L
                    log.info("Fant ikke utbetaling for avstemmingsnøkkel $avstemmingsnøkkel, venter $delayMs før retry")
                    delay(delayMs)
                    utbetalingService.oppdaterMedKvittering(
                        avstemmingsnøkkel,
                        kvittering
                    )
                        .map { ferdigstillInnvilgelse(it) }
                        .mapLeft {
                            throw RuntimeException("Kunne ikke lagre kvittering. Fant ikke utbetaling med avstemmingsnøkkel $avstemmingsnøkkel")
                        }
                }
            }
    }

    private fun ferdigstillInnvilgelse(utbetaling: Utbetaling.OversendtUtbetaling.MedKvittering) {
        if (utbetaling.type != Utbetaling.UtbetalingsType.NY) {
            log.info("Utbetaling ${utbetaling.id} er av type ${utbetaling.type} og vil derfor ikke bli prøvd ferdigstilt.")
            return
        }
        if (!utbetaling.kvittering.erKvittertOk()) {
            log.error("Prøver ikke å ferdigstille innvilgelse siden kvitteringen fra oppdrag ikke var OK.")
            return
        }
        ferdigstillVedtakService.ferdigstillVedtakEtterUtbetaling(utbetaling.id)
    }
}
