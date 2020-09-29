package no.nav.su.se.bakover.web.services.utbetaling.kvittering

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.dataformat.xml.JacksonXmlModule
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.web.services.utbetaling.kvittering.UtbetalingKvitteringResponse.Companion.toKvitteringResponse
import java.time.Clock

class UtbetalingKvitteringConsumer(
    private val utbetalingService: UtbetalingService,
    private val clock: Clock = Clock.systemUTC(),
    private val xmlMapper: XmlMapper = UtbetalingKvitteringConsumer.xmlMapper
) {
    companion object {
        val xmlMapper = XmlMapper(JacksonXmlModule().apply { setDefaultUseWrapper(false) }).apply {
            registerModule(KotlinModule())
            registerModule(JavaTimeModule())
            disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        }
    }

    internal fun onMessage(xmlMessage: String) {
        val kvitteringResponse = xmlMessage.toKvitteringResponse(xmlMapper)

        val utbetalingId = UUID30.fromString(kvitteringResponse.oppdragRequest.avstemming.nokkelAvstemming)
        utbetalingService.hentUtbetaling(utbetalingId)
            .mapLeft { throw RuntimeException("Kunne ikke lagre kvittering. Fant ikke utbetaling med id $utbetalingId") }
            .map { it.addKvittering(kvitteringResponse.toKvittering(xmlMessage, clock)) }
    }
}
