package no.nav.su.se.bakover.client.oppdrag.avstemming

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.xml.JacksonXmlModule
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.dataformat.xml.ser.ToXmlGenerator

object AvstemmingXmlMapper {
    private val xmlMapper: XmlMapper = XmlMapper(
        JacksonXmlModule().apply { setDefaultUseWrapper(false) }
    ).apply {
        configure(ToXmlGenerator.Feature.WRITE_XML_DECLARATION, true)
        enable(SerializationFeature.INDENT_OUTPUT)
        setSerializationInclusion(JsonInclude.Include.NON_NULL)
    }

    fun map(request: AvstemmingStartRequest) = xmlMapper.writeValueAsString(request)
    fun map(request: AvstemmingDataRequest) = xmlMapper.writeValueAsString(request)
    fun map(request: AvstemmingStoppRequest) = xmlMapper.writeValueAsString(request)
}
