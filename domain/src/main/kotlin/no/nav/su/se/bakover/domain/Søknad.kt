package no.nav.su.se.bakover.domain

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.now
import java.time.LocalDate
import java.util.UUID

data class Søknad(
    val sakId: UUID,
    val id: UUID = UUID.randomUUID(),
    val opprettet: Tidspunkt = now(),
    val søknadInnhold: SøknadInnhold,
    val lukket: Lukket? = null,
) {
    fun erLukket() = lukket != null

    sealed class Lukket {
        abstract val tidspunkt: Tidspunkt
        abstract val saksbehandler: NavIdentBruker.Saksbehandler

        data class Trukket(
            override val tidspunkt: Tidspunkt,
            override val saksbehandler: NavIdentBruker.Saksbehandler,
            val datoSøkerTrakkSøknad: LocalDate
        ) : Lukket()
    }
}
