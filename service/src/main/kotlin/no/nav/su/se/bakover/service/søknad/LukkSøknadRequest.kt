package no.nav.su.se.bakover.service.søknad

import no.nav.su.se.bakover.domain.NavIdentBruker
import java.time.LocalDate
import java.util.UUID

sealed class LukkSøknadRequest {
    abstract val søknadId: UUID
    abstract val saksbehandler: NavIdentBruker.Saksbehandler

    data class TrekkSøknad(
        override val søknadId: UUID,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        val trukketDato: LocalDate
    ) : LukkSøknadRequest()
}
