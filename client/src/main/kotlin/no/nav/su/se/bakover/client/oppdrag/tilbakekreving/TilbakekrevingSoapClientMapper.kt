package no.nav.su.se.bakover.client.oppdrag.tilbakekreving

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.dataformat.xml.JacksonXmlModule
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.okonomi.tilbakekrevingservice.TilbakekrevingsvedtakRequest
import no.nav.okonomi.tilbakekrevingservice.TilbakekrevingsvedtakResponse
import javax.xml.datatype.XMLGregorianCalendar

/**
 * Er kun ment brukt for tester/stubs/lokale jobber/serialisere request/response til databasen.
 */
internal data object TilbakekrevingSoapClientMapper {

    private val xmlMapper = XmlMapper(JacksonXmlModule().apply { setDefaultUseWrapper(false) }).apply {
        registerModule(KotlinModule.Builder().build())
        registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .registerModule(CustomXMLGregorianCalendarModule())
        disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    }

    fun toXml(request: TilbakekrevingsvedtakRequest): String {
        return xmlMapper.writeValueAsString(request)
    }

    fun toXml(response: TilbakekrevingsvedtakResponse): String {
        return xmlMapper.writeValueAsString(response)
    }

    fun fromXml(xml: String): TilbakekrevingsvedtakResponse {
        return xmlMapper.readValue(xml)
    }
}

private class CustomXMLGregorianCalendarSerializer : JsonSerializer<XMLGregorianCalendar>() {
    override fun serialize(value: XMLGregorianCalendar?, gen: JsonGenerator, serializers: SerializerProvider) {
        if (value != null) {
            gen.writeString(value.toGregorianCalendar().toZonedDateTime().toLocalDate().toString())
        }
    }
}

private class CustomXMLGregorianCalendarModule : SimpleModule() {
    init {
        addSerializer(XMLGregorianCalendar::class.java, CustomXMLGregorianCalendarSerializer())
    }
}
