package no.nav.su.se.bakover.service.søknad

import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.brev.BrevConfig
import java.time.LocalDate
import java.util.UUID

sealed class LukkSøknadRequest {
    abstract val søknadId: UUID
    abstract val saksbehandler: NavIdentBruker.Saksbehandler

    sealed class MedBrev : LukkSøknadRequest() {
        abstract val brevConfig: BrevConfig

        data class TrekkSøknad(
            override val søknadId: UUID,
            override val saksbehandler: NavIdentBruker.Saksbehandler,
            override val brevConfig: BrevConfig = BrevConfig.BrevTypeConfig(BrevConfig.BrevType.VEDTAK),
            val trukketDato: LocalDate
        ) : MedBrev()

        data class AvvistSøknad(
            override val søknadId: UUID,
            override val saksbehandler: NavIdentBruker.Saksbehandler,
            override val brevConfig: BrevConfig
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
