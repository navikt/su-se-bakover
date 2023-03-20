package no.nav.su.se.bakover.domain.vedtak

import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
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
    abstract override val behandling: Behandling
    abstract override val saksbehandler: NavIdentBruker.Saksbehandler
    abstract override val attestant: NavIdentBruker.Attestant
    abstract override val periode: Periode
    abstract override val simulering: Simulering
    abstract override val utbetalingId: UUID30
    abstract override val beregning: Beregning?
}
