package no.nav.su.se.bakover.hendelse.infrastructure.persistence

import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.HendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import java.util.UUID

data class PersistertHendelse(
    val data: String,
    val hendelsestidspunkt: Tidspunkt,
    val versjon: Hendelsesversjon,
    val type: String,
    val sakId: UUID?,
    val hendelseId: UUID,
    val tidligereHendelseId: UUID?,
    val hendelseMetadata: HendelseMetadata,
    val entitetId: UUID,
)
