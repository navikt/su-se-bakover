package no.nav.su.se.bakover.domain.søknadsbehandling.iverksett

import arrow.core.Either
import dokument.domain.Dokument
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.domain.søknadsbehandling.IverksattSøknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetSøknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.VedtakIverksattSøknadsbehandling
import no.nav.su.se.bakover.oppgave.domain.KunneIkkeLukkeOppgave
import vedtak.domain.Vedtak
import økonomi.domain.utbetaling.KunneIkkeKlaregjøreUtbetaling
import økonomi.domain.utbetaling.Utbetaling
import økonomi.domain.utbetaling.UtbetalingFeilet
import økonomi.domain.utbetaling.UtbetalingKlargjortForOversendelse

interface IverksattSøknadsbehandlingResponse<T : IverksattSøknadsbehandling> {
    val sak: Sak
    val vedtak: VedtakIverksattSøknadsbehandling
    val søknadsbehandling: T

    fun ferdigstillIverksettelseITransaksjon(
        klargjørUtbetaling: (Utbetaling.SimulertUtbetaling, TransactionContext) -> Either<KunneIkkeKlaregjøreUtbetaling, UtbetalingKlargjortForOversendelse<UtbetalingFeilet.Protokollfeil>>,
        sessionFactory: SessionFactory,
        lagreSøknadsbehandling: (T, TransactionContext) -> Unit,
        lagreVedtak: (Vedtak, TransactionContext) -> Unit,
        statistikkObservers: List<StatistikkEventObserver>,
        opprettPlanlagtKontrollsamtale: (VedtakInnvilgetSøknadsbehandling, TransactionContext) -> Unit,
        lagreDokument: (Dokument.MedMetadata, TransactionContext) -> Unit,
        lukkOppgave: (IverksattSøknadsbehandling.Avslag) -> Either<KunneIkkeLukkeOppgave, Unit>,
        genererOgLagreSkattedokument: (VedtakIverksattSøknadsbehandling, TransactionContext) -> Unit,
    )
}
