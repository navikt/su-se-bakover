package no.nav.su.se.bakover.web.services.utbetaling.kvittering

import com.fasterxml.jackson.dataformat.xml.XmlMapper
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.database.ObjectRepo
import no.nav.su.se.bakover.web.services.utbetaling.kvittering.KvitteringResponse.Companion.toKvitteringResponse

class KvitteringConsumer(
    private val xmlMapper: XmlMapper = XmlMapper(),
    private val repo: ObjectRepo,
) {
    internal fun onMessage(xmlMessage: String) {
        val kvitteringResponse = xmlMessage.toKvitteringResponse(xmlMapper)
        val utbetalingId = UUID30.fromString(kvitteringResponse.oppdrag.avstemming.nokkelAvstemming)
        repo.hentUtbetaling(utbetalingId)
            ?.addKvittering(kvitteringResponse.toKvittering(xmlMessage))
            ?: throw RuntimeException("Kunne ikke lagre kvittering. Fant ikke utbetaling med id $utbetalingId")
    }
}
