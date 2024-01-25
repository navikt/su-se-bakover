package no.nav.su.se.bakover.vedtak.domain

import dokument.domain.Dokumenttilstand
import no.nav.su.se.bakover.behandling.Behandling
import no.nav.su.se.bakover.common.domain.Avsluttet
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import java.util.UUID

/**
 * Toppnivået av vedtak. Støtter både stønadsvedtak og klagevedtak.
 */
interface Vedtak : Avsluttet {
    val id: UUID
    val behandling: Behandling

    /**
     * Et vedtak blir opprettet & avsluttet samtidig
     */
    override val avsluttetTidspunkt: Tidspunkt get() = opprettet
    override val avsluttetAv: NavIdentBruker
        get() = attestant

    val opprettet: Tidspunkt
    val saksbehandler: NavIdentBruker.Saksbehandler
    val attestant: NavIdentBruker.Attestant
    val dokumenttilstand: Dokumenttilstand


    fun kanStarteNyBehandling(): Boolean
}
