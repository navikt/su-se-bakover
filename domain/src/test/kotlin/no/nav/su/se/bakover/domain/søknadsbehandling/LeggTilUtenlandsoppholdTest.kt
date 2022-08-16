package no.nav.su.se.bakover.domain.søknadsbehandling

import arrow.core.left
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beInstanceOf
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.januar
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
import no.nav.su.se.bakover.test.vilkår.utenlandsoppholdInnvilget
import org.junit.jupiter.api.Test

class LeggTilUtenlandsoppholdTest {

    @Test
    fun `får ikke legge til opphold i utlandet utenfor perioden`() {
        val uavklart = søknadsbehandlingVilkårsvurdertUavklart().second

        uavklart.leggTilUtenlandsopphold(
            utenlandsopphold = utenlandsoppholdInnvilget(
                periode = januar(2020),
            ),
        ) shouldBe KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilUtenlandsopphold.VurderingsperiodeUtenforBehandlingsperiode.left()

        uavklart.leggTilUtenlandsopphold(
            utenlandsopphold = utenlandsoppholdInnvilget(
                periode = Periode.create(1.januar(2020), 31.januar(2025)),
            ),
        ) shouldBe KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilUtenlandsopphold.VurderingsperiodeUtenforBehandlingsperiode.left()

        uavklart.leggTilUtenlandsopphold(
            utenlandsopphold = utenlandsoppholdInnvilget(
                periode = uavklart.periode,
            ),
        ).isRight() shouldBe true
    }

    @Test
    fun `får bare lagt til opphold i utlandet for enkelte typer`() {
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
            it.leggTilUtenlandsopphold(
                utenlandsopphold = utenlandsoppholdInnvilget(),
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
            it.leggTilUtenlandsopphold(
                utenlandsopphold = utenlandsoppholdInnvilget(),
            ) shouldBe KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilUtenlandsopphold.IkkeLovÅLeggeTilUtenlandsoppholdIDenneStatusen(
                fra = it::class,
                til = Søknadsbehandling.Vilkårsvurdert::class,
            ).left()
        }
    }
}
