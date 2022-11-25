package no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.innvilg

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.toNonEmptyList
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingsinstruksjonForEtterbetalinger
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.sak.lagNyUtbetaling
import no.nav.su.se.bakover.domain.sak.simulerUtbetaling
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.KunneIkkeIverksetteSøknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import org.slf4j.LoggerFactory
import java.time.Clock

private val log = LoggerFactory.getLogger("IverksettInnvilgetSøknadsbehandling.kt")

internal fun Sak.iverksettInnvilgetSøknadsbehandling(
    søknadsbehandling: Søknadsbehandling.TilAttestering.Innvilget,
    attestering: Attestering.Iverksatt,
    clock: Clock,
    simulerUtbetaling: (utbetalingForSimulering: Utbetaling.UtbetalingForSimulering, periode: Periode) -> Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling>,
): Either<KunneIkkeIverksetteSøknadsbehandling, IverksattInnvilgetSøknadsbehandlingResponse> {
    if (avventerKravgrunnlag()) {
        return KunneIkkeIverksetteSøknadsbehandling.SakHarRevurderingerMedÅpentKravgrunnlagForTilbakekreving.left()
    }

    søknadsbehandling.kastHvisFeilutbetalinger()

    val iverksattBehandling = søknadsbehandling.tilIverksatt(attestering)

    val simulertUtbetaling = this.lagNyUtbetaling(
        saksbehandler = attestering.attestant,
        beregning = iverksattBehandling.beregning,
        clock = clock,
        utbetalingsinstruksjonForEtterbetaling = UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig,
        uføregrunnlag = when (iverksattBehandling.sakstype) {
            Sakstype.ALDER -> {
                null
            }

            Sakstype.UFØRE -> {
                iverksattBehandling.vilkårsvurderinger.uføreVilkår()
                    .getOrHandle { throw IllegalStateException("Søknadsbehandling uføre: ${iverksattBehandling.id} mangler uføregrunnlag") }.grunnlag.toNonEmptyList()
            }
        },
    ).let {
        this.simulerUtbetaling(
            utbetalingForSimulering = it,
            periode = iverksattBehandling.periode,
            simuler = simulerUtbetaling,
            kontrollerMotTidligereSimulering = iverksattBehandling.simulering,
            clock = clock,
        )
    }.getOrHandle {
        log.error("Kunne ikke iverksette innvilget søknadsbehandling ${iverksattBehandling.id}. Underliggende feil:$it.")
        return KunneIkkeIverksetteSøknadsbehandling.KunneIkkeUtbetale(UtbetalingFeilet.KunneIkkeSimulere(it)).left()
    }
    val vedtak = VedtakSomKanRevurderes.fromSøknadsbehandling(
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

private fun Søknadsbehandling.TilAttestering.Innvilget.kastHvisFeilutbetalinger() {
    if (this.simulering.harFeilutbetalinger()) {
        throw IllegalStateException("Kan ikke iverksette søknadsbehandling hvor simulering inneholder feilutbetalinger. Dette er kun en nødbrems for tilfeller som i utgangspunktet skal være håndtert og forhindret av andre mekanismer.")
    }
}
