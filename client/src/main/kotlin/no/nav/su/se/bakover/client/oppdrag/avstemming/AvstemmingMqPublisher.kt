package no.nav.su.se.bakover.client.oppdrag.avstemming

import arrow.core.Either
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.xml.JacksonXmlModule
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.dataformat.xml.ser.ToXmlGenerator
import no.nav.su.se.bakover.client.oppdrag.MqPublisher
import java.time.Clock

// Denne skal kunne lage avstemmingsxml og sende på mq til oppdrag
// Den trenger Clock for å kunne sette tidspunkt i testene
// Den trenger mqPublisher for å kunne sende melding på mq
// Den trenger xmlMapper for å kunne mappe en klasse -> xml

class AvstemmingMqPublisher(
    private val clock: Clock = Clock.systemUTC(),
    private val mqPublisher: MqPublisher,
    private val xmlMapper: XmlMapper = XmlMapper(
        JacksonXmlModule().apply { setDefaultUseWrapper(false) }
    ).apply {
        configure(ToXmlGenerator.Feature.WRITE_XML_DECLARATION, true)
        enable(SerializationFeature.INDENT_OUTPUT)
        setSerializationInclusion(JsonInclude.Include.NON_NULL)
    }
) {

    fun publishStart(
        startRequest: AvstemmingStartRequest
    ): Either<KunneIkkeSendeAvstemming, Unit> {
        val xml = xmlMapper.writeValueAsString(startRequest)
        return mqPublisher.publish(xml).mapLeft { KunneIkkeSendeAvstemming }
    }

    fun publishData(
        dataRequest: AvstemmingDataRequest
    ): Either<KunneIkkeSendeAvstemming, Unit> {
        val xml = xmlMapper.writeValueAsString(dataRequest)
        return mqPublisher.publish(xml).mapLeft { KunneIkkeSendeAvstemming }
    }

    fun publishStopp(
        stoppRequest: AvstemmingStoppRequest
    ): Either<KunneIkkeSendeAvstemming, Unit> {
        val xml = xmlMapper.writeValueAsString(stoppRequest)
        return mqPublisher.publish(xml).mapLeft { KunneIkkeSendeAvstemming }
    }
}

object KunneIkkeSendeAvstemming
