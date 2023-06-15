@file:Suppress("PackageDirectoryMismatch")
// Denne må ligge i samme pakke som [Søknadsbehandling], men den trenger ikke ligge i samme mappe.

package no.nav.su.se.bakover.domain.søknadsbehandling

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling
import no.nav.su.se.bakover.domain.avkorting.Avkortingsplan
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import no.nav.su.se.bakover.domain.behandling.AvslagGrunnetBeregning
import no.nav.su.se.bakover.domain.behandling.VurderAvslagGrunnetBeregning
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.BeregningStrategyFactory
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Aldersvurdering
import java.time.Clock

/**
 * Markerer at en søknadsbehandling kan beregnes.
 */
sealed interface KanBeregnes : Søknadsbehandling, KanOppdaterePeriodeGrunnlagVilkår {

    override val aldersvurdering: Aldersvurdering

    fun beregn(
        nySaksbehandler: NavIdentBruker.Saksbehandler,
        begrunnelse: String?,
        clock: Clock,
        satsFactory: SatsFactory,
        uteståendeAvkortingPåSak: Avkortingsvarsel.Utenlandsopphold.SkalAvkortes?,
    ): Either<KunneIkkeBeregne, BeregnetSøknadsbehandling> {
        val beregningStrategyFactory = BeregningStrategyFactory(
            clock = clock,
            satsFactory = satsFactory,
        )
        val utenAvkortingsfradrag: GrunnlagsdataOgVilkårsvurderinger.Søknadsbehandling =
            grunnlagsdataOgVilkårsvurderinger.fjernAvkortingsfradrag()

        val beregningUtenAvkorting = beregningStrategyFactory.beregn(
            grunnlagsdataOgVilkårsvurderinger = utenAvkortingsfradrag,
            begrunnelse = begrunnelse,
            sakstype = this.sakstype,
        )
        return when (uteståendeAvkortingPåSak) {
            null -> Triple(beregningUtenAvkorting, AvkortingVedSøknadsbehandling.IngenAvkorting, utenAvkortingsfradrag)
            else -> medAvkorting(
                uteståendeAvkortingPåSak = uteståendeAvkortingPåSak,
                beregningUtenAvkorting = beregningUtenAvkorting,
                clock = clock,
                utenAvkortingsfradrag = utenAvkortingsfradrag,
                beregningStrategyFactory = beregningStrategyFactory,
                begrunnelse = begrunnelse,
            ).getOrElse { return it.left() }
        }.let { (nyBeregning, avkorting, nyGrunnlagsdataOgVilkårsvurderinger) ->
            val nySøknadsbehandlingshistorikk = this.søknadsbehandlingsHistorikk.leggTilNyHendelse(
                saksbehandlingsHendelse = Søknadsbehandlingshendelse(
                    tidspunkt = Tidspunkt.now(clock),
                    saksbehandler = nySaksbehandler,
                    handling = SøknadsbehandlingsHandling.Beregnet,
                ),
            )
            when (VurderAvslagGrunnetBeregning.vurderAvslagGrunnetBeregning(nyBeregning)) {
                is AvslagGrunnetBeregning.Ja -> tilAvslåttBeregning(
                    saksbehandler = nySaksbehandler,
                    beregning = nyBeregning,
                    grunnlagsdataOgVilkårsvurderinger = nyGrunnlagsdataOgVilkårsvurderinger,
                    søknadsbehandlingshistorikk = nySøknadsbehandlingshistorikk,
                )

                AvslagGrunnetBeregning.Nei -> {
                    tilInnvilgetBeregning(
                        saksbehandler = nySaksbehandler,
                        beregning = nyBeregning,
                        grunnlagsdataOgVilkårsvurderinger = nyGrunnlagsdataOgVilkårsvurderinger,
                        søknadsbehandlingshistorikk = nySøknadsbehandlingshistorikk,
                        avkorting = avkorting,
                    )
                }
            }.right()
        }
    }

    private fun medAvkorting(
        uteståendeAvkortingPåSak: Avkortingsvarsel.Utenlandsopphold.SkalAvkortes,
        beregningUtenAvkorting: Beregning,
        clock: Clock,
        utenAvkortingsfradrag: GrunnlagsdataOgVilkårsvurderinger.Søknadsbehandling,
        beregningStrategyFactory: BeregningStrategyFactory,
        begrunnelse: String?,
    ): Either<KunneIkkeBeregne, Triple<Beregning, AvkortingVedSøknadsbehandling.KlarTilIverksetting, GrunnlagsdataOgVilkårsvurderinger.Søknadsbehandling>> {
        return Avkortingsplan(
            feilutbetaltBeløp = uteståendeAvkortingPåSak.hentUtbetalteBeløp().sum(),
            beregningUtenAvkorting = beregningUtenAvkorting,
            clock = clock,
        ).lagFradrag().getOrElse {
            return when (it) {
                Avkortingsplan.KunneIkkeLageAvkortingsplan.AvkortingErUfullstendig -> {
                    KunneIkkeBeregne.AvkortingErUfullstendig.left()
                }
            }
        }.let {
            val nyGrunnlagsdataOgVilkårsvurderinger =
                utenAvkortingsfradrag.oppdaterFradragsgrunnlag(
                    utenAvkortingsfradrag.grunnlagsdata.fradragsgrunnlag + it,
                )
            Triple(
                beregningStrategyFactory.beregn(
                    grunnlagsdataOgVilkårsvurderinger = nyGrunnlagsdataOgVilkårsvurderinger,
                    begrunnelse = begrunnelse,
                    sakstype = this.sakstype,
                ),
                AvkortingVedSøknadsbehandling.SkalAvkortes(uteståendeAvkortingPåSak),
                nyGrunnlagsdataOgVilkårsvurderinger,
            ).right()
        }
    }

    private fun tilAvslåttBeregning(
        saksbehandler: NavIdentBruker.Saksbehandler,
        beregning: Beregning,
        grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger.Søknadsbehandling,
        søknadsbehandlingshistorikk: Søknadsbehandlingshistorikk,
    ): BeregnetSøknadsbehandling.Avslag {
        return BeregnetSøknadsbehandling.Avslag(
            id = this.id,
            opprettet = this.opprettet,
            sakId = this.sakId,
            saksnummer = this.saksnummer,
            søknad = this.søknad,
            oppgaveId = this.oppgaveId,
            fnr = this.fnr,
            beregning = beregning,
            fritekstTilBrev = this.fritekstTilBrev,
            aldersvurdering = this.aldersvurdering,
            grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
            attesteringer = this.attesteringer,
            søknadsbehandlingsHistorikk = søknadsbehandlingshistorikk,
            sakstype = this.sakstype,
            saksbehandler = saksbehandler,
        )
    }

    private fun tilInnvilgetBeregning(
        saksbehandler: NavIdentBruker.Saksbehandler,
        beregning: Beregning,
        grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger.Søknadsbehandling,
        søknadsbehandlingshistorikk: Søknadsbehandlingshistorikk,
        avkorting: AvkortingVedSøknadsbehandling.KlarTilIverksetting,
    ): BeregnetSøknadsbehandling.Innvilget {
        return BeregnetSøknadsbehandling.Innvilget(
            id = this.id,
            opprettet = this.opprettet,
            sakId = this.sakId,
            saksnummer = this.saksnummer,
            søknad = this.søknad,
            oppgaveId = this.oppgaveId,
            fnr = this.fnr,
            beregning = beregning,
            fritekstTilBrev = this.fritekstTilBrev,
            aldersvurdering = this.aldersvurdering,
            grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
            attesteringer = this.attesteringer,
            søknadsbehandlingsHistorikk = søknadsbehandlingshistorikk,
            avkorting = avkorting,
            sakstype = this.sakstype,
            saksbehandler = saksbehandler,
        )
    }
}
