package no.nav.su.se.bakover.domain.søknadsbehandling

import arrow.core.left
import arrow.core.nonEmptyListOf
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.test.fixedClock
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
import no.nav.su.se.bakover.test.vilkår.familiegjenforeningVilkårInnvilget
import no.nav.su.se.bakover.test.vilkårsvurderingSøknadsbehandlingIkkeVurdertAlder
import no.nav.su.se.bakover.test.vilkårsvurderingSøknadsbehandlingVurdertAvslagAlder
import no.nav.su.se.bakover.test.vilkårsvurderingSøknadsbehandlingVurdertInnvilgetAlder
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import vurderingsperiode.vurderingsperiodeFamiliegjenforeningAvslag
import vurderingsperiode.vurderingsperiodeFamiliegjenforeningInnvilget

internal class LeggTilFamiliegjenforeningTest {

    @Test
    fun `kan legge til familiegjenforening ved uavklart`() {
        val uavklart =
            søknadsbehandlingVilkårsvurdertUavklart(vilkårsvurderinger = vilkårsvurderingSøknadsbehandlingIkkeVurdertAlder())

        uavklart.second.leggTilFamiliegjenforegningvilkår(
            familiegjenforegning = familiegjenforeningVilkårInnvilget(),
            clock = fixedClock,
        ).shouldBeRight()
    }

    @Test
    fun `kan legge til familiegjenforening ved vilkårsvurdert innvilget`() {
        val innvilget =
            søknadsbehandlingVilkårsvurdertInnvilget(vilkårsvurderinger = vilkårsvurderingSøknadsbehandlingVurdertInnvilgetAlder())

        innvilget.second.leggTilFamiliegjenforegningvilkår(
            familiegjenforegning = familiegjenforeningVilkårInnvilget(
                nonEmptyListOf(vurderingsperiodeFamiliegjenforeningInnvilget(periode = år(2039))),
            ),
            clock = fixedClock,
        )
    }

    @Test
    fun `kan legge til familiegjenforening ved vilkårsvurdert avslag`() {
        val avslag =
            søknadsbehandlingVilkårsvurdertAvslag(vilkårsvurderinger = vilkårsvurderingSøknadsbehandlingVurdertAvslagAlder())

        avslag.second.leggTilFamiliegjenforegningvilkår(
            familiegjenforegning = familiegjenforeningVilkårInnvilget(
                nonEmptyListOf(vurderingsperiodeFamiliegjenforeningInnvilget(periode = år(2039))),
            ),
            clock = fixedClock,
        ).shouldBeRight()
    }

    @Test
    @Disabled("Beregning er ikke implementert for alder enda")
    fun `kan legge til familiegjenforening ved beregnet innvilget`() {
        val innvilget =
            søknadsbehandlingBeregnetInnvilget(vilkårsvurderinger = vilkårsvurderingSøknadsbehandlingVurdertInnvilgetAlder())

        innvilget.second.leggTilFamiliegjenforegningvilkår(
            familiegjenforegning = familiegjenforeningVilkårInnvilget(
                nonEmptyListOf(vurderingsperiodeFamiliegjenforeningInnvilget(periode = år(2039))),
            ),
            clock = fixedClock,
        ).shouldBeRight()
    }

    @Test
    @Disabled("Beregning er ikke implementert for alder enda")
    fun `kan legge til familiegjenforening ved beregnet avslag`() {
        val avslag =
            søknadsbehandlingBeregnetAvslag(vilkårsvurderinger = vilkårsvurderingSøknadsbehandlingVurdertAvslagAlder())

        avslag.second.leggTilFamiliegjenforegningvilkår(
            familiegjenforegning = familiegjenforeningVilkårInnvilget(
                nonEmptyListOf(vurderingsperiodeFamiliegjenforeningAvslag(periode = år(2039))),
            ),
            clock = fixedClock,
        ).shouldBeRight()
    }

    @Test
    @Disabled("Beregning er ikke implementert for alder enda")
    fun `kan legge til familiegjenforening ved simulert`() {
        val simulert =
            søknadsbehandlingSimulert(vilkårsvurderinger = vilkårsvurderingSøknadsbehandlingVurdertInnvilgetAlder())

        simulert.second.leggTilFamiliegjenforegningvilkår(
            familiegjenforegning = familiegjenforeningVilkårInnvilget(
                nonEmptyListOf(vurderingsperiodeFamiliegjenforeningAvslag(periode = år(2039))),
            ),
            clock = fixedClock,
        ).shouldBeRight()
    }

    @Test
    @Disabled("Beregning er ikke implementert for alder enda")
    fun `ikke lov å legge til familiegjenforening ved tilAttesteringInnvilget`() {
        val attesteringInnvilget =
            søknadsbehandlingTilAttesteringInnvilget(vilkårsvurderinger = vilkårsvurderingSøknadsbehandlingVurdertInnvilgetAlder())

        attesteringInnvilget.second.leggTilFamiliegjenforegningvilkår(
            familiegjenforegning = familiegjenforeningVilkårInnvilget(
                nonEmptyListOf(vurderingsperiodeFamiliegjenforeningAvslag(periode = år(2039))),
            ),
            clock = fixedClock,
        ) shouldBe KunneIkkeLeggeTilFamiliegjenforegningVilkår.UgyldigTilstand(
            fra = Søknadsbehandling.TilAttestering::class,
            til = Søknadsbehandling.Vilkårsvurdert::class,
        ).left()
    }

    @Test
    @Disabled("Beregning er ikke implementert for alder enda")
    fun `ikke lov å legge til familiegjenforening ved tilAttesteringAvslagMedBeregning`() {
        val attesteringAvslagMedBeregning =
            søknadsbehandlingTilAttesteringAvslagMedBeregning(vilkårsvurderinger = vilkårsvurderingSøknadsbehandlingVurdertAvslagAlder())

        attesteringAvslagMedBeregning.second.leggTilFamiliegjenforegningvilkår(
            familiegjenforegning = familiegjenforeningVilkårInnvilget(
                nonEmptyListOf(vurderingsperiodeFamiliegjenforeningAvslag(periode = år(2039))),
            ),
            clock = fixedClock,
        ) shouldBe KunneIkkeLeggeTilFamiliegjenforegningVilkår.UgyldigTilstand(
            fra = Søknadsbehandling.TilAttestering::class,
            til = Søknadsbehandling.Vilkårsvurdert::class,
        ).left()
    }

    @Test
    fun `ikke lov å legge til familiegjenforening ved tilAttesteringAvslagUtenBeregning`() {
        val attesteringAvslagMedBeregning =
            søknadsbehandlingTilAttesteringAvslagUtenBeregning(vilkårsvurderinger = vilkårsvurderingSøknadsbehandlingVurdertAvslagAlder())

        attesteringAvslagMedBeregning.second.leggTilFamiliegjenforegningvilkår(
            familiegjenforegning = familiegjenforeningVilkårInnvilget(
                nonEmptyListOf(vurderingsperiodeFamiliegjenforeningAvslag(periode = år(2039))),
            ),
            clock = fixedClock,
        ) shouldBe KunneIkkeLeggeTilFamiliegjenforegningVilkår.UgyldigTilstand(
            fra = Søknadsbehandling.TilAttestering.Avslag.UtenBeregning::class,
            til = Søknadsbehandling.Vilkårsvurdert::class,
        ).left()
    }

    @Test
    @Disabled("Beregning er ikke implementert for alder enda")
    fun `ikke lov å legge til familiegjenforening ved IverksattInnvilget`() {
        val iverksattInnvilget =
            søknadsbehandlingIverksattInnvilget(vilkårsvurderinger = vilkårsvurderingSøknadsbehandlingVurdertInnvilgetAlder())

        iverksattInnvilget.second.leggTilFamiliegjenforegningvilkår(
            familiegjenforegning = familiegjenforeningVilkårInnvilget(
                nonEmptyListOf(vurderingsperiodeFamiliegjenforeningAvslag(periode = år(2039))),
            ),
            clock = fixedClock,
        ) shouldBe KunneIkkeLeggeTilFamiliegjenforegningVilkår.UgyldigTilstand(
            fra = Søknadsbehandling.Iverksatt::class,
            til = Søknadsbehandling.Vilkårsvurdert::class,
        ).left()
    }

    @Test
    @Disabled("Beregning er ikke implementert for alder enda")
    fun `ikke lov å legge til familiegjenforening ved IverksattAvslagMedBeregning`() {
        val iverksattInnvilget =
            søknadsbehandlingIverksattAvslagMedBeregning(vilkårsvurderinger = vilkårsvurderingSøknadsbehandlingVurdertAvslagAlder())

        iverksattInnvilget.second.leggTilFamiliegjenforegningvilkår(
            familiegjenforegning = familiegjenforeningVilkårInnvilget(
                nonEmptyListOf(vurderingsperiodeFamiliegjenforeningAvslag(periode = år(2039))),
            ),
            clock = fixedClock,
        ) shouldBe KunneIkkeLeggeTilFamiliegjenforegningVilkår.UgyldigTilstand(
            fra = Søknadsbehandling.Iverksatt::class,
            til = Søknadsbehandling.Vilkårsvurdert::class,
        ).left()
    }

    @Test
    fun `ikke lov å legge til familiegjenforening ved IverksattAvslagUtenBeregning`() {
        val iverksattInnvilget =
            søknadsbehandlingIverksattAvslagUtenBeregning(vilkårsvurderinger = vilkårsvurderingSøknadsbehandlingVurdertAvslagAlder())

        iverksattInnvilget.second.leggTilFamiliegjenforegningvilkår(
            familiegjenforegning = familiegjenforeningVilkårInnvilget(
                nonEmptyListOf(vurderingsperiodeFamiliegjenforeningAvslag(periode = år(2039))),
            ),
            clock = fixedClock,
        ) shouldBe KunneIkkeLeggeTilFamiliegjenforegningVilkår.UgyldigTilstand(
            fra = Søknadsbehandling.Iverksatt.Avslag.UtenBeregning::class,
            til = Søknadsbehandling.Vilkårsvurdert::class,
        ).left()
    }

    @Test
    @Disabled("Beregning er ikke implementert for alder enda")
    fun `kan legge til familiegjenforening ved underkjentInnvilget`() {
        val iverksattInnvilget =
            søknadsbehandlingUnderkjentInnvilget(vilkårsvurderinger = vilkårsvurderingSøknadsbehandlingVurdertInnvilgetAlder())

        iverksattInnvilget.second.leggTilFamiliegjenforegningvilkår(
            familiegjenforegning = familiegjenforeningVilkårInnvilget(
                nonEmptyListOf(vurderingsperiodeFamiliegjenforeningAvslag(periode = år(2039))),
            ),
            clock = fixedClock,
        ) shouldBe KunneIkkeLeggeTilFamiliegjenforegningVilkår.UgyldigTilstand(
            fra = Søknadsbehandling.Underkjent::class,
            til = Søknadsbehandling.Vilkårsvurdert::class,
        ).left()
    }

    @Test
    @Disabled("Beregning er ikke implementert for alder enda")
    fun `kan legge til familiegjenforening ved underkjentAvslagMedBeregning`() {
        val iverksattInnvilget =
            søknadsbehandlingUnderkjentAvslagMedBeregning(vilkårsvurderinger = vilkårsvurderingSøknadsbehandlingVurdertAvslagAlder())

        iverksattInnvilget.second.leggTilFamiliegjenforegningvilkår(
            familiegjenforegning = familiegjenforeningVilkårInnvilget(
                nonEmptyListOf(vurderingsperiodeFamiliegjenforeningAvslag(periode = år(2039))),
            ),
            clock = fixedClock,
        ) shouldBe KunneIkkeLeggeTilFamiliegjenforegningVilkår.UgyldigTilstand(
            fra = Søknadsbehandling.Underkjent::class,
            til = Søknadsbehandling.Vilkårsvurdert::class,
        ).left()
    }

    @Test
    fun `kan legge til familiegjenforening ved underkjentAvslagUtenBeregning`() {
        val iverksattInnvilget =
            søknadsbehandlingUnderkjentAvslagUtenBeregning(vilkårsvurderinger = vilkårsvurderingSøknadsbehandlingVurdertAvslagAlder())

        iverksattInnvilget.second.leggTilFamiliegjenforegningvilkår(
            familiegjenforegning = familiegjenforeningVilkårInnvilget(
                nonEmptyListOf(vurderingsperiodeFamiliegjenforeningAvslag(periode = år(2039))),
            ),
            clock = fixedClock,
        ).shouldBeRight()
    }
}
