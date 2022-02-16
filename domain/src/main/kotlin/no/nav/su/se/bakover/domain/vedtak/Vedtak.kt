package no.nav.su.se.bakover.domain.vedtak

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.bruker.NavIdentBruker
import java.util.UUID

/**
 * Toppnivået av vedtak. Støtter både stønadsvedtak og klagevedtak.
 */
sealed interface Vedtak {
    val id: UUID
    val opprettet: Tidspunkt
    val saksbehandler: NavIdentBruker.Saksbehandler
    val attestant: NavIdentBruker.Attestant
}
