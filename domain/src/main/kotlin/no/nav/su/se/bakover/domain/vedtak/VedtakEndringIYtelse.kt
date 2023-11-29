package no.nav.su.se.bakover.domain.vedtak

import beregning.domain.Beregning
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.behandling.Stønadsbehandling
import økonomi.domain.simulering.Simulering
import java.util.UUID

/**
 * Motparten til [VedtakIngenEndringIYtelse].
 * Vil ha opprettet en eller flere utbetalingslinjer.
 *
 * Ikke alle undertyper har beregning; i.e. [VedtakStansAvYtelse] og [VedtakGjenopptakAvYtelse].
 */
sealed interface VedtakEndringIYtelse : VedtakSomKanRevurderes {
    abstract override val id: UUID
    abstract override val opprettet: Tidspunkt
    abstract override val behandling: Stønadsbehandling
    abstract override val saksbehandler: NavIdentBruker.Saksbehandler
    abstract override val attestant: NavIdentBruker.Attestant
    abstract override val periode: Periode
    abstract override val simulering: Simulering
    abstract override val utbetalingId: UUID30
    abstract override val beregning: Beregning?
}
