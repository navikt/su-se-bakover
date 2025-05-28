package no.nav.su.se.bakover.domain.søknadsbehandling.vilkår

import arrow.core.left
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beInstanceOf
import no.nav.su.se.bakover.common.domain.extensions.mapSecond
import no.nav.su.se.bakover.common.domain.tid.januar
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.domain.søknadsbehandling.BeregnetSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.VilkårsvurdertSøknadsbehandling
import no.nav.su.se.bakover.test.beregnetSøknadsbehandlingInnvilget
import no.nav.su.se.bakover.test.beregnetSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.nySøknadsbehandlingMedStønadsperiode
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.shouldBeType
import no.nav.su.se.bakover.test.simulertSøknadsbehandling
import no.nav.su.se.bakover.test.søknadsbehandlingBeregnetAvslag
import no.nav.su.se.bakover.test.søknadsbehandlingUnderkjentAvslagMedBeregning
import no.nav.su.se.bakover.test.søknadsbehandlingUnderkjentAvslagUtenBeregning
import no.nav.su.se.bakover.test.søknadsbehandlingUnderkjentInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertAvslag
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertInnvilget
import no.nav.su.se.bakover.test.vilkår.utenlandsoppholdAvslag
import no.nav.su.se.bakover.test.vilkår.utenlandsoppholdInnvilget
import org.junit.jupiter.api.Test
import vilkår.inntekt.domain.grunnlag.FradragTilhører

class LeggTilUtenlandsoppholdTest {

    @Test
    fun `beregnet avslag til vilkårsvurdert innvilget`() {
        beregnetSøknadsbehandlingUføre(
            customGrunnlag = listOf(
                fradragsgrunnlagArbeidsinntekt(
                    arbeidsinntekt = 50000.0,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
        ).also { (_, beregnet) ->
            beregnet.shouldBeType<BeregnetSøknadsbehandling.Avslag>().also {
                beregnet.leggTilUtenlandsopphold(
                    saksbehandler = saksbehandler,
                    utenlandsoppholdInnvilget(),
                )
                    .getOrFail()
                    .also { innvilget -> innvilget.shouldBeType<VilkårsvurdertSøknadsbehandling.Innvilget>() }
            }
        }
    }

    @Test
    fun `beregnet innvilget til vilkårsvurdert avslag`() {
        beregnetSøknadsbehandlingUføre().also { (_, beregnet) ->
            beregnet.shouldBeType<BeregnetSøknadsbehandling.Innvilget>().also {
                beregnet.leggTilUtenlandsopphold(
                    saksbehandler = saksbehandler,
                    utenlandsoppholdAvslag(),
                )
                    .getOrFail().also { avslag ->
                        avslag.shouldBeType<VilkårsvurdertSøknadsbehandling.Avslag>()
                    }
            }
        }
    }

    @Test
    fun `beregnet innvilget til vilkårsvurdert innvilget`() {
        beregnetSøknadsbehandlingUføre().also { (_, beregnet) ->
            beregnet.shouldBeType<BeregnetSøknadsbehandling.Innvilget>().also {
                beregnet.leggTilUtenlandsopphold(
                    saksbehandler = saksbehandler,
                    utenlandsoppholdInnvilget(),
                )
                    .getOrFail()
                    .also { innvilget ->
                        innvilget.shouldBeType<VilkårsvurdertSøknadsbehandling.Innvilget>()
                    }
            }
        }
    }

    @Test
    fun `får ikke legge til opphold i utlandet utenfor perioden`() {
        val uavklart = nySøknadsbehandlingMedStønadsperiode().second

        uavklart.leggTilUtenlandsopphold(
            vilkår = utenlandsoppholdInnvilget(
                periode = januar(2020),
            ),
            saksbehandler = saksbehandler,
        ) shouldBe KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilUtenlandsopphold.Vilkårsfeil(
            VilkårsfeilVedSøknadsbehandling.VurderingsperiodeUtenforBehandlingsperiode,
        ).left()

        uavklart.leggTilUtenlandsopphold(
            vilkår = utenlandsoppholdInnvilget(
                periode = Periode.create(1.januar(2020), 31.januar(2025)),
            ),
            saksbehandler = saksbehandler,
        ) shouldBe KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilUtenlandsopphold.Vilkårsfeil(
            VilkårsfeilVedSøknadsbehandling.VurderingsperiodeUtenforBehandlingsperiode,
        ).left()

        uavklart.leggTilUtenlandsopphold(
            vilkår = utenlandsoppholdInnvilget(
                periode = uavklart.periode,
            ),
            saksbehandler = saksbehandler,
        ).shouldBeRight()
    }

    @Test
    fun `får bare lagt til opphold i utlandet for enkelte typer`() {
        listOf(
            nySøknadsbehandlingMedStønadsperiode().let {
                it.first to it.second
            },
            søknadsbehandlingVilkårsvurdertAvslag(),
            søknadsbehandlingVilkårsvurdertInnvilget(),
            søknadsbehandlingBeregnetAvslag(),
            beregnetSøknadsbehandlingInnvilget().mapSecond { it as BeregnetSøknadsbehandling.Innvilget },
            simulertSøknadsbehandling(),
            søknadsbehandlingUnderkjentInnvilget(),
            søknadsbehandlingUnderkjentAvslagUtenBeregning(),
            søknadsbehandlingUnderkjentAvslagMedBeregning(),
        ).map {
            it.second
        }.forEach {
            it.leggTilUtenlandsopphold(
                vilkår = utenlandsoppholdInnvilget(),
                saksbehandler = saksbehandler,
            ).let { oppdatert ->
                oppdatert.shouldBeRight()
                oppdatert.getOrFail() shouldBe beInstanceOf<VilkårsvurdertSøknadsbehandling>()
            }
        }
    }
}
