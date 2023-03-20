package no.nav.su.se.bakover.domain.vedtak

import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.dokument.Dokumenttilstand
import java.util.UUID

/**
 * Toppnivået av vedtak. Støtter både stønadsvedtak og klagevedtak.
 */
sealed interface Vedtak {
    val id: UUID
    val opprettet: Tidspunkt
    val saksbehandler: NavIdentBruker.Saksbehandler
    val attestant: NavIdentBruker.Attestant
    val dokumenttilstand: Dokumenttilstand
}
