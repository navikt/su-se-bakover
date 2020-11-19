package no.nav.su.se.bakover.domain.søknad

import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.brev.BrevConfig
import java.time.LocalDate
import java.util.UUID

sealed class LukkSøknadRequest {
    abstract val søknadId: UUID
    abstract val saksbehandler: NavIdentBruker.Saksbehandler

    sealed class MedBrev : LukkSøknadRequest() {

        data class TrekkSøknad(
            override val søknadId: UUID,
            override val saksbehandler: NavIdentBruker.Saksbehandler,
            val trukketDato: LocalDate
        ) : MedBrev() {
            fun erDatoGyldig(dato: LocalDate): Boolean {
                return !trukketDato.isBefore(dato) && !trukketDato.isAfter(LocalDate.now())
            }
        }

        data class AvvistSøknad(
            override val søknadId: UUID,
            override val saksbehandler: NavIdentBruker.Saksbehandler,
            val brevConfig: BrevConfig
        ) : MedBrev()
    }

    sealed class UtenBrev : LukkSøknadRequest() {
        data class BortfaltSøknad(
            override val søknadId: UUID,
            override val saksbehandler: NavIdentBruker.Saksbehandler
        ) : UtenBrev()

        data class AvvistSøknad(
            override val søknadId: UUID,
            override val saksbehandler: NavIdentBruker.Saksbehandler,
        ) : UtenBrev()
    }
}
