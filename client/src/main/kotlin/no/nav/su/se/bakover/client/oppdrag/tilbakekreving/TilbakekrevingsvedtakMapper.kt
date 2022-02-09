package no.nav.su.se.bakover.client.oppdrag.tilbakekreving

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.dataformat.xml.JacksonXmlModule
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import no.nav.okonomi.tilbakekrevingservice.TilbakekrevingsvedtakRequest
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.RåttTilbakekrevingsvedtak

internal object TilbakekrevingsvedtakMapper {

    private val xmlMapper = XmlMapper(JacksonXmlModule().apply { setDefaultUseWrapper(false) }).apply {
        registerModule(KotlinModule.Builder().build())
        registerModule(JavaTimeModule())
        disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    }

    fun map(request: TilbakekrevingsvedtakRequest): RåttTilbakekrevingsvedtak {
        return RåttTilbakekrevingsvedtak(xmlMapper.writeValueAsString(request))
    }
}
