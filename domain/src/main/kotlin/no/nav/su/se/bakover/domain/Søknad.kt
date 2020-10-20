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
    sealed class Lukket {
        abstract val tidspunkt: Tidspunkt
        abstract val saksbehandler: Saksbehandler
        abstract val typeLukking: TypeLukking

        data class Trukket(
            override val tidspunkt: Tidspunkt,
            override val saksbehandler: Saksbehandler,
            override val typeLukking: TypeLukking = TypeLukking.Trukket
        ) : Lukket()
    }

    data class LukketSøknadBody(
        val datoSøkerTrakkSøknad: LocalDate,
        val typeLukking: TypeLukking
    )

    enum class TypeLukking(val value: String) {
        Trukket("Trukket")
    }
}
