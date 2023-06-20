package no.nav.su.se.bakover.domain.søknad

import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.brev.Brevvalg
import no.nav.su.se.bakover.domain.dokument.Dokumenttilstand
import java.time.LocalDate
import java.util.UUID

sealed class LukkSøknadCommand {
    abstract val søknadId: UUID
    abstract val saksbehandler: NavIdentBruker.Saksbehandler
    abstract val lukketTidspunkt: Tidspunkt

    // Convenience prop
    open val brevvalg: Brevvalg.SaksbehandlersValg? = null
    abstract val dokumenttilstand: Dokumenttilstand

    sealed class MedBrev : LukkSøknadCommand() {

        data class TrekkSøknad(
            override val søknadId: UUID,
            override val saksbehandler: NavIdentBruker.Saksbehandler,
            override val lukketTidspunkt: Tidspunkt,
            val trukketDato: LocalDate,
        ) : MedBrev() {
            override val dokumenttilstand: Dokumenttilstand = Dokumenttilstand.IKKE_GENERERT_ENDA
        }

        data class AvvistSøknad(
            override val søknadId: UUID,
            override val saksbehandler: NavIdentBruker.Saksbehandler,
            override val lukketTidspunkt: Tidspunkt,
            override val brevvalg: Brevvalg.SaksbehandlersValg,
        ) : MedBrev() {
            override val dokumenttilstand: Dokumenttilstand = Dokumenttilstand.IKKE_GENERERT_ENDA
        }
    }

    sealed class UtenBrev : LukkSøknadCommand() {
        data class BortfaltSøknad(
            override val søknadId: UUID,
            override val saksbehandler: NavIdentBruker.Saksbehandler,
            override val lukketTidspunkt: Tidspunkt,
        ) : UtenBrev() {
            override val dokumenttilstand: Dokumenttilstand = Dokumenttilstand.SKAL_IKKE_GENERERE
        }

        data class AvvistSøknad(
            override val søknadId: UUID,
            override val saksbehandler: NavIdentBruker.Saksbehandler,
            override val lukketTidspunkt: Tidspunkt,
        ) : UtenBrev() {
            override val dokumenttilstand: Dokumenttilstand = Dokumenttilstand.SKAL_IKKE_GENERERE
        }
    }
}
