package no.nav.su.se.bakover.hendelse.application

import no.nav.su.se.bakover.common.Tidspunkt
import java.util.UUID

/**
 * @property id unikt identifiserer denne hendelsen.
 * @property entitetId Også kalt streamId. Knytter en serie med hendelser sammen, hvor versjon angir rekkefølgen.
 * @property versjon se: [entitetId]
 * @property hendelsestidspunkt Tidspunktet hendelsen skjedde fra domenet sin side.
 * @property meta metadata knyttet til hendelsen for auditing/tracing/debug-formål.
 */
sealed interface Hendelse {
    val id: UUID
    val sakId: UUID
    val entitetId: UUID
    val versjon: Versjon
    val hendelsestidspunkt: Tidspunkt
    val meta: HendelseMetadata

    @JvmInline
    value class Versjon(val value: Long) {

        init {
            require(value > 0L)
        }
        override fun toString() = value.toString()
    }
}
