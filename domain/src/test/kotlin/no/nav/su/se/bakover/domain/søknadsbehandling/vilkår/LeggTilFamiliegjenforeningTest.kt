package no.nav.su.se.bakover.domain.søknadsbehandling.vilkår

import arrow.core.nonEmptyListOf
import io.kotest.assertions.arrow.core.shouldBeRight
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.FnrWrapper
import no.nav.su.se.bakover.test.beregnetSøknadsbehandlingInnvilget
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.nySakAlder
import no.nav.su.se.bakover.test.nySøknadsbehandlingMedStønadsperiode
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.simulertSøknadsbehandling
import no.nav.su.se.bakover.test.søknad.nySakMedjournalførtSøknadOgOppgave
import no.nav.su.se.bakover.test.søknad.søknadsinnholdAlder
import no.nav.su.se.bakover.test.søknadsbehandlingUnderkjentAvslagUtenBeregning
import no.nav.su.se.bakover.test.søknadsbehandlingUnderkjentInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertAvslag
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertInnvilget
import no.nav.su.se.bakover.test.vilkår.familiegjenforeningVilkårInnvilget
import no.nav.su.se.bakover.test.vilkårsvurderingSøknadsbehandlingVurdertInnvilgetAlder
import org.junit.jupiter.api.Test
import vurderingsperiode.vurderingsperiodeFamiliegjenforeningAvslag
import vurderingsperiode.vurderingsperiodeFamiliegjenforeningInnvilget

internal class LeggTilFamiliegjenforeningTest {

    @Test
    fun `kan legge til familiegjenforening ved uavklart`() {
        val fnr = Fnr.generer()
        val uavklart = nySøknadsbehandlingMedStønadsperiode(
            sakstype = Sakstype.ALDER,
            sakOgSøknad = nySakMedjournalførtSøknadOgOppgave(
                fnr = fnr,
                søknadInnhold = søknadsinnholdAlder(personopplysninger = FnrWrapper(fnr)),
            ),
        )

        uavklart.second.leggTilFamiliegjenforeningvilkår(
            vilkår = familiegjenforeningVilkårInnvilget(),
            saksbehandler = saksbehandler,
        ).shouldBeRight()
    }

    @Test
    fun `kan legge til familiegjenforening ved vilkårsvurdert innvilget`() {
        val innvilget =
            søknadsbehandlingVilkårsvurdertInnvilget(
                sakOgSøknad = nySakAlder(),
            )

        innvilget.second.leggTilFamiliegjenforeningvilkår(
            vilkår = familiegjenforeningVilkårInnvilget(
                nonEmptyListOf(vurderingsperiodeFamiliegjenforeningInnvilget()),
            ),
            saksbehandler = saksbehandler,
        ).shouldBeRight()
    }

    @Test
    fun `kan legge til familiegjenforening ved vilkårsvurdert avslag`() {
        val avslag =
            søknadsbehandlingVilkårsvurdertAvslag(
                sakOgSøknad = nySakAlder(),
            )

        avslag.second.leggTilFamiliegjenforeningvilkår(
            vilkår = familiegjenforeningVilkårInnvilget(
                nonEmptyListOf(vurderingsperiodeFamiliegjenforeningInnvilget()),
            ),
            saksbehandler = saksbehandler,
        ).shouldBeRight()
    }

    @Test
    fun `kan legge til familiegjenforening ved beregnet innvilget`() {
        val innvilget =
            beregnetSøknadsbehandlingInnvilget(sakOgSøknad = nySakAlder(), customVilkår = vilkårsvurderingSøknadsbehandlingVurdertInnvilgetAlder().vilkår.toList())

        innvilget.second.leggTilFamiliegjenforeningvilkår(
            vilkår = familiegjenforeningVilkårInnvilget(
                nonEmptyListOf(vurderingsperiodeFamiliegjenforeningAvslag()),
            ),
            saksbehandler = saksbehandler,
        ).shouldBeRight()
    }

    @Test
    fun `kan legge til familiegjenforening ved simulert`() {
        val simulert =
            simulertSøknadsbehandling(sakOgSøknad = nySakAlder(), customVilkår = vilkårsvurderingSøknadsbehandlingVurdertInnvilgetAlder().vilkår.toList())

        simulert.second.leggTilFamiliegjenforeningvilkår(
            vilkår = familiegjenforeningVilkårInnvilget(
                nonEmptyListOf(vurderingsperiodeFamiliegjenforeningInnvilget()),
            ),
            saksbehandler = saksbehandler,
        ).shouldBeRight()
    }

    @Test
    fun `kan legge til familiegjenforening ved underkjentInnvilget`() {
        val iverksattInnvilget =
            søknadsbehandlingUnderkjentInnvilget(sakOgSøknad = nySakAlder(), customVilkår = vilkårsvurderingSøknadsbehandlingVurdertInnvilgetAlder().vilkår.toList())

        iverksattInnvilget.second.leggTilFamiliegjenforeningvilkår(
            vilkår = familiegjenforeningVilkårInnvilget(
                nonEmptyListOf(vurderingsperiodeFamiliegjenforeningAvslag()),
            ),
            saksbehandler = saksbehandler,
        ).shouldBeRight()
    }

    @Test
    fun `kan legge til familiegjenforening ved underkjentAvslagUtenBeregning`() {
        val iverksattInnvilget =
            søknadsbehandlingUnderkjentAvslagUtenBeregning(
                sakOgSøknad = nySakAlder(),
            )

        iverksattInnvilget.second.leggTilFamiliegjenforeningvilkår(
            vilkår = familiegjenforeningVilkårInnvilget(
                nonEmptyListOf(vurderingsperiodeFamiliegjenforeningInnvilget()),
            ),
            saksbehandler = saksbehandler,
        ).shouldBeRight()
    }
}
