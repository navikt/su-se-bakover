package no.nav.su.se.bakover.domain.revurdering.iverksett.innvilg

import arrow.core.Either
import arrow.core.Nel
import arrow.core.getOrElse
import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.iverksett.IverksettRevurderingResponse
import no.nav.su.se.bakover.domain.revurdering.iverksett.IverksettTransactionException
import no.nav.su.se.bakover.domain.revurdering.iverksett.KunneIkkeFerdigstilleIverksettelsestransaksjon
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.domain.statistikk.notify
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetRevurdering
import org.slf4j.LoggerFactory
import økonomi.domain.utbetaling.KunneIkkeKlaregjøreUtbetaling
import økonomi.domain.utbetaling.Utbetaling
import økonomi.domain.utbetaling.UtbetalingFeilet
import økonomi.domain.utbetaling.UtbetalingKlargjortForOversendelse
import java.util.UUID

private val log = LoggerFactory.getLogger("IverksettInnvilgetRevurderingResponse")

data class IverksettInnvilgetRevurderingResponse(
    override val sak: Sak,
    override val vedtak: VedtakInnvilgetRevurdering,
    override val utbetaling: Utbetaling.SimulertUtbetaling,

) : IverksettRevurderingResponse<VedtakInnvilgetRevurdering> {
    override val statistikkhendelser: Nel<StatistikkEvent> = nonEmptyListOf(
        StatistikkEvent.Behandling.Revurdering.Iverksatt.Innvilget(vedtak),
    )

    override fun ferdigstillIverksettelseITransaksjon(
        sessionFactory: SessionFactory,
        klargjørUtbetaling: (utbetaling: Utbetaling.SimulertUtbetaling, tx: TransactionContext) -> Either<KunneIkkeKlaregjøreUtbetaling, UtbetalingKlargjortForOversendelse<UtbetalingFeilet.Protokollfeil>>,
        lagreVedtak: (vedtak: VedtakInnvilgetRevurdering, tx: TransactionContext) -> Unit,
        lagreRevurdering: (revurdering: IverksattRevurdering, tx: TransactionContext) -> Unit,
        annullerKontrollsamtale: (sakId: UUID, tx: TransactionContext) -> Unit,
        statistikkObservers: () -> List<StatistikkEventObserver>,
    ): Either<KunneIkkeFerdigstilleIverksettelsestransaksjon, IverksattRevurdering> {
        return Either.catch {
            sessionFactory.withTransactionContext { tx ->
                /**
                 * OBS: Det er kun exceptions som vil føre til at transaksjonen ruller tilbake.
                 * Hvis funksjonene returnerer Left/null o.l. vil transaksjonen gå igjennom. De tilfellene må håndteres eksplisitt per funksjon.
                 * Det er også viktig at publiseringen av utbetalingen er det siste som skjer i blokka.
                 * Alt som ikke skal påvirke utfallet av iverksettingen skal flyttes ut av blokka. E.g. statistikk.
                 */
                val nyUtbetaling = klargjørUtbetaling(
                    utbetaling,
                    tx,
                ).getOrElse {
                    throw IverksettTransactionException(
                        "Kunne ikke opprette utbetaling. Underliggende feil:$it.",
                        KunneIkkeFerdigstilleIverksettelsestransaksjon.KunneIkkeKlargjøreUtbetaling(it),
                    )
                }
                lagreVedtak(vedtak, tx)
                lagreRevurdering(vedtak.behandling, tx)

                nyUtbetaling.sendUtbetaling()
                    .getOrElse { feil ->
                        throw IverksettTransactionException(
                            "Kunne ikke publisere utbetaling på køen. Underliggende feil: $feil.",
                            KunneIkkeFerdigstilleIverksettelsestransaksjon.KunneIkkeLeggeUtbetalingPåKø(feil),
                        )
                    }
                statistikkObservers().notify(statistikkhendelser)

                vedtak.behandling
            }
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
