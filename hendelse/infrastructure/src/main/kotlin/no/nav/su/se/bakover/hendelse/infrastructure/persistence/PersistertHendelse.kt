package no.nav.su.se.bakover.hendelse.infrastructure.persistence

import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.DefaultHendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsestype
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.hendelse.domain.JMSHendelseMetadata
import java.util.UUID

data class PersistertHendelse(
    val data: String,
    val hendelsestidspunkt: Tidspunkt,
    val versjon: Hendelsesversjon,
    val type: Hendelsestype,
    val sakId: UUID?,
    val hendelseId: HendelseId,
    val tidligereHendelseId: HendelseId?,
    val hendelseMetadataDbJson: String,
    val entitetId: UUID,
) {
    /**
     * @throws com.fasterxml.jackson.core.JacksonException if the json does not match [DefaultHendelseMetadata]
     */
    fun defaultHendelseMetadata(): DefaultHendelseMetadata =
        DefaultMetadataJson.toDomain(hendelseMetadataDbJson)

    /**
     * @throws com.fasterxml.jackson.core.JacksonException if the json does not match [JMSHendelseMetadata]
     */
    fun jmsHendelseMetadata(): JMSHendelseMetadata = deserialize<JMSHendelseMetadata>(hendelseMetadataDbJson)
}
