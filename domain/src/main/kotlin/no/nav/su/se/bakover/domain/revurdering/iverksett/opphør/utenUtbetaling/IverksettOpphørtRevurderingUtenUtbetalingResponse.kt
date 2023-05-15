package no.nav.su.se.bakover.domain.revurdering.iverksett.opphør.utenUtbetaling

import arrow.core.Either
import arrow.core.Nel
import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingKlargjortForOversendelse
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.iverksett.IverksettRevurderingResponse
import no.nav.su.se.bakover.domain.revurdering.iverksett.IverksettTransactionException
import no.nav.su.se.bakover.domain.revurdering.iverksett.KunneIkkeFerdigstilleIverksettelsestransaksjon
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.domain.statistikk.notify
import no.nav.su.se.bakover.domain.vedtak.KunneIkkeFerdigstilleVedtak
import no.nav.su.se.bakover.domain.vedtak.Opphørsvedtak
import no.nav.su.se.bakover.domain.vedtak.VedtakOpphørAvkorting
import org.slf4j.LoggerFactory
import java.util.UUID

private val log = LoggerFactory.getLogger("IverksettOpphørtRevurderingResponse")

data class IverksettOpphørtRevurderingUtenUtbetalingResponse(
    override val sak: Sak,
    override val vedtak: VedtakOpphørAvkorting,
    val dokument: Dokument.MedMetadata,
) : IverksettRevurderingResponse<Opphørsvedtak> {

    override val utbetaling: Utbetaling.SimulertUtbetaling? = null

    override val statistikkhendelser: Nel<StatistikkEvent> = nonEmptyListOf(
        StatistikkEvent.Stønadsvedtak(vedtak) { sak },
        StatistikkEvent.Behandling.Revurdering.Iverksatt.Opphørt(vedtak),
    )

    override fun ferdigstillIverksettelseITransaksjon(
        sessionFactory: SessionFactory,
        // Brukes kun for utbetalingstilfellene.
        klargjørUtbetaling: (utbetaling: Utbetaling.SimulertUtbetaling, tx: TransactionContext) -> Either<UtbetalingFeilet, UtbetalingKlargjortForOversendelse<UtbetalingFeilet.Protokollfeil>>,
        lagreVedtak: (vedtak: Opphørsvedtak, tx: TransactionContext) -> Unit,
        lagreRevurdering: (revurdering: IverksattRevurdering, tx: TransactionContext) -> Unit,
        annullerKontrollsamtale: (sakId: UUID, tx: TransactionContext) -> Unit,
        statistikkObservers: () -> List<StatistikkEventObserver>,
        lagreDokument: (Dokument.MedMetadata, TransactionContext) -> Unit,
        lukkOppgave: (IverksattRevurdering.Opphørt) -> Either<KunneIkkeFerdigstilleVedtak.KunneIkkeLukkeOppgave, Unit>,
    ): Either<KunneIkkeFerdigstilleIverksettelsestransaksjon, IverksattRevurdering> {
        return Either.catch {
            sessionFactory.withTransactionContext { tx ->
                /**
                 * OBS: Det er kun exceptions som vil føre til at transaksjonen ruller tilbake.
                 * Hvis funksjonene returnerer Left/null o.l. vil transaksjonen gå igjennom. De tilfellene må håndteres eksplisitt per funksjon.
                 * Alt som ikke skal påvirke utfallet av iverksettingen skal flyttes ut av blokka. E.g. statistikk.
                 */
                lagreVedtak(vedtak, tx)
                annullerKontrollsamtale(sak.id, tx)
                lagreRevurdering(vedtak.behandling, tx)
                lagreDokument(dokument, tx)
                vedtak.behandling
            }
        }.onRight { revurdering ->
            lukkOppgave(revurdering).mapLeft {
                log.error("Lukking av oppgave ${revurdering.oppgaveId} for revurderingId: ${revurdering.id} feilet. Må ryddes opp manuelt.")
            }
            statistikkObservers().notify(statistikkhendelser)
        }.mapLeft {
            when (it) {
                is IverksettTransactionException -> {
                    log.error("Feil ved iverksetting av revurdering ${vedtak.behandling.id}", it)
                    it.feil
                }
                else -> {
                    log.error("Ukjent feil ved iverksetting av revurdering ${vedtak.behandling.id}", it)
                    KunneIkkeFerdigstilleIverksettelsestransaksjon.UkjentFeil(it)
                }
            }
        }
    }
}
