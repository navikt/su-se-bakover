package no.nav.su.se.bakover.domain.søknadsbehandling

import arrow.core.left
import arrow.core.nonEmptyListOf
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.Personopplysninger
import no.nav.su.se.bakover.test.beregnetSøknadsbehandling
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.nySakAlder
import no.nav.su.se.bakover.test.nySøknadsbehandlingMedStønadsperiode
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.simulertSøknadsbehandling
import no.nav.su.se.bakover.test.søknad.nySakMedjournalførtSøknadOgOppgave
import no.nav.su.se.bakover.test.søknad.søknadsinnholdAlder
import no.nav.su.se.bakover.test.søknadsbehandlingBeregnetAvslag
import no.nav.su.se.bakover.test.søknadsbehandlingIverksattAvslagMedBeregning
import no.nav.su.se.bakover.test.søknadsbehandlingIverksattAvslagUtenBeregning
import no.nav.su.se.bakover.test.søknadsbehandlingIverksattInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingTilAttesteringAvslagMedBeregning
import no.nav.su.se.bakover.test.søknadsbehandlingTilAttesteringAvslagUtenBeregning
import no.nav.su.se.bakover.test.søknadsbehandlingTilAttesteringInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingUnderkjentAvslagMedBeregning
import no.nav.su.se.bakover.test.søknadsbehandlingUnderkjentAvslagUtenBeregning
import no.nav.su.se.bakover.test.søknadsbehandlingUnderkjentInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertAvslag
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertInnvilget
import no.nav.su.se.bakover.test.vilkår.familiegjenforeningVilkårInnvilget
import no.nav.su.se.bakover.test.vilkårsvurderingSøknadsbehandlingVurdertAvslagAlder
import no.nav.su.se.bakover.test.vilkårsvurderingSøknadsbehandlingVurdertInnvilgetAlder
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import vurderingsperiode.vurderingsperiodeFamiliegjenforeningAvslag
import vurderingsperiode.vurderingsperiodeFamiliegjenforeningInnvilget

internal class LeggTilFamiliegjenforeningTest {

    @Test
    fun `kan legge til familiegjenforening ved uavklart`() {
        val fnr = Fnr.generer()
        val uavklart = nySøknadsbehandlingMedStønadsperiode(
            sakOgSøknad = nySakMedjournalførtSøknadOgOppgave(
                fnr = fnr,
                søknadInnhold = søknadsinnholdAlder(personopplysninger = Personopplysninger(fnr)),
            ),
        )

        uavklart.second.leggTilFamiliegjenforeningvilkår(
            familiegjenforening = familiegjenforeningVilkårInnvilget(),
            saksbehandler = saksbehandler,
        ).shouldBeRight()
    }

    @Test
    fun `kan legge til familiegjenforening ved vilkårsvurdert innvilget`() {
        val innvilget =
            søknadsbehandlingVilkårsvurdertInnvilget(
                customVilkår = vilkårsvurderingSøknadsbehandlingVurdertInnvilgetAlder().vilkår.toList(),
                sakOgSøknad = nySakAlder(),
            )

        innvilget.second.leggTilFamiliegjenforeningvilkår(
            familiegjenforening = familiegjenforeningVilkårInnvilget(
                nonEmptyListOf(vurderingsperiodeFamiliegjenforeningAvslag()),
            ),
            saksbehandler = saksbehandler,
        )
    }

    @Test
    fun `kan legge til familiegjenforening ved vilkårsvurdert avslag`() {
        val avslag =
            søknadsbehandlingVilkårsvurdertAvslag(
                customVilkår = vilkårsvurderingSøknadsbehandlingVurdertAvslagAlder().vilkår.toList(),
                sakOgSøknad = nySakAlder(),
            )

        avslag.second.leggTilFamiliegjenforeningvilkår(
            familiegjenforening = familiegjenforeningVilkårInnvilget(
                nonEmptyListOf(vurderingsperiodeFamiliegjenforeningInnvilget()),
            ),
            saksbehandler = saksbehandler,
        ).shouldBeRight()
    }

    @Test
    @Disabled("Beregning er ikke implementert for alder enda")
    fun `kan legge til familiegjenforening ved beregnet innvilget`() {
        val innvilget =
            beregnetSøknadsbehandling(customVilkår = vilkårsvurderingSøknadsbehandlingVurdertInnvilgetAlder().vilkår.toList())

        innvilget.second.leggTilFamiliegjenforeningvilkår(
            familiegjenforening = familiegjenforeningVilkårInnvilget(
                nonEmptyListOf(vurderingsperiodeFamiliegjenforeningAvslag()),
            ),
            saksbehandler = saksbehandler,
        ).shouldBeRight()
    }

    @Test
    @Disabled("Beregning er ikke implementert for alder enda")
    fun `kan legge til familiegjenforening ved beregnet avslag`() {
        val avslag =
            søknadsbehandlingBeregnetAvslag(customVilkår = vilkårsvurderingSøknadsbehandlingVurdertAvslagAlder().vilkår.toList())

        avslag.second.leggTilFamiliegjenforeningvilkår(
            familiegjenforening = familiegjenforeningVilkårInnvilget(
                nonEmptyListOf(vurderingsperiodeFamiliegjenforeningInnvilget()),
            ),
            saksbehandler = saksbehandler,
        ).shouldBeRight()
    }

    @Test
    @Disabled("Beregning er ikke implementert for alder enda")
    fun `kan legge til familiegjenforening ved simulert`() {
        val simulert =
            simulertSøknadsbehandling(customVilkår = vilkårsvurderingSøknadsbehandlingVurdertInnvilgetAlder().vilkår.toList())

        simulert.second.leggTilFamiliegjenforeningvilkår(
            familiegjenforening = familiegjenforeningVilkårInnvilget(
                nonEmptyListOf(vurderingsperiodeFamiliegjenforeningInnvilget()),
            ),
            saksbehandler = saksbehandler,
        ).shouldBeRight()
    }

    @Test
    @Disabled("Beregning er ikke implementert for alder enda")
    fun `ikke lov å legge til familiegjenforening ved tilAttesteringInnvilget`() {
        val attesteringInnvilget =
            søknadsbehandlingTilAttesteringInnvilget(customVilkår = vilkårsvurderingSøknadsbehandlingVurdertInnvilgetAlder().vilkår.toList())

        attesteringInnvilget.second.leggTilFamiliegjenforeningvilkår(
            familiegjenforening = familiegjenforeningVilkårInnvilget(
                nonEmptyListOf(vurderingsperiodeFamiliegjenforeningAvslag()),
            ),
            saksbehandler = saksbehandler,
        ) shouldBe KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilFamiliegjenforeningVilkår.UgyldigTilstand(
            fra = SøknadsbehandlingTilAttestering::class,
            til = VilkårsvurdertSøknadsbehandling::class,
        ).left()
    }

    @Test
    @Disabled("Beregning er ikke implementert for alder enda")
    fun `ikke lov å legge til familiegjenforening ved tilAttesteringAvslagMedBeregning`() {
        val attesteringAvslagMedBeregning =
            søknadsbehandlingTilAttesteringAvslagMedBeregning(customVilkår = vilkårsvurderingSøknadsbehandlingVurdertAvslagAlder().vilkår.toList())

        attesteringAvslagMedBeregning.second.leggTilFamiliegjenforeningvilkår(
            familiegjenforening = familiegjenforeningVilkårInnvilget(
                nonEmptyListOf(vurderingsperiodeFamiliegjenforeningInnvilget()),
            ),
            saksbehandler = saksbehandler,
        ) shouldBe KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilFamiliegjenforeningVilkår.UgyldigTilstand(
            fra = SøknadsbehandlingTilAttestering::class,
            til = VilkårsvurdertSøknadsbehandling::class,
        ).left()
    }

    @Test
    fun `ikke lov å legge til familiegjenforening ved tilAttesteringAvslagUtenBeregning`() {
        val attesteringAvslagMedBeregning =
            søknadsbehandlingTilAttesteringAvslagUtenBeregning(customVilkår = vilkårsvurderingSøknadsbehandlingVurdertAvslagAlder().vilkår.toList())

        attesteringAvslagMedBeregning.second.leggTilFamiliegjenforeningvilkår(
            familiegjenforening = familiegjenforeningVilkårInnvilget(
                nonEmptyListOf(vurderingsperiodeFamiliegjenforeningInnvilget()),
            ),
            saksbehandler = saksbehandler,
        ) shouldBe KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilFamiliegjenforeningVilkår.UgyldigTilstand(
            fra = SøknadsbehandlingTilAttestering.Avslag.UtenBeregning::class,
            til = VilkårsvurdertSøknadsbehandling::class,
        ).left()
    }

    @Test
    @Disabled("Beregning er ikke implementert for alder enda")
    fun `ikke lov å legge til familiegjenforening ved IverksattInnvilget`() {
        val iverksattInnvilget =
            søknadsbehandlingIverksattInnvilget(customVilkår = vilkårsvurderingSøknadsbehandlingVurdertInnvilgetAlder().vilkår.toList())

        iverksattInnvilget.second.leggTilFamiliegjenforeningvilkår(
            familiegjenforening = familiegjenforeningVilkårInnvilget(
                nonEmptyListOf(vurderingsperiodeFamiliegjenforeningAvslag()),
            ),
            saksbehandler = saksbehandler,
        ) shouldBe KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilFamiliegjenforeningVilkår.UgyldigTilstand(
            fra = IverksattSøknadsbehandling::class,
            til = VilkårsvurdertSøknadsbehandling::class,
        ).left()
    }

    @Test
    @Disabled("Beregning er ikke implementert for alder enda")
    fun `ikke lov å legge til familiegjenforening ved IverksattAvslagMedBeregning`() {
        val iverksattInnvilget =
            søknadsbehandlingIverksattAvslagMedBeregning(customVilkår = vilkårsvurderingSøknadsbehandlingVurdertAvslagAlder().vilkår.toList())

        iverksattInnvilget.second.leggTilFamiliegjenforeningvilkår(
            familiegjenforening = familiegjenforeningVilkårInnvilget(
                nonEmptyListOf(vurderingsperiodeFamiliegjenforeningInnvilget()),
            ),
            saksbehandler = saksbehandler,
        ) shouldBe KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilFamiliegjenforeningVilkår.UgyldigTilstand(
            fra = IverksattSøknadsbehandling::class,
            til = VilkårsvurdertSøknadsbehandling::class,
        ).left()
    }

    @Test
    fun `ikke lov å legge til familiegjenforening ved IverksattAvslagUtenBeregning`() {
        val iverksattInnvilget =
            søknadsbehandlingIverksattAvslagUtenBeregning(customVilkår = vilkårsvurderingSøknadsbehandlingVurdertAvslagAlder().vilkår.toList())

        iverksattInnvilget.second.leggTilFamiliegjenforeningvilkår(
            familiegjenforening = familiegjenforeningVilkårInnvilget(
                nonEmptyListOf(vurderingsperiodeFamiliegjenforeningInnvilget()),
            ),
            saksbehandler = saksbehandler,
        ) shouldBe KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilFamiliegjenforeningVilkår.UgyldigTilstand(
            fra = IverksattSøknadsbehandling.Avslag.UtenBeregning::class,
            til = VilkårsvurdertSøknadsbehandling::class,
        ).left()
    }

    @Test
    @Disabled("Beregning er ikke implementert for alder enda")
    fun `kan legge til familiegjenforening ved underkjentInnvilget`() {
        val iverksattInnvilget =
            søknadsbehandlingUnderkjentInnvilget(customVilkår = vilkårsvurderingSøknadsbehandlingVurdertInnvilgetAlder().vilkår.toList())

        iverksattInnvilget.second.leggTilFamiliegjenforeningvilkår(
            familiegjenforening = familiegjenforeningVilkårInnvilget(
                nonEmptyListOf(vurderingsperiodeFamiliegjenforeningAvslag()),
            ),
            saksbehandler = saksbehandler,
        ).shouldBeRight()
    }

    @Test
    @Disabled("Beregning er ikke implementert for alder enda")
    fun `kan legge til familiegjenforening ved underkjentAvslagMedBeregning`() {
        val iverksattInnvilget =
            søknadsbehandlingUnderkjentAvslagMedBeregning(customVilkår = vilkårsvurderingSøknadsbehandlingVurdertAvslagAlder().vilkår.toList())

        iverksattInnvilget.second.leggTilFamiliegjenforeningvilkår(
            familiegjenforening = familiegjenforeningVilkårInnvilget(
                nonEmptyListOf(vurderingsperiodeFamiliegjenforeningInnvilget()),
            ),
            saksbehandler = saksbehandler,
        ).shouldBeRight()
    }

    @Test
    fun `kan legge til familiegjenforening ved underkjentAvslagUtenBeregning`() {
        val iverksattInnvilget =
            søknadsbehandlingUnderkjentAvslagUtenBeregning(
                customVilkår = vilkårsvurderingSøknadsbehandlingVurdertAvslagAlder().vilkår.toList(),
                sakOgSøknad = nySakAlder(),
            )

        iverksattInnvilget.second.leggTilFamiliegjenforeningvilkår(
            familiegjenforening = familiegjenforeningVilkårInnvilget(
                nonEmptyListOf(vurderingsperiodeFamiliegjenforeningInnvilget()),
            ),
            saksbehandler = saksbehandler,
        ).shouldBeRight()
    }
}
