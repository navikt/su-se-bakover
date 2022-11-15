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
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.domain.statistikk.notify
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import java.time.Clock
import java.util.UUID
import kotlin.reflect.KClass

fun Sak.iverksettInnvilget(
    revurderingId: UUID,
    attestant: NavIdentBruker.Attestant,
    clock: Clock,
    simuler: (utbetaling: Utbetaling.UtbetalingForSimulering, periode: Periode) -> Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling>,
    sessionFactory: SessionFactory,
    klargjørUtbetaling: (utbetaling: Utbetaling.SimulertUtbetaling, transactionContext: TransactionContext) -> Either<UtbetalingFeilet, UtbetalingKlargjortForOversendelse<UtbetalingFeilet.Protokollfeil>>,
    lagreVedtak: (vedtak: VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRevurdering, transactionContext: TransactionContext) -> Unit,
    lagreRevurdering: (revurdering: IverksattRevurdering.Innvilget, transactionContext: TransactionContext) -> Unit,
    observers: List<StatistikkEventObserver>,
    hentSak: (id: UUID) -> Sak,
): Either<KunneIkkeIverksetteInnvilgetRevurdering, IverksattRevurdering.Innvilget> {
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
            is RevurderingTilAttestering.KunneIkkeIverksetteRevurdering.KunneIkkeUtbetale -> KunneIkkeIverksetteInnvilgetRevurdering.KunneIkkeUtbetale(
                it.utbetalingFeilet,
            )
        }
    }.flatMap { iverksattRevurdering ->
        Either.catch {
            val simulertUtbetaling = lagNyUtbetaling(
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
            }.getOrHandle { feil ->
                throw IverksettTransactionException(
                    "Kunne ikke opprette utbetaling. Underliggende feil:$feil.",
                    KunneIkkeIverksetteInnvilgetRevurdering.KunneIkkeUtbetale(UtbetalingFeilet.KunneIkkeSimulere(feil)),
                )
            }
            sessionFactory.withTransactionContext { tx ->
                /**
                 * OBS: Det er kun exceptions som vil føre til at transaksjonen ruller tilbake.
                 * Hvis funksjonene returnerer Left/null o.l. vil transaksjonen gå igjennom. De tilfellene må håndteres eksplisitt per funksjon.
                 * Det er også viktig at publiseringen av utbetalingen er det siste som skjer i blokka.
                 * Alt som ikke skal påvirke utfallet av iverksettingen skal flyttes ut av blokka. E.g. statistikk.
                 */
                val nyUtbetaling = klargjørUtbetaling(
                    simulertUtbetaling,
                    tx,
                ).getOrHandle {
                    throw IverksettTransactionException(
                        "Kunne ikke opprette utbetaling. Underliggende feil:$it.",
                        KunneIkkeIverksetteInnvilgetRevurdering.KunneIkkeUtbetale(it),
                    )
                }
                val vedtak = VedtakSomKanRevurderes.from(
                    revurdering = iverksattRevurdering,
                    utbetalingId = nyUtbetaling.utbetaling.id,
                    clock = clock,
                )

                lagreVedtak(
                    vedtak,
                    tx,
                )
                lagreRevurdering(
                    iverksattRevurdering,
                    tx,
                )
                nyUtbetaling.sendUtbetaling()
                    .getOrHandle { feil ->
                        throw IverksettTransactionException(
                            "Kunne ikke publisere utbetaling på køen. Underliggende feil: $feil.",
                            KunneIkkeIverksetteInnvilgetRevurdering.KunneIkkeUtbetale(feil),
                        )
                    }
                vedtak
            }
        }.mapLeft {
            when (it) {
                is IverksettTransactionException -> it.feil
                else -> {
                    no.nav.su.se.bakover.common.log.error("Ukjent feil:${it.message} ved iverksetting av revurdering ${revurdering.id}")
                    KunneIkkeIverksetteInnvilgetRevurdering.LagringFeilet
                }
            }
        }.map { vedtak ->
            // TODO jah: Vi har gjort endringer på saken underveis - endret regulering, ny utbetaling og nytt vedtak - uten at selve saken blir oppdatert underveis. Når saken returnerer en oppdatert versjon av seg selv for disse tilfellene kan vi fjerne det ekstra kallet til hentSak.
            observers.notify(StatistikkEvent.Stønadsvedtak(vedtak) { hentSak(id) })
            observers.notify(StatistikkEvent.Behandling.Revurdering.Iverksatt.Innvilget(vedtak))
            iverksattRevurdering
        }
    }
}

private data class IverksettTransactionException(
    override val message: String,
    val feil: KunneIkkeIverksetteInnvilgetRevurdering,
) : RuntimeException(message)

sealed interface KunneIkkeIverksetteInnvilgetRevurdering {
    object AttestantOgSaksbehandlerKanIkkeVæreSammePerson : KunneIkkeIverksetteInnvilgetRevurdering
    data class KunneIkkeUtbetale(val utbetalingFeilet: UtbetalingFeilet) : KunneIkkeIverksetteInnvilgetRevurdering
    object FantIkkeRevurdering : KunneIkkeIverksetteInnvilgetRevurdering
    object LagringFeilet : KunneIkkeIverksetteInnvilgetRevurdering
    object HarAlleredeBlittAvkortetAvEnAnnen : KunneIkkeIverksetteInnvilgetRevurdering
    object SakHarRevurderingerMedÅpentKravgrunnlagForTilbakekreving : KunneIkkeIverksetteInnvilgetRevurdering

    data class UgyldigTilstand(
        val fra: KClass<out AbstraktRevurdering>,
        val til: KClass<out AbstraktRevurdering>,
    ) : KunneIkkeIverksetteInnvilgetRevurdering
}
