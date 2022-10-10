package no.nav.su.se.bakover.hendelse.infrastructure.persistence

import no.nav.su.se.bakover.hendelse.domain.Hendelse
import no.nav.su.se.bakover.hendelse.domain.SakOpprettetHendelse

internal enum class HendelseType {
    SAK_OPPRETTET,
    ;

    companion object {
        internal fun Hendelse.toDatabaseType(): String {
            return when (this) {
                is SakOpprettetHendelse -> SAK_OPPRETTET
            }.toString()
        }
    }
}
