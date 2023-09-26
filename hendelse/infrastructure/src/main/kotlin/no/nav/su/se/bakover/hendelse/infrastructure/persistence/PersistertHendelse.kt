package no.nav.su.se.bakover.hendelse.infrastructure.persistence

import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.HendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.Hendelsestype
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import java.util.UUID

data class PersistertHendelse(
    val data: String,
    val hendelsestidspunkt: Tidspunkt,
    val versjon: Hendelsesversjon,
    val type: Hendelsestype,
    val sakId: UUID?,
    val hendelseId: HendelseId,
    val tidligereHendelseId: HendelseId?,
    val hendelseMetadata: HendelseMetadata,
    val entitetId: UUID,
)
