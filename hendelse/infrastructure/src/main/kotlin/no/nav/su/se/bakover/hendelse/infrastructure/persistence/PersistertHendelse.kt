package no.nav.su.se.bakover.hendelse.infrastructure.persistence

import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsestype
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.hendelse.domain.JMSHendelseMetadata
import java.util.UUID

/**
 * Henter ut en hendelse fra databasen uten metadata, dette er det vanligste tilfellet.
 */
data class PersistertHendelse(
    val data: String,
    val hendelsestidspunkt: Tidspunkt,
    val versjon: Hendelsesversjon,
    val type: Hendelsestype,
    val sakId: UUID?,
    val hendelseId: HendelseId,
    val tidligereHendelseId: HendelseId?,
    val entitetId: UUID,
)

/**
 * Henter ut en hendelse fra databasen med metadata, det er ikke ofte vi trenger dette.
 * Et eksempel er [JMSHendelseMetadata].
 */
data class PersistertHendelseMedMetadata(
    val hendelse: PersistertHendelse,
    val meta: String,
) {
    /** @throws com.fasterxml.jackson.core.JacksonException if the json does not match [JMSHendelseMetadata] */
    fun jmsHendelseMetadata(): JMSHendelseMetadata = toJMSHendelseMetaData(meta)
}
