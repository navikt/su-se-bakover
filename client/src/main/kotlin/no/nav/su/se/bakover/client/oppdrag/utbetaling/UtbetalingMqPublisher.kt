package no.nav.su.se.bakover.client.oppdrag.utbetaling

import arrow.core.Either
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.xml.JacksonXmlModule
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.dataformat.xml.ser.ToXmlGenerator
import no.nav.su.se.bakover.client.oppdrag.MqPublisher
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher.KunneIkkeSendeUtbetaling

class UtbetalingMqPublisher(
    private val mqPublisher: MqPublisher,
    private val xmlMapper: XmlMapper = XmlMapper(
        JacksonXmlModule().apply { setDefaultUseWrapper(false) }
    ).apply {
        configure(ToXmlGenerator.Feature.WRITE_XML_DECLARATION, true)
        enable(SerializationFeature.INDENT_OUTPUT)
        setSerializationInclusion(JsonInclude.Include.NON_NULL)
    }
) : UtbetalingPublisher {

    override fun publish(
        oppdrag: Oppdrag,
        utbetaling: Utbetaling,
        oppdragGjelder: Fnr
    ): Either<KunneIkkeSendeUtbetaling, String> {
        val xml = xmlMapper.writeValueAsString(toUtbetalingRequest(oppdrag, utbetaling, oppdragGjelder))
        return mqPublisher.publish(xml)
            .mapLeft { KunneIkkeSendeUtbetaling(xml) }
            .map { xml }
    }
}
