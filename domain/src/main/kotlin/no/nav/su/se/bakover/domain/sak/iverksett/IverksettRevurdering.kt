package no.nav.su.se.bakover.domain.sak.iverksett

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingKlargjortForOversendelse
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import java.time.Clock
import java.util.UUID

fun Sak.iverksettRevurdering(
    revurderingId: UUID,
    attestant: NavIdentBruker.Attestant,
    clock: Clock,
    simuler: (utbetaling: Utbetaling.UtbetalingForSimulering, periode: Periode) -> Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling>,
): Either<KunneIkkeIverksetteRevurdering, IverksettRevurderingResponse<VedtakSomKanRevurderes.EndringIYtelse>> {
    val revurdering = hentRevurdering(revurderingId)
        .getOrHandle { return KunneIkkeIverksetteRevurdering.FantIkkeRevurdering.left() }
        .let {
            (it as? RevurderingTilAttestering)
                ?: return KunneIkkeIverksetteRevurdering.UgyldigTilstand(
                    fra = it::class,
                    til = IverksattRevurdering::class,
                ).left()
        }

    return when (revurdering) {
        is RevurderingTilAttestering.Innvilget -> iverksettInnvilgetRevurdering(
            revurdering = revurdering,
            attestant = attestant,
            clock = clock,
            simuler = simuler,
        )
        is RevurderingTilAttestering.Opphørt -> iverksettOpphørtRevurdering(
            revurdering = revurdering,
            attestant = attestant,
            clock = clock,
            simuler = simuler,
        )
    }
}

interface IverksettRevurderingResponse<out T : VedtakSomKanRevurderes.EndringIYtelse> {
    val sak: Sak
    val vedtak: T
    val utbetaling: Utbetaling.SimulertUtbetaling
    val statistikkhendelser: List<StatistikkEvent>

    /**
     * @param annullerKontrollsamtale er kun relevant ved opphør.
     */
    fun ferdigstillIverksettelseITransaksjon(
        sessionFactory: SessionFactory,
        klargjørUtbetaling: (utbetaling: Utbetaling.SimulertUtbetaling, tx: TransactionContext) -> Either<UtbetalingFeilet, UtbetalingKlargjortForOversendelse<UtbetalingFeilet.Protokollfeil>>,
        lagreVedtak: (vedtak: T, tx: TransactionContext) -> Unit,
        lagreRevurdering: (revurdering: IverksattRevurdering, tx: TransactionContext) -> Unit,
        annullerKontrollsamtale: (sakId: UUID, tx: TransactionContext) -> Unit,
        statistikkObservers: () -> List<StatistikkEventObserver>,
    ): Either<KunneIkkeFerdigstilleIverksettelsestransaksjon, IverksattRevurdering>
}
