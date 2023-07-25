package no.nav.su.se.bakover.domain.søknadsbehandling

import no.nav.su.se.bakover.test.attesteringUnderkjent
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.getOrFailAsType
import no.nav.su.se.bakover.test.iverksattSøknadsbehandling
import no.nav.su.se.bakover.test.nySøknadsbehandlingMedStønadsperiode
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import no.nav.su.se.bakover.test.simulering.simulerUtbetaling
import no.nav.su.se.bakover.test.stønadsperiode2021
import no.nav.su.se.bakover.test.søknad.søknadId
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertAvslag
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertInnvilget
import no.nav.su.se.bakover.test.trekkSøknad
import no.nav.su.se.bakover.test.vilkårsvurderinger.avslåttUførevilkårUtenGrunnlag
import no.nav.su.se.bakover.test.vilkårsvurderinger.innvilgetUførevilkår
import no.nav.su.se.bakover.test.vilkårsvurderinger.innvilgetUførevilkårForventetInntekt12000

private val sakOgUavklart
    get() = nySøknadsbehandlingMedStønadsperiode(
        stønadsperiode = stønadsperiode2021,
    )

internal val opprettet get() = sakOgUavklart.second

internal const val fritekstTilBrev: String = "Fritekst til brev"

internal val vilkårsvurdertInnvilget: VilkårsvurdertSøknadsbehandling.Innvilget
    get() =
        søknadsbehandlingVilkårsvurdertInnvilget(
            customVilkår = listOf(innvilgetUførevilkårForventetInntekt12000()),
        ).second

internal val vilkårsvurdertAvslag: VilkårsvurdertSøknadsbehandling.Avslag
    get() =
        søknadsbehandlingVilkårsvurdertAvslag().second

internal val beregnetInnvilget: BeregnetSøknadsbehandling.Innvilget
    get() =
        vilkårsvurdertInnvilget.beregn(
            begrunnelse = null,
            clock = fixedClock,
            satsFactory = satsFactoryTestPåDato(),
            nySaksbehandler = saksbehandler,
            uteståendeAvkortingPåSak = null,
        ).getOrFail() as BeregnetSøknadsbehandling.Innvilget

internal val beregnetAvslag: BeregnetSøknadsbehandling.Avslag
    get() =
        vilkårsvurdertInnvilget.leggTilUførevilkår(
            vilkår = innvilgetUførevilkår(forventetInntekt = 11000000),
            saksbehandler = saksbehandler,
        ).getOrFailAsType<VilkårsvurdertSøknadsbehandling.Innvilget>().beregn(
            begrunnelse = null,
            clock = fixedClock,
            satsFactory = satsFactoryTestPåDato(),
            nySaksbehandler = saksbehandler,
            uteståendeAvkortingPåSak = null,
        ).getOrFail() as BeregnetSøknadsbehandling.Avslag

internal val simulert: SimulertSøknadsbehandling
    get() =
        beregnetInnvilget.simuler(
            saksbehandler = saksbehandler,
            clock = fixedClock,
        ) { _, _ ->
            simulerUtbetaling(
                sak = sakOgUavklart.first,
                søknadsbehandling = beregnetInnvilget,
            ).map {
                it.simulering
            }
        }.getOrFail()

internal val tilAttesteringInnvilget: SøknadsbehandlingTilAttestering.Innvilget
    get() =
        simulert.tilAttestering(saksbehandler, fritekstTilBrev, fixedClock).getOrFail()

internal val tilAttesteringAvslagVilkår: SøknadsbehandlingTilAttestering.Avslag.UtenBeregning
    get() =
        vilkårsvurdertAvslag.tilAttestering(saksbehandler, fritekstTilBrev, fixedClock).getOrFail()

internal val tilAttesteringAvslagBeregning: SøknadsbehandlingTilAttestering.Avslag.MedBeregning
    get() =
        beregnetAvslag.tilAttestering(saksbehandler, fritekstTilBrev, fixedClock).getOrFail()

internal val underkjentInnvilget: UnderkjentSøknadsbehandling.Innvilget
    get() =
        tilAttesteringInnvilget.tilUnderkjent(attesteringUnderkjent(clock = fixedClock)).getOrFail()
internal val underkjentAvslagVilkår: UnderkjentSøknadsbehandling.Avslag.UtenBeregning
    get() =
        tilAttesteringAvslagVilkår.tilUnderkjent(attesteringUnderkjent(clock = fixedClock)).getOrFail()
internal val underkjentAvslagBeregning: UnderkjentSøknadsbehandling.Avslag.MedBeregning
    get() =
        tilAttesteringAvslagBeregning.tilUnderkjent(attesteringUnderkjent(clock = fixedClock)).getOrFail()
internal val iverksattInnvilget get() = iverksattSøknadsbehandling().second as IverksattSøknadsbehandling.Innvilget

internal val iverksattAvslagVilkår
    get() = iverksattSøknadsbehandling(
        customVilkår = listOf(avslåttUførevilkårUtenGrunnlag()),
    ).second as IverksattSøknadsbehandling.Avslag.UtenBeregning

internal val iverksattAvslagBeregning
    get() = iverksattSøknadsbehandling(
        customGrunnlag = listOf(fradragsgrunnlagArbeidsinntekt(arbeidsinntekt = 30000.0)),
    ).second as IverksattSøknadsbehandling.Avslag.MedBeregning

internal val lukketSøknadsbehandling
    get() =
        underkjentInnvilget.lukkSøknadsbehandlingOgSøknad(
            trekkSøknad(søknadId),
        ).getOrFail()
