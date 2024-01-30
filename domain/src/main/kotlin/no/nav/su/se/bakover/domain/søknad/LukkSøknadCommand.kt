package no.nav.su.se.bakover.domain.søknad

import dokument.domain.brev.Brevvalg
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import java.time.LocalDate
import java.util.UUID

sealed interface LukkSøknadCommand {
    val søknadId: UUID
    val saksbehandler: NavIdentBruker.Saksbehandler
    val lukketTidspunkt: Tidspunkt

    // Convenience prop
    val brevvalg: Brevvalg.SaksbehandlersValg? get() = null

    sealed interface MedBrev : LukkSøknadCommand {

        data class TrekkSøknad(
            override val søknadId: UUID,
            override val saksbehandler: NavIdentBruker.Saksbehandler,
            override val lukketTidspunkt: Tidspunkt,
            val trukketDato: LocalDate,
        ) : MedBrev

        data class AvvistSøknad(
            override val søknadId: UUID,
            override val saksbehandler: NavIdentBruker.Saksbehandler,
            override val lukketTidspunkt: Tidspunkt,
            override val brevvalg: Brevvalg.SaksbehandlersValg,
        ) : MedBrev
    }

    sealed interface UtenBrev : LukkSøknadCommand {
        data class BortfaltSøknad(
            override val søknadId: UUID,
            override val saksbehandler: NavIdentBruker.Saksbehandler,
            override val lukketTidspunkt: Tidspunkt,
        ) : UtenBrev

        data class AvvistSøknad(
            override val søknadId: UUID,
            override val saksbehandler: NavIdentBruker.Saksbehandler,
            override val lukketTidspunkt: Tidspunkt,
        ) : UtenBrev
    }
}
