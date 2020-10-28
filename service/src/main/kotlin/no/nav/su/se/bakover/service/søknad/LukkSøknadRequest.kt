package no.nav.su.se.bakover.service.søknad

import no.nav.su.se.bakover.domain.NavIdentBruker
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
        ) : MedBrev()

        data class AvvistSøknad(
            override val søknadId: UUID,
            override val saksbehandler: NavIdentBruker.Saksbehandler,
            private val brevInfo: BrevInfo
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

    data class BrevInfo(
        val typeBrev: BrevType,
        val fritekst: String?
    )

    enum class BrevType {
        VEDTAK,
        FRITEKST
    }
}
