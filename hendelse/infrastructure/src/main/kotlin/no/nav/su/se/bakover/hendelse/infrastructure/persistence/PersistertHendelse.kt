package no.nav.su.se.bakover.hendelse.infrastructure.persistence

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon

data class PersistertHendelse(
    val data: String,
    val hendelsestidspunkt: Tidspunkt,
    val versjon: Hendelsesversjon,
)
