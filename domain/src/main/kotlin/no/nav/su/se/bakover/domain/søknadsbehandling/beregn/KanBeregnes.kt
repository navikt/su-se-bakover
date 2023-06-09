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
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.satser.SatsFactory
import java.time.Clock

/**
 * Markerer at en søknadsbehandling kan beregnes.
 */
interface KanBeregnes :
    Søknadsbehandling.KanOppdaterePeriodeGrunnlagVilkår {

    fun beregn(
        nySaksbehandler: NavIdentBruker.Saksbehandler,
        begrunnelse: String?,
        clock: Clock,
        satsFactory: SatsFactory,
        uteståendeAvkortingPåSak: Avkortingsvarsel.Utenlandsopphold.SkalAvkortes?,
    ): Either<KunneIkkeBeregne, BeregnetSøknadsbehandling> {
        val vilkårsvurdert = (this as Søknadsbehandling).vilkårsvurder(nySaksbehandler)
        return vilkårsvurdert.beregnInternal(
            begrunnelse = begrunnelse,
            clock = clock,
            beregningStrategyFactory = BeregningStrategyFactory(
                clock = clock,
                satsFactory = satsFactory,
            ),
            nySaksbehandler = nySaksbehandler,
            uteståendeAvkortingPåSak = uteståendeAvkortingPåSak,
        )
    }

    private fun VilkårsvurdertSøknadsbehandling.beregnInternal(
        nySaksbehandler: NavIdentBruker.Saksbehandler,
        begrunnelse: String?,
        clock: Clock,
        beregningStrategyFactory: BeregningStrategyFactory,
        uteståendeAvkortingPåSak: Avkortingsvarsel.Utenlandsopphold.SkalAvkortes?,
    ): Either<KunneIkkeBeregne, BeregnetSøknadsbehandling> {
        return when (uteståendeAvkortingPåSak) {
            null -> {
                beregnUtenAvkorting(
                    begrunnelse = begrunnelse,
                    beregningStrategyFactory = beregningStrategyFactory,
                ).getOrElse { return it.left() }
            }

            else -> {
                beregnMedAvkorting(
                    avkortingsvarsel = uteståendeAvkortingPåSak,
                    begrunnelse = begrunnelse,
                    clock = clock,
                    beregningStrategyFactory = beregningStrategyFactory,
                ).getOrElse { return it.left() }
            }
        }.let { (behandling, beregning, avkorting) ->
            val nySøknadsbehandlingshistorikk = behandling.søknadsbehandlingsHistorikk.leggTilNyHendelse(
                saksbehandlingsHendelse = Søknadsbehandlingshendelse(
                    tidspunkt = Tidspunkt.now(clock),
                    saksbehandler = nySaksbehandler,
                    handling = SøknadsbehandlingsHandling.Beregnet,
                ),
            )
            when (VurderAvslagGrunnetBeregning.vurderAvslagGrunnetBeregning(beregning)) {
                is AvslagGrunnetBeregning.Ja -> BeregnetSøknadsbehandling.Avslag(
                    id = behandling.id,
                    opprettet = behandling.opprettet,
                    sakId = behandling.sakId,
                    saksnummer = behandling.saksnummer,
                    søknad = behandling.søknad,
                    oppgaveId = behandling.oppgaveId,
                    fnr = behandling.fnr,
                    beregning = beregning,
                    fritekstTilBrev = behandling.fritekstTilBrev,
                    aldersvurdering = behandling.aldersvurdering!!,
                    grunnlagsdata = behandling.grunnlagsdata,
                    vilkårsvurderinger = behandling.vilkårsvurderinger,
                    eksterneGrunnlag = behandling.eksterneGrunnlag,
                    attesteringer = behandling.attesteringer,
                    søknadsbehandlingsHistorikk = nySøknadsbehandlingshistorikk,
                    sakstype = behandling.sakstype,
                    saksbehandler = nySaksbehandler,
                )

                AvslagGrunnetBeregning.Nei -> {
                    BeregnetSøknadsbehandling.Innvilget(
                        id = behandling.id,
                        opprettet = behandling.opprettet,
                        sakId = behandling.sakId,
                        saksnummer = behandling.saksnummer,
                        søknad = behandling.søknad,
                        oppgaveId = behandling.oppgaveId,
                        fnr = behandling.fnr,
                        beregning = beregning,
                        fritekstTilBrev = behandling.fritekstTilBrev,
                        aldersvurdering = behandling.aldersvurdering!!,
                        grunnlagsdata = behandling.grunnlagsdata,
                        vilkårsvurderinger = behandling.vilkårsvurderinger,
                        eksterneGrunnlag = behandling.eksterneGrunnlag,
                        attesteringer = behandling.attesteringer,
                        søknadsbehandlingsHistorikk = nySøknadsbehandlingshistorikk,
                        avkorting = avkorting,
                        sakstype = behandling.sakstype,
                        saksbehandler = nySaksbehandler,
                    )
                }
            }.right()
        }
    }

    /**
     * Beregner uten å ta hensyn til avkorting. Fjerner eventuelle [Fradragstype.AvkortingUtenlandsopphold] som måtte
     * ligge i grunnlaget
     */
    private fun VilkårsvurdertSøknadsbehandling.beregnUtenAvkorting(
        begrunnelse: String?,
        beregningStrategyFactory: BeregningStrategyFactory,
    ): Either<KunneIkkeBeregne, Triple<VilkårsvurdertSøknadsbehandling, Beregning, AvkortingVedSøknadsbehandling.IngenAvkorting>> {
        return leggTilFradragsgrunnlagForBeregning(
            fradragsgrunnlag = grunnlagsdata.fradragsgrunnlag.filterNot { it.fradragstype == Fradragstype.AvkortingUtenlandsopphold },
        ).getOrElse {
            return KunneIkkeBeregne.UgyldigTilstandForEndringAvFradrag(it).left()
        }.let {
            Triple(
                it,
                gjørBeregning(
                    søknadsbehandling = it,
                    begrunnelse = begrunnelse,
                    beregningStrategyFactory = beregningStrategyFactory,
                ),
                AvkortingVedSøknadsbehandling.IngenAvkorting,
            )
        }.right()
    }

    /**
     * Restbeløpet etter andre fradrag er faktorert inn av [beregnUtenAvkorting] er maksimalt beløp som kan avkortes.
     */
    private fun VilkårsvurdertSøknadsbehandling.beregnMedAvkorting(
        avkortingsvarsel: Avkortingsvarsel.Utenlandsopphold.SkalAvkortes,
        begrunnelse: String?,
        clock: Clock,
        beregningStrategyFactory: BeregningStrategyFactory,
    ): Either<KunneIkkeBeregne, Triple<VilkårsvurdertSøknadsbehandling, Beregning, AvkortingVedSøknadsbehandling.SkalAvkortes>> {
        return beregnUtenAvkorting(
            begrunnelse,
            beregningStrategyFactory,
        ).map { (utenAvkorting, beregningUtenAvkorting) ->
            val fradragForAvkorting = Avkortingsplan(
                feilutbetaltBeløp = avkortingsvarsel.hentUtbetalteBeløp().sum(),
                beregning = beregningUtenAvkorting,
                clock = clock,
            ).lagFradrag().getOrElse {
                return when (it) {
                    Avkortingsplan.KunneIkkeLageAvkortingsplan.AvkortingErUfullstendig -> {
                        KunneIkkeBeregne.AvkortingErUfullstendig.left()
                    }
                }
            }

            val medAvkorting = utenAvkorting.leggTilFradragsgrunnlagForBeregning(
                fradragsgrunnlag = utenAvkorting.grunnlagsdata.fradragsgrunnlag + fradragForAvkorting,
            ).getOrElse { return KunneIkkeBeregne.UgyldigTilstandForEndringAvFradrag(it).left() }

            Triple(
                medAvkorting,
                gjørBeregning(
                    søknadsbehandling = medAvkorting,
                    begrunnelse = begrunnelse,
                    beregningStrategyFactory = beregningStrategyFactory,
                ),
                AvkortingVedSøknadsbehandling.SkalAvkortes(avkortingsvarsel),
            )
        }
    }

    private fun gjørBeregning(
        søknadsbehandling: Søknadsbehandling,
        begrunnelse: String?,
        beregningStrategyFactory: BeregningStrategyFactory,
    ): Beregning {
        return beregningStrategyFactory.beregn(søknadsbehandling, begrunnelse)
    }

    /**
     * *protected* skal kun kalles fra typer som arver [Søknadsbehandling]
     */
    private fun VilkårsvurdertSøknadsbehandling.leggTilFradragsgrunnlagForBeregning(
        fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>,
    ): Either<KunneIkkeLeggeTilGrunnlag.KunneIkkeLeggeTilFradragsgrunnlag, VilkårsvurdertSøknadsbehandling.Innvilget> {
        return vilkårsvurder(saksbehandler).let {
            when (it) {
                is KanBeregnes -> leggTilFradragsgrunnlagInternalForBeregning(fradragsgrunnlag)
                else -> KunneIkkeLeggeTilGrunnlag.KunneIkkeLeggeTilFradragsgrunnlag.IkkeLovÅLeggeTilFradragIDenneStatusen(
                    this::class,
                ).left()
            }
        }
    }

    private fun VilkårsvurdertSøknadsbehandling.leggTilFradragsgrunnlagInternalForBeregning(
        fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>,
    ): Either<KunneIkkeLeggeTilGrunnlag.KunneIkkeLeggeTilFradragsgrunnlag, VilkårsvurdertSøknadsbehandling.Innvilget> {
        return validerFradragsgrunnlag(fradragsgrunnlag).map {
            copyInternal(
                grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.leggTilFradragsgrunnlag(
                    fradragsgrunnlag,
                ),
                søknadsbehandlingshistorikk = this.søknadsbehandlingsHistorikk,
            ).vilkårsvurder(saksbehandler) as VilkårsvurdertSøknadsbehandling.Innvilget // TODO cast
        }
    }
}
