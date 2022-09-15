package no.nav.su.se.bakover.client.oppdrag

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.xml.JacksonXmlModule
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.dataformat.xml.ser.ToXmlGenerator

internal val XmlMapper = XmlMapper(
    JacksonXmlModule().apply { setDefaultUseWrapper(false) },
).apply {
    configure(ToXmlGenerator.Feature.WRITE_XML_DECLARATION, true)
    enable(SerializationFeature.INDENT_OUTPUT)
    setSerializationInclusion(JsonInclude.Include.NON_NULL)
}
