package dokument.domain.hendelser

import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Sakshendelse

sealed interface DokumentHendelse : Sakshendelse {
    val relatertHendelse: HendelseId
}
