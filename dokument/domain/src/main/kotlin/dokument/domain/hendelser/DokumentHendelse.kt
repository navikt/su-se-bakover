package dokument.domain.hendelser

import arrow.core.NonEmptyList
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Sakshendelse

sealed interface DokumentHendelse : Sakshendelse {
    val relaterteHendelser: NonEmptyList<HendelseId>
}
