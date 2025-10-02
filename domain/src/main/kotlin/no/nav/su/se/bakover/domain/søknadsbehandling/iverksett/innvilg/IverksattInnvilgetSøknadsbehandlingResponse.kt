@file:Suppress("PackageDirectoryMismatch")
// Må ligge i samme pakke som IverksattSøknadsbehandlingResponse (siden det er et sealed interface), men trenger ikke ligge i samme mappe.

package no.nav.su.se.bakover.domain.søknadsbehandling.iverksett

import arrow.core.Either
import arrow.core.Nel
import arrow.core.getOrElse
import dokument.domain.Dokument
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.oppgave.OppdaterOppgaveInfo
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.domain.statistikk.notify
import no.nav.su.se.bakover.domain.søknadsbehandling.IverksattSøknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetSøknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.VedtakIverksattSøknadsbehandling
import no.nav.su.se.bakover.oppgave.domain.KunneIkkeLukkeOppgave
import org.slf4j.LoggerFactory
import vedtak.domain.Vedtak
import økonomi.domain.utbetaling.KunneIkkeKlaregjøreUtbetaling
import økonomi.domain.utbetaling.Utbetaling
import økonomi.domain.utbetaling.UtbetalingFeilet
import økonomi.domain.utbetaling.UtbetalingKlargjortForOversendelse

data class IverksattInnvilgetSøknadsbehandlingResponse(
    override val sak: Sak,
    override val vedtak: VedtakInnvilgetSøknadsbehandling,
    val statistikk: Nel<StatistikkEvent>,
    val utbetaling: Utbetaling.SimulertUtbetaling,
) : IverksattSøknadsbehandlingResponse<IverksattSøknadsbehandling.Innvilget> {

    private val log = LoggerFactory.getLogger(this::class.java)

    override val søknadsbehandling = vedtak.behandling

    override fun ferdigstillIverksettelseITransaksjon(
        klargjørUtbetaling: (Utbetaling.SimulertUtbetaling, TransactionContext) -> Either<KunneIkkeKlaregjøreUtbetaling, UtbetalingKlargjortForOversendelse<UtbetalingFeilet.Protokollfeil>>,
        sessionFactory: SessionFactory,
        lagreSøknadsbehandling: (IverksattSøknadsbehandling.Innvilget, TransactionContext) -> Unit,
        lagreVedtak: (Vedtak, TransactionContext) -> Unit,
        statistikkObservers: List<StatistikkEventObserver>,
        opprettPlanlagtKontrollsamtale: (VedtakInnvilgetSøknadsbehandling, TransactionContext) -> Unit,
        // disse er kun i bruk for avslag, men den må være med hvis vi ikke skal trekke domenelogikk ut i domenet. På sikt bør disse gjøres asynkront.
        lagreDokument: (Dokument.MedMetadata, TransactionContext) -> Unit,
        lukkOppgave: (IverksattSøknadsbehandling.Avslag, OppdaterOppgaveInfo.TilordnetRessurs) -> Either<KunneIkkeLukkeOppgave, Unit>,
        genererOgLagreSkattedokument: (VedtakIverksattSøknadsbehandling, TransactionContext) -> Unit,
    ) {
        val søknadsbehandling = vedtak.behandling
        sessionFactory.withTransactionContext { tx ->
            /**
             * OBS: Det er kun exceptions som vil føre til at transaksjonen ruller tilbake.
             * Hvis funksjonene returnerer Left/null o.l. vil transaksjonen gå igjennom. De tilfellene må håndteres eksplisitt per funksjon.
             * Det er også viktig at publiseringen av utbetalingen er det siste som skjer i blokka.
             * Alt som ikke skal påvirke utfallet av iverksettingen skal flyttes ut av blokka. E.g. send statistikk og lukk oppgave.
             */
            lagreSøknadsbehandling(søknadsbehandling, tx)
            val nyUtbetaling = klargjørUtbetaling(utbetaling, tx)
                .getOrElse { feil ->
                    throw RuntimeException(
                        "Kunne ikke innvilge søknadsbehandling ${søknadsbehandling.id}. Underliggende feil:$feil.",
                    )
                }
            lagreVedtak(vedtak, tx)
            genererOgLagreSkattedokument(vedtak, tx)

            // Så fremt denne ikke kaster ønsker vi å gå igjennom med iverksettingen.
            opprettPlanlagtKontrollsamtale(vedtak, tx)
            nyUtbetaling.sendUtbetaling().getOrElse { feil ->
                throw RuntimeException(
                    "Kunne ikke innvilge søknadsbehandling ${søknadsbehandling.id}. Underliggende feil: $feil.",
                )
            }
        }
        log.info("Iverksatt innvilgelse for søknadsbehandling: ${søknadsbehandling.id}, vedtak: ${vedtak.id}")
        statistikkObservers.notify(StatistikkEvent.Behandling.Søknad.Iverksatt.Innvilget(vedtak))
    }
}
