package no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.innvilg

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.continuations.either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingsinstruksjonForEtterbetalinger
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.sak.lagNyUtbetaling
import no.nav.su.se.bakover.domain.sak.simulerUtbetaling
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.søknadsbehandling.IverksattSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingTilAttestering
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.KunneIkkeIverksetteSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.validerOverlappendeStønadsperioder
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import org.slf4j.LoggerFactory
import java.time.Clock

private val log = LoggerFactory.getLogger("IverksettInnvilgetSøknadsbehandling.kt")

/**
 * Innvilger søknadsbehandlingen uten sideeffekter.
 * IO: Utbetalingen simules.
 *
 * Begrensninger:
 * - Det kan ikke finnes åpne kravgrunnlag på saken.
 * - Dersom det finnes uhåndterte avkortingsvarsler på saken, må disse håndteres av denne behandlingen i sin helhet.
 * - Kan ikke føre til feilutbetaling (verifiseres vha. simulering og kontrollsimulering)
 * - Stønadsperioden kan ikke overlappe tidligere stønadsperioder, med noen unntak:
 *     - Opphørte måneder som ikke har ført til utbetaling kan "overskrives".
 *     - Opphørte måneder med feilutbetaling som har blitt 100% tilbakekrevet kan "overskrives".
 */
internal fun Sak.iverksettInnvilgetSøknadsbehandling(
    søknadsbehandling: SøknadsbehandlingTilAttestering.Innvilget,
    attestering: Attestering.Iverksatt,
    clock: Clock,
    simulerUtbetaling: (utbetalingForSimulering: Utbetaling.UtbetalingForSimulering, periode: Periode) -> Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling>,
): Either<KunneIkkeIverksetteSøknadsbehandling, IverksattInnvilgetSøknadsbehandlingResponse> {
    require(this.søknadsbehandlinger.any { it == søknadsbehandling })

    either.eager {
        validerKravgrunnlag().bind()
        validerAvkorting(søknadsbehandling).bind()
        validerFeilutbetalinger(søknadsbehandling).bind()
        validerGjeldendeVedtak(søknadsbehandling, clock).bind()
    }.onLeft {
        return it.left()
    }

    val iverksattBehandling = søknadsbehandling.tilIverksatt(attestering)
    val simulertUtbetaling = this.lagNyUtbetaling(
        saksbehandler = attestering.attestant,
        beregning = iverksattBehandling.beregning,
        clock = clock,
        utbetalingsinstruksjonForEtterbetaling = UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig,
        uføregrunnlag = hentUføregrunnlag(iverksattBehandling),
    ).let {
        this.simulerUtbetaling(
            utbetalingForSimulering = it,
            periode = iverksattBehandling.periode,
            simuler = simulerUtbetaling,
            kontrollerMotTidligereSimulering = iverksattBehandling.simulering,
        )
    }.getOrElse {
        log.error("Kunne ikke iverksette innvilget søknadsbehandling ${iverksattBehandling.id}. Underliggende feil:$it.")
        return KunneIkkeIverksetteSøknadsbehandling.KunneIkkeUtbetale(UtbetalingFeilet.KunneIkkeSimulere(it)).left()
    }
    val vedtak = VedtakSomKanRevurderes.from(
        søknadsbehandling = iverksattBehandling,
        utbetalingId = simulertUtbetaling.id,
        clock = clock,
    )

    val oppdatertSak = this.copy(
        vedtakListe = this.vedtakListe + vedtak,
        søknadsbehandlinger = this.søknadsbehandlinger.filterNot { iverksattBehandling.id == it.id } + iverksattBehandling,
        utbetalinger = this.utbetalinger + simulertUtbetaling,
        uteståendeAvkorting = when (iverksattBehandling.avkorting) {
            is AvkortingVedSøknadsbehandling.Iverksatt.AvkortUtestående -> Avkortingsvarsel.Ingen
            is AvkortingVedSøknadsbehandling.Iverksatt.IngenUtestående,
            is AvkortingVedSøknadsbehandling.Iverksatt.KanIkkeHåndtere,
            -> uteståendeAvkorting
        },
    )
    return IverksattInnvilgetSøknadsbehandlingResponse(
        sak = oppdatertSak,
        vedtak = vedtak,
        statistikk = nonEmptyListOf(
            StatistikkEvent.Behandling.Søknad.Iverksatt.Innvilget(vedtak),
            StatistikkEvent.Stønadsvedtak(vedtak) { oppdatertSak },
        ),
        utbetaling = simulertUtbetaling,
    ).right()
}

private fun Sak.validerGjeldendeVedtak(
    søknadsbehandling: SøknadsbehandlingTilAttestering.Innvilget,
    clock: Clock,
): Either<KunneIkkeIverksetteSøknadsbehandling.OverlappendeStønadsperiode, Unit> {
    return this.validerOverlappendeStønadsperioder(søknadsbehandling.periode, clock).mapLeft {
        KunneIkkeIverksetteSøknadsbehandling.OverlappendeStønadsperiode(it)
    }
}

private fun hentUføregrunnlag(
    iverksattBehandling: IverksattSøknadsbehandling.Innvilget,
): NonEmptyList<Grunnlag.Uføregrunnlag>? {
    return when (iverksattBehandling.sakstype) {
        Sakstype.ALDER -> {
            null
        }

        Sakstype.UFØRE -> {
            iverksattBehandling.vilkårsvurderinger.uføreVilkår().getOrElse {
                throw IllegalStateException("Søknadsbehandling uføre: ${iverksattBehandling.id} mangler uføregrunnlag")
            }.grunnlag.toNonEmptyList()
        }
    }
}

/**
 * Sjekker kun saksbehandlers simulering.
 */
private fun validerFeilutbetalinger(søknadsbehandling: SøknadsbehandlingTilAttestering.Innvilget): Either<KunneIkkeIverksetteSøknadsbehandling.SimuleringFørerTilFeilutbetaling, Unit> {
    if (søknadsbehandling.simulering.harFeilutbetalinger()) {
        log.warn("Kan ikke iverksette søknadsbehandling ${søknadsbehandling.id} hvor simulering inneholder feilutbetalinger. Dette er kun en nødbrems for tilfeller som i utgangspunktet skal være håndtert og forhindret av andre mekanismer. Se sikkerlogg for simuleringsdetaljer.")
        sikkerLogg.warn("Kan ikke iverksette søknadsbehandling ${søknadsbehandling.id} hvor simulering inneholder feilutbetalinger. Dette er kun en nødbrems for tilfeller som i utgangspunktet skal være håndtert og forhindret av andre mekanismer. Simulering: ${søknadsbehandling.simulering}")
        return KunneIkkeIverksetteSøknadsbehandling.SimuleringFørerTilFeilutbetaling.left()
    }
    return Unit.right()
}

private fun Sak.validerKravgrunnlag(): Either<KunneIkkeIverksetteSøknadsbehandling.SakHarRevurderingerMedÅpentKravgrunnlagForTilbakekreving, Unit> {
    return if (avventerKravgrunnlag()) {
        KunneIkkeIverksetteSøknadsbehandling.SakHarRevurderingerMedÅpentKravgrunnlagForTilbakekreving.left()
    } else {
        Unit.right()
    }
}

private fun Sak.validerAvkorting(
    søknadsbehandling: SøknadsbehandlingTilAttestering.Innvilget,
): Either<KunneIkkeIverksetteSøknadsbehandling.AvkortingErUfullstendig, Unit> {
    val uteståendeAvkortingPåSak = if (uteståendeAvkorting is Avkortingsvarsel.Ingen) {
        null
    } else {
        uteståendeAvkorting as Avkortingsvarsel.Utenlandsopphold.SkalAvkortes
    }
    // TODO jah: Mulig å flytte den biten som kun angår behandlingen inn i [SøknadsbehandlingTilAttestering.Innvilget.tilIverksatt], mens det saksnære bør ligge her (som f.eks. at tilstander og IDer er like)
    return when (val a = søknadsbehandling.avkorting) {
        is AvkortingVedSøknadsbehandling.Håndtert.AvkortUtestående -> {
            val avkortingsvarselPåBehandling = a.avkortingsvarsel
            if (uteståendeAvkortingPåSak == null) {
                throw IllegalStateException("Prøver å iverksette søknadsbehandling ${søknadsbehandling.id} med utestående avkorting uten at det finnes noe å avkorte på saken for avkortingsvarsel ${avkortingsvarselPåBehandling.id}")
            }
            if (avkortingsvarselPåBehandling != uteståendeAvkortingPåSak) {
                throw IllegalStateException("Prøver å iverksette søknadsbehandling ${søknadsbehandling.id} hvor søknadsbehandlingens avkorting ${avkortingsvarselPåBehandling.id} er forskjellig fra sakens avkorting ${uteståendeAvkortingPåSak.id}")
            }
            if (!avkortingsvarselPåBehandling.fullstendigAvkortetAv(søknadsbehandling.beregning)) {
                KunneIkkeIverksetteSøknadsbehandling.AvkortingErUfullstendig.left()
            } else {
                Unit.right()
            }
        }

        is AvkortingVedSøknadsbehandling.Håndtert.IngenUtestående -> {
            if (uteståendeAvkortingPåSak != null) {
                throw IllegalStateException("Prøver å iverksette søknadsbehandling ${søknadsbehandling.id} uten å håndtere utestående avkorting for sak $id")
            }
            Unit.right()
        }

        is AvkortingVedSøknadsbehandling.Håndtert.KanIkkeHåndtere -> {
            throw IllegalStateException("Søknadsbehandling ${søknadsbehandling.id} i tilstand ${søknadsbehandling::class} skulle hatt håndtert eventuell avkorting.")
        }
    }
}
