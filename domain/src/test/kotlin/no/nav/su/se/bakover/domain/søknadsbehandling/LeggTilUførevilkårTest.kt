package no.nav.su.se.bakover.domain.søknadsbehandling

import arrow.core.left
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beInstanceOf
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.januar
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.søknadsbehandlingBeregnetAvslag
import no.nav.su.se.bakover.test.søknadsbehandlingBeregnetInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingIverksattAvslagMedBeregning
import no.nav.su.se.bakover.test.søknadsbehandlingIverksattAvslagUtenBeregning
import no.nav.su.se.bakover.test.søknadsbehandlingIverksattInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingSimulert
import no.nav.su.se.bakover.test.søknadsbehandlingTilAttesteringAvslagMedBeregning
import no.nav.su.se.bakover.test.søknadsbehandlingTilAttesteringAvslagUtenBeregning
import no.nav.su.se.bakover.test.søknadsbehandlingTilAttesteringInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingUnderkjentAvslagMedBeregning
import no.nav.su.se.bakover.test.søknadsbehandlingUnderkjentAvslagUtenBeregning
import no.nav.su.se.bakover.test.søknadsbehandlingUnderkjentInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertAvslag
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertUavklart
import no.nav.su.se.bakover.test.vilkårsvurderinger.innvilgetUførevilkår
import org.junit.jupiter.api.Test

internal class LeggTilUførevilkårTest {
    @Test
    fun `får ikke legge til uførevilkår utenfor perioden`() {
        val uavklart = søknadsbehandlingVilkårsvurdertUavklart().second

        uavklart.leggTilUførevilkår(
            uførhet = innvilgetUførevilkår(
                periode = januar(2020),
            ),
            clock = fixedClock,
        ) shouldBe Søknadsbehandling.KunneIkkeLeggeTilUførevilkår.VurderingsperiodeUtenforBehandlingsperiode.left()

        uavklart.leggTilUførevilkår(
            uførhet = innvilgetUførevilkår(
                periode = Periode.create(1.januar(2020), 31.januar(2025)),
            ),
            clock = fixedClock,
        ) shouldBe Søknadsbehandling.KunneIkkeLeggeTilUførevilkår.VurderingsperiodeUtenforBehandlingsperiode.left()

        uavklart.leggTilUførevilkår(
            uførhet = innvilgetUførevilkår(
                periode = uavklart.periode,
            ),
            clock = fixedClock,
        ).isRight() shouldBe true
    }

    @Test
    fun `får bare lagt til uførevilkår for enkelte typer`() {
        listOf(
            søknadsbehandlingVilkårsvurdertUavklart(),
            søknadsbehandlingVilkårsvurdertAvslag(),
            søknadsbehandlingVilkårsvurdertInnvilget(),
            søknadsbehandlingBeregnetAvslag(),
            søknadsbehandlingBeregnetInnvilget(),
            søknadsbehandlingSimulert(),
            søknadsbehandlingUnderkjentInnvilget(),
            søknadsbehandlingUnderkjentAvslagUtenBeregning(),
            søknadsbehandlingUnderkjentAvslagMedBeregning(),
        ).map {
            it.second
        }.forEach {
            it.leggTilUførevilkår(
                uførhet = innvilgetUførevilkår(),
                clock = fixedClock,
            ).let { oppdatert ->
                oppdatert.isRight() shouldBe true
                oppdatert.getOrFail() shouldBe beInstanceOf<Søknadsbehandling.Vilkårsvurdert>()
            }
        }

        listOf(
            søknadsbehandlingTilAttesteringInnvilget(),
            søknadsbehandlingTilAttesteringAvslagMedBeregning(),
            søknadsbehandlingTilAttesteringAvslagUtenBeregning(),
            søknadsbehandlingIverksattInnvilget(),
            søknadsbehandlingIverksattAvslagMedBeregning(),
            søknadsbehandlingIverksattAvslagUtenBeregning(),
        ).map {
            it.second
        }.forEach {
            it.leggTilUførevilkår(
                uførhet = innvilgetUførevilkår(),
                clock = fixedClock,
            ) shouldBe Søknadsbehandling.KunneIkkeLeggeTilUførevilkår.UgyldigTilstand(
                fra = it::class,
                til = Søknadsbehandling.Vilkårsvurdert::class,
            ).left()
        }
    }
}
