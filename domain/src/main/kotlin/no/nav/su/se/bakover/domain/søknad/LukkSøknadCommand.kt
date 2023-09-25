package no.nav.su.se.bakover.domain.søknad

import dokument.domain.brev.Brevvalg
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import java.time.LocalDate
import java.util.UUID

sealed class LukkSøknadCommand {
    abstract val søknadId: UUID
    abstract val saksbehandler: NavIdentBruker.Saksbehandler
    abstract val lukketTidspunkt: Tidspunkt

    // Convenience prop
    open val brevvalg: Brevvalg.SaksbehandlersValg? = null

    sealed class MedBrev : LukkSøknadCommand() {

        data class TrekkSøknad(
            override val søknadId: UUID,
            override val saksbehandler: NavIdentBruker.Saksbehandler,
            override val lukketTidspunkt: Tidspunkt,
            val trukketDato: LocalDate,
        ) : MedBrev()

        data class AvvistSøknad(
            override val søknadId: UUID,
            override val saksbehandler: NavIdentBruker.Saksbehandler,
            override val lukketTidspunkt: Tidspunkt,
            override val brevvalg: Brevvalg.SaksbehandlersValg,
        ) : MedBrev()
    }

    sealed class UtenBrev : LukkSøknadCommand() {
        data class BortfaltSøknad(
            override val søknadId: UUID,
            override val saksbehandler: NavIdentBruker.Saksbehandler,
            override val lukketTidspunkt: Tidspunkt,
        ) : UtenBrev()

        data class AvvistSøknad(
            override val søknadId: UUID,
            override val saksbehandler: NavIdentBruker.Saksbehandler,
            override val lukketTidspunkt: Tidspunkt,
        ) : UtenBrev()
    }
}
