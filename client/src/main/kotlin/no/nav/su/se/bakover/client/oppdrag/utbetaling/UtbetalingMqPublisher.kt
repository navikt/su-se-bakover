package no.nav.su.se.bakover.client.oppdrag.utbetaling

import arrow.core.Either
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.xml.JacksonXmlModule
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.dataformat.xml.ser.ToXmlGenerator
import no.nav.su.se.bakover.client.oppdrag.MqPublisher
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher.KunneIkkeSendeUtbetaling
import java.time.Clock

class UtbetalingMqPublisher(
    private val clock: Clock = Clock.systemUTC(),
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
        utbetaling: Utbetaling,
        oppdragGjelder: Fnr
    ): Either<KunneIkkeSendeUtbetaling, Unit> {
        val xml = xmlMapper.writeValueAsString(utbetaling.toExternal(oppdragGjelder, clock))
        return mqPublisher.publish(xml).mapLeft { KunneIkkeSendeUtbetaling }
    }
}
