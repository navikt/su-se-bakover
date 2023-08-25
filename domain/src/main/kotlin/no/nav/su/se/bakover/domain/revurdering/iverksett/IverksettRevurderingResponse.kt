package no.nav.su.se.bakover.domain.revurdering.iverksett

import arrow.core.Either
import dokument.domain.Dokument
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingKlargjortForOversendelse
import no.nav.su.se.bakover.domain.oppgave.OppgaveFeil
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.domain.vedtak.Revurderingsvedtak
import java.util.UUID

interface IverksettRevurderingResponse<out T : Revurderingsvedtak> {
    val sak: Sak
    val vedtak: T

    /** Kan være null dersom dette er et rent avkortingsopphør */
    val utbetaling: Utbetaling.SimulertUtbetaling?
    val statistikkhendelser: List<StatistikkEvent>

    /**
     * @param annullerKontrollsamtale er kun relevant ved opphør.
     * @param klargjørUtbetaling er ikke relevant for [no.nav.su.se.bakover.domain.vedtak.VedtakOpphørAvkorting]
     */
    fun ferdigstillIverksettelseITransaksjon(
        sessionFactory: SessionFactory,
        klargjørUtbetaling: (utbetaling: Utbetaling.SimulertUtbetaling, tx: TransactionContext) -> Either<UtbetalingFeilet, UtbetalingKlargjortForOversendelse<UtbetalingFeilet.Protokollfeil>>,
        lagreVedtak: (vedtak: T, tx: TransactionContext) -> Unit,
        lagreRevurdering: (revurdering: IverksattRevurdering, tx: TransactionContext) -> Unit,
        annullerKontrollsamtale: (sakId: UUID, tx: TransactionContext) -> Unit,
        statistikkObservers: () -> List<StatistikkEventObserver>,
        // lagreDokument  og lukkOppgave er kun brukt ved rene avkortinger.
        lagreDokument: (Dokument.MedMetadata, TransactionContext) -> Unit,
        lukkOppgave: (IverksattRevurdering.Opphørt) -> Either<OppgaveFeil.KunneIkkeLukkeOppgave, Unit>,
    ): Either<KunneIkkeFerdigstilleIverksettelsestransaksjon, IverksattRevurdering>
}
