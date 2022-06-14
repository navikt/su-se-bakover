package no.nav.su.se.bakover.domain.søknadsbehandling

import arrow.core.nonEmptyListOf
import io.kotest.assertions.arrow.core.shouldBeRight
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertAvslag
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertUavklart
import no.nav.su.se.bakover.test.vilkår.familiegjenforeningVilkårInnvilget
import no.nav.su.se.bakover.test.vilkårsvurderingSøknadsbehandlingIkkeVurdertAlder
import no.nav.su.se.bakover.test.vilkårsvurderingSøknadsbehandlingVurdertAvslagAlder
import no.nav.su.se.bakover.test.vilkårsvurderingSøknadsbehandlingVurdertInnvilgetAlder
import org.junit.jupiter.api.Test
import vurderingsperiode.vurderingsperiodeFamiliegjenforeningInnvilget

private class LeggTilFamiliegjenforeningTest {

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
        val avslag = søknadsbehandlingVilkårsvurdertAvslag(vilkårsvurderinger = vilkårsvurderingSøknadsbehandlingVurdertAvslagAlder())

        avslag.second.leggTilFamiliegjenforegningvilkår(
            familiegjenforegning = familiegjenforeningVilkårInnvilget(
                nonEmptyListOf(vurderingsperiodeFamiliegjenforeningInnvilget(periode = år(2039))),
            ),
            clock = fixedClock,
        ).shouldBeRight()
    }
}
