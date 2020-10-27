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

    data class BortfaltSøknad(
        override val søknadId: UUID,
        override val saksbehandler: NavIdentBruker.Saksbehandler
    ) : LukkSøknadRequest()

    sealed class AvvistSøknad : LukkSøknadRequest() {
        data class MedBrev(
            override val søknadId: UUID,
            override val saksbehandler: NavIdentBruker.Saksbehandler,
            private val brevInfo: BrevInfo
        ) : AvvistSøknad()

        data class UtenBrev(
            override val søknadId: UUID,
            override val saksbehandler: NavIdentBruker.Saksbehandler,
        ) : AvvistSøknad()

        data class BrevInfo(
            val typeBrev: BrevType,
            val fritekst: String?
        )

        enum class BrevType {
            VEDTAK,
            FRITEKST
        }
    }
}
