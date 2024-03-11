package no.nav.su.se.bakover.domain.vedtak

import beregning.domain.Beregning
import no.nav.su.se.bakover.behandling.Stønadsbehandling
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import vedtak.domain.VedtakSomKanRevurderes
import økonomi.domain.simulering.Simulering
import java.util.UUID

/**
 * ADVARSEL: Denne brukes kun av [VedtakOpphørUtenUtbetaling], som igjen brukes kun for historiske revurderinger (rene avkortingsvedtak uten utbetaling). Skal ikke brukes i nye revurderinger.
 *
 * Motparten til [VedtakEndringIYtelse].
 * Vil ikke ha nye utbetalingslinjer (fører ikke til en utbetaling mot oppdrag)
 */
sealed interface VedtakIngenEndringIYtelse : VedtakSomKanRevurderes {
    abstract override val id: UUID
    abstract override val opprettet: Tidspunkt
    abstract override val behandling: Stønadsbehandling
    abstract override val saksbehandler: NavIdentBruker.Saksbehandler
    abstract override val attestant: NavIdentBruker.Attestant
    abstract override val periode: Periode
    override val beregning: Beregning
    override val simulering: Simulering
}
