package no.nav.su.se.bakover.domain.revurdering.iverksett

import arrow.core.Either
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.domain.vedtak.Revurderingsvedtak
import økonomi.domain.utbetaling.KunneIkkeKlaregjøreUtbetaling
import økonomi.domain.utbetaling.Utbetaling
import økonomi.domain.utbetaling.UtbetalingFeilet
import økonomi.domain.utbetaling.UtbetalingKlargjortForOversendelse
import java.util.UUID

interface IverksettRevurderingResponse<out T : Revurderingsvedtak> {
    val sak: Sak
    val vedtak: T
    val utbetaling: Utbetaling.SimulertUtbetaling
    val statistikkhendelser: List<StatistikkEvent>

    /**
     * @param annullerKontrollsamtale er kun relevant ved opphør.
     */
    fun ferdigstillIverksettelseITransaksjon(
        sessionFactory: SessionFactory,
        klargjørUtbetaling: (utbetaling: Utbetaling.SimulertUtbetaling, tx: TransactionContext) -> Either<KunneIkkeKlaregjøreUtbetaling, UtbetalingKlargjortForOversendelse<UtbetalingFeilet.Protokollfeil>>,
        lagreVedtak: (vedtak: T, tx: TransactionContext) -> Unit,
        lagreRevurdering: (revurdering: IverksattRevurdering, tx: TransactionContext) -> Unit,
        annullerKontrollsamtale: (sakId: UUID, tx: TransactionContext) -> Unit,
        statistikkObservers: () -> List<StatistikkEventObserver>,
    ): Either<KunneIkkeFerdigstilleIverksettelsestransaksjon, IverksattRevurdering>
}
