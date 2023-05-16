package no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.innvilg

import arrow.core.Either
import arrow.core.Nel
import arrow.core.getOrElse
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingKlargjortForOversendelse
import no.nav.su.se.bakover.domain.skatt.Skattedokument
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.domain.statistikk.notify
import no.nav.su.se.bakover.domain.søknadsbehandling.IverksattSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.IverksattSøknadsbehandlingResponse
import no.nav.su.se.bakover.domain.vedtak.KunneIkkeFerdigstilleVedtak
import no.nav.su.se.bakover.domain.vedtak.Stønadsvedtak
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetSøknadsbehandling
import org.slf4j.LoggerFactory

data class IverksattInnvilgetSøknadsbehandlingResponse(
    override val sak: Sak,
    override val vedtak: VedtakInnvilgetSøknadsbehandling,
    val statistikk: Nel<StatistikkEvent>,
    val utbetaling: Utbetaling.SimulertUtbetaling,
) : IverksattSøknadsbehandlingResponse<IverksattSøknadsbehandling.Innvilget> {

    private val log = LoggerFactory.getLogger(this::class.java)

    override val søknadsbehandling = vedtak.behandling

    override fun ferdigstillIverksettelseITransaksjon(
        klargjørUtbetaling: (Utbetaling.SimulertUtbetaling, TransactionContext) -> Either<UtbetalingFeilet, UtbetalingKlargjortForOversendelse<UtbetalingFeilet.Protokollfeil>>,
        sessionFactory: SessionFactory,
        lagreSøknadsbehandling: (IverksattSøknadsbehandling.Innvilget, TransactionContext) -> Unit,
        lagreVedtak: (Vedtak, TransactionContext) -> Unit,
        statistikkObservers: List<StatistikkEventObserver>,
        opprettPlanlagtKontrollsamtale: (VedtakInnvilgetSøknadsbehandling, TransactionContext) -> Unit,
        // disse er kun i bruk for avslag, men den må være med hvis vi ikke skal trekke domenelogikk ut i domenet. På sikt bør disse gjøres asynkront.
        lagreDokument: (Dokument.MedMetadata, TransactionContext) -> Unit,
        lukkOppgave: (IverksattSøknadsbehandling.Avslag) -> Either<KunneIkkeFerdigstilleVedtak.KunneIkkeLukkeOppgave, Unit>,
        genererOgLagreSkattedokument: (Stønadsvedtak, TransactionContext) -> Unit,
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
            // TODO: finn ut hvor vi skal lage skattedokumentet

            // Så fremt denne ikke kaster ønsker vi å gå igjennom med iverksettingen.
            opprettPlanlagtKontrollsamtale(vedtak, tx)
            nyUtbetaling.sendUtbetaling().getOrElse { feil ->
                throw RuntimeException(
                    "Kunne ikke innvilge søknadsbehandling ${søknadsbehandling.id}. Underliggende feil: $feil.",
                )
            }
        }
        log.info("Iverksatt innvilgelse for søknadsbehandling: ${søknadsbehandling.id}, vedtak: ${vedtak.id}")
        statistikkObservers.notify(statistikk)
    }
}
