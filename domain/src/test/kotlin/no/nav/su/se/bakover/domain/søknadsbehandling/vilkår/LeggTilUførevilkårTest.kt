package no.nav.su.se.bakover.domain.søknadsbehandling.vilkår

import arrow.core.left
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beInstanceOf
import no.nav.su.se.bakover.common.extensions.januar
import no.nav.su.se.bakover.common.extensions.mapSecond
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.domain.søknadsbehandling.BeregnetSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.UnderkjentSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.VilkårsvurdertSøknadsbehandling
import no.nav.su.se.bakover.domain.vilkår.OpplysningspliktVilkår
import no.nav.su.se.bakover.test.beregnetSøknadsbehandling
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.nySøknadsbehandlingMedStønadsperiode
import no.nav.su.se.bakover.test.nySøknadsbehandlingUføre
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.shouldBeType
import no.nav.su.se.bakover.test.simulertSøknadsbehandling
import no.nav.su.se.bakover.test.søknadsbehandlingBeregnetAvslag
import no.nav.su.se.bakover.test.søknadsbehandlingUnderkjentAvslagMedBeregning
import no.nav.su.se.bakover.test.søknadsbehandlingUnderkjentAvslagUtenBeregning
import no.nav.su.se.bakover.test.søknadsbehandlingUnderkjentInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertAvslag
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertInnvilget
import no.nav.su.se.bakover.test.underkjentSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.vilkårsvurderinger.avslåttUførevilkårUtenGrunnlag
import no.nav.su.se.bakover.test.vilkårsvurderinger.innvilgetUførevilkår
import no.nav.su.se.bakover.test.vilkårsvurdertSøknadsbehandlingUføre
import org.junit.jupiter.api.Test
import vilkår.uføre.domain.UføreVilkår

internal class LeggTilUførevilkårTest {

    @Test
    fun `underkjent avslag vilkår til vilkårsvurdert innvilget`() {
        underkjentSøknadsbehandlingUføre(
            customVilkår = listOf(avslåttUførevilkårUtenGrunnlag()),
        ).also { (_, underkjent) ->
            underkjent.shouldBeType<UnderkjentSøknadsbehandling.Avslag.UtenBeregning>().also {
                underkjent.leggTilUførevilkår(
                    saksbehandler = saksbehandler,
                    vilkår = innvilgetUførevilkår(),
                ).getOrFail()
                    .also { innvilget ->
                        innvilget.shouldBeType<VilkårsvurdertSøknadsbehandling.Innvilget>()
                    }
            }
        }
    }

    @Test
    fun `underkjent avslag vilkår til vilkårsvurdert avslag`() {
        underkjentSøknadsbehandlingUføre(
            customVilkår = listOf(avslåttUførevilkårUtenGrunnlag()),
        ).also { (_, underkjent) ->
            underkjent.shouldBeType<UnderkjentSøknadsbehandling.Avslag.UtenBeregning>().also {
                underkjent.leggTilUførevilkår(
                    saksbehandler = saksbehandler,
                    vilkår = avslåttUførevilkårUtenGrunnlag(),
                )
                    .getOrFail()
                    .also { avslag ->
                        avslag.shouldBeType<VilkårsvurdertSøknadsbehandling.Avslag>()
                    }
            }
        }
    }

    @Test
    fun `vilkårsvurdert avslag til vilkårsvurdert innvilget`() {
        vilkårsvurdertSøknadsbehandlingUføre(customVilkår = listOf(avslåttUførevilkårUtenGrunnlag())).also { (_, avslag) ->
            avslag.shouldBeType<VilkårsvurdertSøknadsbehandling.Avslag>().also {
                it.leggTilUførevilkår(
                    saksbehandler = saksbehandler,
                    innvilgetUførevilkår(),
                ).getOrFail()
                    .also { innvilget ->
                        innvilget.shouldBeType<VilkårsvurdertSøknadsbehandling.Innvilget>()
                    }
            }
        }
    }

    @Test
    fun `håndtering av opplysningsplikt`() {
        nySøknadsbehandlingUføre().also { (_, uavklart) ->
            uavklart.leggTilUførevilkår(
                saksbehandler = saksbehandler,
                vilkår = innvilgetUførevilkår(),
            ).getOrFail()
                .also { vilkårsvurdert ->
                    vilkårsvurdert.shouldBeType<VilkårsvurdertSøknadsbehandling.Uavklart>()
                    vilkårsvurdert.vilkårsvurderinger.uføreVilkår().getOrFail().shouldBeType<UføreVilkår.Vurdert>()
                    // skal legges til implisitt hvis det ikke er vurdert fra før
                    vilkårsvurdert.vilkårsvurderinger.opplysningspliktVilkår()
                        .shouldBeType<OpplysningspliktVilkår.Vurdert>()
                }
        }
    }

    @Test
    fun `får ikke legge til uførevilkår utenfor perioden`() {
        val uavklart = nySøknadsbehandlingMedStønadsperiode().second

        uavklart.leggTilUførevilkår(
            vilkår = innvilgetUførevilkår(
                periode = januar(2020),
            ),
            saksbehandler = saksbehandler,
        ) shouldBe KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilUførevilkår.Vilkårsfeil(
            VilkårsfeilVedSøknadsbehandling.VurderingsperiodeUtenforBehandlingsperiode,
        ).left()

        uavklart.leggTilUførevilkår(
            vilkår = innvilgetUførevilkår(
                periode = Periode.create(1.januar(2020), 31.januar(2025)),
            ),
            saksbehandler = saksbehandler,
        ) shouldBe KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilUførevilkår.Vilkårsfeil(
            VilkårsfeilVedSøknadsbehandling.VurderingsperiodeUtenforBehandlingsperiode,
        ).left()

        uavklart.leggTilUførevilkår(
            vilkår = innvilgetUførevilkår(
                periode = uavklart.periode,
            ),
            saksbehandler = saksbehandler,
        ).shouldBeRight()
    }

    @Test
    fun `får bare lagt til uførevilkår for enkelte typer`() {
        listOf(
            nySøknadsbehandlingMedStønadsperiode().let {
                it.first to it.second
            },
            søknadsbehandlingVilkårsvurdertAvslag(),
            søknadsbehandlingVilkårsvurdertInnvilget(),
            søknadsbehandlingBeregnetAvslag(),
            beregnetSøknadsbehandling().mapSecond { it as BeregnetSøknadsbehandling.Innvilget },
            simulertSøknadsbehandling(),
            søknadsbehandlingUnderkjentInnvilget(),
            søknadsbehandlingUnderkjentAvslagUtenBeregning(),
            søknadsbehandlingUnderkjentAvslagMedBeregning(),
        ).map {
            it.second
        }.forEach {
            it.leggTilUførevilkår(
                vilkår = innvilgetUførevilkår(),
                saksbehandler = saksbehandler,
            ).let { oppdatert ->
                oppdatert.shouldBeRight()
                oppdatert.getOrFail() shouldBe beInstanceOf<VilkårsvurdertSøknadsbehandling>()
            }
        }
    }
}
