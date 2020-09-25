package no.nav.su.se.bakover.domain.hendelseslogg.hendelse

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.su.se.bakover.common.MicroInstant
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.hendelseslogg.hendelse.behandling.UnderkjentAttestering

private val HendelseListType =
    objectMapper.typeFactory.constructCollectionLikeType(List::class.java, Hendelse::class.java)
val HendelseListWriter = objectMapper.writerFor(HendelseListType)
val HendelseListReader = objectMapper.readerFor(HendelseListType)

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type"
)
@JsonSubTypes(
    JsonSubTypes.Type(value = UnderkjentAttestering::class)
)
@JsonIgnoreProperties(
    "overskrift",
    "underoverskrift",
    "melding"
)
interface Hendelse {
    val overskrift: String
    val underoverskrift: String
    val tidspunkt: MicroInstant
    val melding: String
}

abstract class AbstractHendelse : Hendelse
