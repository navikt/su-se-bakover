package no.nav.su.se.bakover.domain.sak

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrHandle
import arrow.core.left
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.common.toNonEmptyList
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingKlargjortForOversendelse
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingsinstruksjonForEtterbetalinger
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.revurdering.AbstraktRevurdering
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.KunneIkkeIverksetteRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.domain.statistikk.notify
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import java.time.Clock
import java.util.UUID
import kotlin.reflect.KClass

fun Sak.iverksettInnvilgetRevurdering(
    revurderingId: UUID,
    attestant: NavIdentBruker.Attestant,
    clock: Clock,
    simuler: (utbetaling: Utbetaling.UtbetalingForSimulering, periode: Periode) -> Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling>,
): Either<KunneIkkeIverksetteInnvilgetRevurdering, IverksettInnvilgetRevurderingResponse> {
    val revurdering = hentRevurdering(revurderingId)
        .getOrHandle { return KunneIkkeIverksetteInnvilgetRevurdering.FantIkkeRevurdering.left() }

    if (avventerKravgrunnlag()) {
        return KunneIkkeIverksetteInnvilgetRevurdering.SakHarRevurderingerMedÅpentKravgrunnlagForTilbakekreving.left()
    }

    if (revurdering !is RevurderingTilAttestering.Innvilget) {
        return KunneIkkeIverksetteInnvilgetRevurdering.UgyldigTilstand(
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
            RevurderingTilAttestering.KunneIkkeIverksetteRevurdering.AttestantOgSaksbehandlerKanIkkeVæreSammePerson -> KunneIkkeIverksetteInnvilgetRevurdering.AttestantOgSaksbehandlerKanIkkeVæreSammePerson
            RevurderingTilAttestering.KunneIkkeIverksetteRevurdering.HarAlleredeBlittAvkortetAvEnAnnen -> KunneIkkeIverksetteInnvilgetRevurdering.HarAlleredeBlittAvkortetAvEnAnnen
            RevurderingTilAttestering.KunneIkkeIverksetteRevurdering.HarBlittAnnullertAvEnAnnen -> KunneIkkeIverksetteInnvilgetRevurdering.HarAlleredeBlittAvkortetAvEnAnnen
        }
    }.flatMap { iverksattRevurdering ->
        lagNyUtbetaling(
            saksbehandler = attestant,
            beregning = iverksattRevurdering.beregning,
            clock = clock,
            utbetalingsinstruksjonForEtterbetaling = UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig,
            uføregrunnlag = when (iverksattRevurdering.sakstype) {
                Sakstype.ALDER -> {
                    null
                }
                Sakstype.UFØRE -> {
                    iverksattRevurdering.vilkårsvurderinger.uføreVilkår()
                        .getOrHandle { throw IllegalStateException("Revurdering uføre: ${iverksattRevurdering.id} mangler uføregrunnlag") }
                        .grunnlag
                        .toNonEmptyList()
                }
            },
        ).let {
            simulerUtbetaling(
                utbetalingForSimulering = it,
                periode = iverksattRevurdering.periode,
                simuler = simuler,
                kontrollerMotTidligereSimulering = iverksattRevurdering.simulering,
                clock = clock,
            )
        }.mapLeft { feil ->
            KunneIkkeIverksetteInnvilgetRevurdering.KunneIkkeUtbetale(UtbetalingFeilet.KunneIkkeSimulere(feil))
        }.map { simulertUtbetaling ->
            VedtakSomKanRevurderes.from(
                revurdering = iverksattRevurdering,
                utbetalingId = simulertUtbetaling.id,
                clock = clock,
            ).let { vedtak ->
                IverksettInnvilgetRevurderingResponse(
                    sak = copy(
                        revurderinger = revurderinger.filterNot { it.id == revurderingId } + iverksattRevurdering,
                        vedtakListe = vedtakListe.filterNot { it.id == vedtak.id } + vedtak,
                        utbetalinger = utbetalinger.filterNot { it.id == simulertUtbetaling.id } + simulertUtbetaling,
                    ),
                    vedtak = vedtak,
                    utbetaling = simulertUtbetaling,
                )
            }
        }
    }
}

fun IverksettInnvilgetRevurderingResponse.ferdigstillIverksettelseITransaksjon(
    sessionFactory: SessionFactory,
    klargjørUtbetaling: (utbetaling: Utbetaling.SimulertUtbetaling, tx: TransactionContext) -> Either<UtbetalingFeilet, UtbetalingKlargjortForOversendelse<UtbetalingFeilet.Protokollfeil>>,
    lagreVedtak: (vedtak: VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRevurdering, tx: TransactionContext) -> Unit,
    lagreRevurdering: (revurdering: IverksattRevurdering.Innvilget, tx: TransactionContext) -> Unit,
    statistikkObservers: () -> List<StatistikkEventObserver>,
): Either<KunneIkkeIverksetteRevurdering, IverksattRevurdering> {
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
                    KunneIkkeIverksetteRevurdering.KunneIkkeUtbetale(it),
                )
            }
            lagreVedtak(vedtak, tx)
            lagreRevurdering(vedtak.behandling, tx)

            nyUtbetaling.sendUtbetaling()
                .getOrHandle { feil ->
                    throw IverksettTransactionException(
                        "Kunne ikke publisere utbetaling på køen. Underliggende feil: $feil.",
                        KunneIkkeIverksetteRevurdering.KunneIkkeUtbetale(feil),
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
                it.feil
            }
            else -> {
                no.nav.su.se.bakover.common.log.error("Ukjent feil:${it.message} ved iverksetting av revurdering ${vedtak.behandling.id}")
                KunneIkkeIverksetteRevurdering.LagringFeilet
            }
        }
    }
}
data class IverksettInnvilgetRevurderingResponse(
    val sak: Sak,
    val vedtak: VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRevurdering,
    val utbetaling: Utbetaling.SimulertUtbetaling,

) {
    val statistikkhendelser: List<StatistikkEvent> = listOf(
        StatistikkEvent.Stønadsvedtak(vedtak) { sak },
        StatistikkEvent.Behandling.Revurdering.Iverksatt.Innvilget(vedtak),
    )
}

sealed interface KunneIkkeIverksetteInnvilgetRevurdering {
    object AttestantOgSaksbehandlerKanIkkeVæreSammePerson : KunneIkkeIverksetteInnvilgetRevurdering
    data class KunneIkkeUtbetale(val utbetalingFeilet: UtbetalingFeilet) : KunneIkkeIverksetteInnvilgetRevurdering
    object FantIkkeRevurdering : KunneIkkeIverksetteInnvilgetRevurdering
    object HarAlleredeBlittAvkortetAvEnAnnen : KunneIkkeIverksetteInnvilgetRevurdering
    object SakHarRevurderingerMedÅpentKravgrunnlagForTilbakekreving : KunneIkkeIverksetteInnvilgetRevurdering

    data class UgyldigTilstand(
        val fra: KClass<out AbstraktRevurdering>,
        val til: KClass<out AbstraktRevurdering>,
    ) : KunneIkkeIverksetteInnvilgetRevurdering
}

private data class IverksettTransactionException(
    override val message: String,
    val feil: KunneIkkeIverksetteRevurdering,
) : RuntimeException(message)
