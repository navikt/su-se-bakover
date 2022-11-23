package no.nav.su.se.bakover.domain.sak.iverksett

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrHandle
import arrow.core.left
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.avkorting.oppdaterUteståendeAvkortingVedIverksettelse
import no.nav.su.se.bakover.domain.kontrollsamtale.UgyldigStatusovergang
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingKlargjortForOversendelse
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.sak.lagUtbetalingForOpphør
import no.nav.su.se.bakover.domain.sak.simulerUtbetaling
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.domain.statistikk.notify
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.UUID

private val log = LoggerFactory.getLogger("IverksettOpphørtRevurdering")

fun Sak.iverksettOpphørtRevurdering(
    revurderingId: UUID,
    attestant: NavIdentBruker.Attestant,
    clock: Clock,
    simuler: (utbetaling: Utbetaling.UtbetalingForSimulering, periode: Periode) -> Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling>,
): Either<KunneIkkeIverksetteRevurdering, IverksettOpphørtRevurderingResponse> {
    val revurdering = hentRevurdering(revurderingId)
        .getOrHandle { return KunneIkkeIverksetteRevurdering.FantIkkeRevurdering.left() }

    if (avventerKravgrunnlag()) {
        return KunneIkkeIverksetteRevurdering.SakHarRevurderingerMedÅpentKravgrunnlagForTilbakekreving.left()
    }

    if (revurdering !is RevurderingTilAttestering.Opphørt) {
        return KunneIkkeIverksetteRevurdering.UgyldigTilstand(
            fra = revurdering::class,
            til = IverksattRevurdering::class,
        ).left()
    }

    return revurdering.tilIverksatt(
        attestant = attestant,
        hentOpprinneligAvkorting = { uteståendeAvkorting },
        clock = clock,
    ).mapLeft {
        when (it) {
            RevurderingTilAttestering.KunneIkkeIverksetteRevurdering.AttestantOgSaksbehandlerKanIkkeVæreSammePerson -> KunneIkkeIverksetteRevurdering.AttestantOgSaksbehandlerKanIkkeVæreSammePerson
            RevurderingTilAttestering.KunneIkkeIverksetteRevurdering.HarAlleredeBlittAvkortetAvEnAnnen -> KunneIkkeIverksetteRevurdering.HarAlleredeBlittAvkortetAvEnAnnen
            RevurderingTilAttestering.KunneIkkeIverksetteRevurdering.HarBlittAnnullertAvEnAnnen -> KunneIkkeIverksetteRevurdering.HarAlleredeBlittAvkortetAvEnAnnen
        }
    }.flatMap { iverksattRevurdering ->
        lagUtbetalingForOpphør(
            opphørsperiode = revurdering.opphørsperiodeForUtbetalinger,
            behandler = attestant,
            clock = clock,
        ).let {
            simulerUtbetaling(
                utbetalingForSimulering = it,
                periode = revurdering.opphørsperiodeForUtbetalinger,
                simuler = simuler,
                kontrollerMotTidligereSimulering = revurdering.simulering,
                clock = clock,
            )
        }.mapLeft {
            KunneIkkeIverksetteRevurdering.KunneIkkeUtbetale(UtbetalingFeilet.KunneIkkeSimulere(it))
        }.map { simulertUtbetaling ->
            VedtakSomKanRevurderes.from(
                revurdering = iverksattRevurdering,
                utbetalingId = simulertUtbetaling.id,
                clock = clock,
            ).let { vedtak ->
                IverksettOpphørtRevurderingResponse(
                    sak = copy(
                        revurderinger = revurderinger.filterNot { it.id == revurderingId } + iverksattRevurdering,
                        vedtakListe = vedtakListe.filterNot { it.id == vedtak.id } + vedtak,
                        utbetalinger = utbetalinger.filterNot { it.id == simulertUtbetaling.id } + simulertUtbetaling,
                    ).oppdaterUteståendeAvkortingVedIverksettelse(
                        behandletAvkorting = vedtak.behandling.avkorting,
                    ),
                    vedtak = vedtak,
                    utbetaling = simulertUtbetaling,
                )
            }
        }
    }
}

fun IverksettOpphørtRevurderingResponse.ferdigstillIverksettelseITransaksjon(
    sessionFactory: SessionFactory,
    klargjørUtbetaling: (utbetaling: Utbetaling.SimulertUtbetaling, tx: TransactionContext) -> Either<UtbetalingFeilet, UtbetalingKlargjortForOversendelse<UtbetalingFeilet.Protokollfeil>>,
    lagreVedtak: (vedtak: VedtakSomKanRevurderes.EndringIYtelse.OpphørtRevurdering, tx: TransactionContext) -> Unit,
    lagreRevurdering: (revurdering: IverksattRevurdering.Opphørt, tx: TransactionContext) -> Unit,
    annullerKontrollsamtale: (sakId: UUID, tx: TransactionContext) -> Either<UgyldigStatusovergang, Unit>,
    statistikkObservers: () -> List<StatistikkEventObserver>,
): Either<KunneIkkeFerdigstilleIverksettelsestransaksjon, IverksattRevurdering.Opphørt> {
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
            ).getOrHandle {
                throw IverksettTransactionException(
                    "Kunne ikke opprette utbetaling. Underliggende feil:$it.",
                    KunneIkkeFerdigstilleIverksettelsestransaksjon.KunneIkkeUtbetale(it),
                )
            }
            lagreVedtak(vedtak, tx)

            annullerKontrollsamtale(sak.id, tx)
                .getOrHandle {
                    throw IverksettTransactionException(
                        "Kunne ikke annullere kontrollsamtale. Underliggende feil: $it.",
                        KunneIkkeFerdigstilleIverksettelsestransaksjon.Opphør.KunneIkkeAnnullereKontrollsamtale,
                    )
                }
            lagreRevurdering(vedtak.behandling, tx)

            nyUtbetaling.sendUtbetaling()
                .getOrHandle { feil ->
                    throw IverksettTransactionException(
                        "Kunne ikke publisere utbetaling på køen. Underliggende feil: $feil.",
                        KunneIkkeFerdigstilleIverksettelsestransaksjon.KunneIkkeUtbetale(feil),
                    )
                }

            statistikkhendelser.forEach {
                statistikkObservers().notify(it)
            }

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
                KunneIkkeFerdigstilleIverksettelsestransaksjon.LagringFeilet
            }
        }
    }
}

data class IverksettOpphørtRevurderingResponse(
    val sak: Sak,
    val vedtak: VedtakSomKanRevurderes.EndringIYtelse.OpphørtRevurdering,
    val utbetaling: Utbetaling.SimulertUtbetaling,

) {
    val statistikkhendelser: List<StatistikkEvent> = listOf(
        StatistikkEvent.Stønadsvedtak(vedtak) { sak },
        StatistikkEvent.Behandling.Revurdering.Iverksatt.Opphørt(vedtak),
    )
}
