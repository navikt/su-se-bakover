package no.nav.su.se.bakover.domain.søknadsbehandling.iverksett

import arrow.core.Either
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingKlargjortForOversendelse
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.KunneIkkeFerdigstilleVedtak
import no.nav.su.se.bakover.domain.vedtak.Stønadsvedtak
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes

interface IverksattSøknadsbehandlingResponse<T : Søknadsbehandling.Iverksatt> {
    val sak: Sak
    val vedtak: Stønadsvedtak
    val søknadsbehandling: T

    fun ferdigstillIverksettelseITransaksjon(
        klargjørUtbetaling: (Utbetaling.SimulertUtbetaling, TransactionContext) -> Either<UtbetalingFeilet, UtbetalingKlargjortForOversendelse<UtbetalingFeilet.Protokollfeil>>,
        sessionFactory: SessionFactory,
        lagreSøknadsbehandling: (T, TransactionContext) -> Unit,
        lagreVedtak: (Vedtak, TransactionContext) -> Unit,
        statistikkObservers: List<StatistikkEventObserver>,
        lagreDokument: (Dokument.MedMetadata, TransactionContext) -> Unit,
        lukkOppgave: (Søknadsbehandling.Iverksatt.Avslag) -> Either<KunneIkkeFerdigstilleVedtak.KunneIkkeLukkeOppgave, Unit>,
        opprettPlanlagtKontrollsamtale: (VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling, TransactionContext) -> Unit,
    )
}
