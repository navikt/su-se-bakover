package no.nav.su.se.bakover.web.services.tilbakekreving

import arrow.core.Either
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.dataformat.xml.JacksonXmlModule
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue

/** Mapper fra XML til [KravmeldingDto] */
internal object TilbakekrevingXmlMapper {
    private val xmlMapper = XmlMapper(JacksonXmlModule().apply { setDefaultUseWrapper(false) }).apply {
        registerModule(KotlinModule.Builder().build())
        registerModule(JavaTimeModule())
        disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    }

    fun toDto(kravgrunnlagXml: String): Either<Throwable, KravmeldingDto> {
        return Either.catch {
            xmlMapper.readValue<KravmeldingRootDto>(kravgrunnlagXml).kravmeldingDto
        }
    }
}
