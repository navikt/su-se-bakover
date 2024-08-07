@file:Suppress("PackageDirectoryMismatch")
// Må ligge i samme pakke som IverksattSøknadsbehandlingResponse (siden det er et sealed interface), men trenger ikke ligge i samme mappe.

package no.nav.su.se.bakover.domain.søknadsbehandling.iverksett

import arrow.core.Either
import dokument.domain.Dokument
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.oppgave.OppdaterOppgaveInfo
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.domain.statistikk.notify
import no.nav.su.se.bakover.domain.søknadsbehandling.IverksattSøknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.Avslagsvedtak
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetSøknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.VedtakIverksattSøknadsbehandling
import no.nav.su.se.bakover.oppgave.domain.KunneIkkeLukkeOppgave
import org.slf4j.LoggerFactory
import vedtak.domain.Vedtak
import økonomi.domain.utbetaling.KunneIkkeKlaregjøreUtbetaling
import økonomi.domain.utbetaling.Utbetaling
import økonomi.domain.utbetaling.UtbetalingFeilet
import økonomi.domain.utbetaling.UtbetalingKlargjortForOversendelse

data class IverksattAvslåttSøknadsbehandlingResponse(
    override val sak: Sak,
    val dokument: Dokument.MedMetadata,
    override val vedtak: Avslagsvedtak,
    val statistikkhendelse: StatistikkEvent.Behandling.Søknad.Iverksatt.Avslag,
    val oppgaveSomSkalLukkes: OppgaveId,
) : IverksattSøknadsbehandlingResponse<IverksattSøknadsbehandling.Avslag> {
    private val log = LoggerFactory.getLogger(this::class.java)

    override val søknadsbehandling = vedtak.behandling

    /**
     * Utfører alle sideeffektene for iverksettelse av en avslått søknadsbehandling.
     * Merk at for avslag sender vi ingenting til oppdrag, slik at vi ikke kan basere oss på asynk-jobben når vi mottar kvittering.
     */
    override fun ferdigstillIverksettelseITransaksjon(
        klargjørUtbetaling: (Utbetaling.SimulertUtbetaling, TransactionContext) -> Either<KunneIkkeKlaregjøreUtbetaling, UtbetalingKlargjortForOversendelse<UtbetalingFeilet.Protokollfeil>>,
        sessionFactory: SessionFactory,
        lagreSøknadsbehandling: (IverksattSøknadsbehandling.Avslag, TransactionContext) -> Unit,
        lagreVedtak: (Vedtak, TransactionContext) -> Unit,
        statistikkObservers: List<StatistikkEventObserver>,
        // Denne er kun brukt ved innvilgelse, men må være med i interfacet for slippe å ha denne domenelogikken i servicelaget. På sikt bør denne gjøres asynkront.
        opprettPlanlagtKontrollsamtale: (VedtakInnvilgetSøknadsbehandling, TransactionContext) -> Unit,
        lagreDokument: (Dokument.MedMetadata, TransactionContext) -> Unit,
        lukkOppgave: (IverksattSøknadsbehandling.Avslag, OppdaterOppgaveInfo.TilordnetRessurs) -> Either<KunneIkkeLukkeOppgave, Unit>,
        genererOgLagreSkattedokument: (VedtakIverksattSøknadsbehandling, TransactionContext) -> Unit,
    ) {
        sessionFactory.withTransactionContext { tx ->
            /**
             * OBS: Det er kun exceptions som vil føre til at transaksjonen ruller tilbake.
             * Hvis funksjonene returnerer Left/null o.l. vil transaksjonen gå igjennom. De tilfellene må håndteres eksplisitt per funksjon.
             * Det er også viktig at publiseringen av utbetalingen er det siste som skjer i blokka.
             * Alt som ikke skal påvirke utfallet av iverksettingen skal flyttes ut av blokka. E.g. send statistikk og lukk oppgave.
             */
            lagreSøknadsbehandling(søknadsbehandling, tx)
            lagreVedtak(vedtak, tx)
            lagreDokument(dokument, tx)
            genererOgLagreSkattedokument(vedtak, tx)
        }
        log.info("Iverksatt avslag for søknadsbehandling: ${søknadsbehandling.id}, vedtak: ${vedtak.id}")
        statistikkObservers.notify(statistikkhendelse)
        lukkOppgave(
            søknadsbehandling,
            OppdaterOppgaveInfo.TilordnetRessurs.NavIdent(vedtak.attestant.navIdent),
        ).mapLeft {
            log.error("Lukking av oppgave ${søknadsbehandling.oppgaveId} for behandlingId: ${søknadsbehandling.id} feilet. Må ryddes opp manuelt.")
        }
    }
}
