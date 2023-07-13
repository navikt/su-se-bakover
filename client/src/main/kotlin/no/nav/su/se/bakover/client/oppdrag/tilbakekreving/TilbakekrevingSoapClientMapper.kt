package no.nav.su.se.bakover.client.oppdrag.tilbakekreving

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.dataformat.xml.JacksonXmlModule
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.okonomi.tilbakekrevingservice.TilbakekrevingsvedtakRequest
import no.nav.okonomi.tilbakekrevingservice.TilbakekrevingsvedtakResponse

internal data object TilbakekrevingSoapClientMapper {

    private val xmlMapper = XmlMapper(JacksonXmlModule().apply { setDefaultUseWrapper(false) }).apply {
        registerModule(KotlinModule.Builder().build())
        registerModule(JavaTimeModule())
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
