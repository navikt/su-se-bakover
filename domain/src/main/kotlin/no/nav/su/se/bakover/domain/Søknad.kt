package no.nav.su.se.bakover.domain

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.now
import java.util.UUID

data class Søknad(
    val sakId: UUID,
    val id: UUID = UUID.randomUUID(),
    val opprettet: Tidspunkt = now(),
    val søknadInnhold: SøknadInnhold,
    val lukket: Lukket? = null,
) {
    sealed class Lukket {
        abstract val tidspunkt: Tidspunkt
        abstract val saksbehandler: Saksbehandler
        abstract val begrunnelse: String

        data class Trukket(
            override val tidspunkt: Tidspunkt,
            override val saksbehandler: Saksbehandler,
            override val begrunnelse: String
        ) : Lukket()
    }
}
