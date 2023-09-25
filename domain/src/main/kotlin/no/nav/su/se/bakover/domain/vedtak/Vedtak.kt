package no.nav.su.se.bakover.domain.vedtak

import dokument.domain.Dokumenttilstand
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.behandling.Avsluttet
import java.util.UUID

/**
 * Toppnivået av vedtak. Støtter både stønadsvedtak og klagevedtak.
 */
sealed interface Vedtak : Avsluttet {
    override val id: UUID

    /**
     * Et vedtak blir opprettet & avsluttet samtidig
     */
    override val avsluttetTidspunkt: Tidspunkt get() = opprettet

    val opprettet: Tidspunkt
    val saksbehandler: NavIdentBruker.Saksbehandler
    val attestant: NavIdentBruker.Attestant
    val dokumenttilstand: Dokumenttilstand
}
