package no.nav.su.se.bakover.domain.søknadsbehandling

import io.kotest.assertions.arrow.core.shouldBeRight
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertUavklart
import no.nav.su.se.bakover.test.vilkår.familiegjenforeningVilkår
import no.nav.su.se.bakover.test.vilkårsvurderingSøknadsbehandlingIkkeVurdertAlder
import org.junit.jupiter.api.Test

private class LeggTilFamiliegjenforeningTest {

    @Test
    fun `kan legge til familiegjenforening ved uavklart`() {
        val uavklart =
            søknadsbehandlingVilkårsvurdertUavklart(vilkårsvurderinger = vilkårsvurderingSøknadsbehandlingIkkeVurdertAlder())

        uavklart.second.leggTilFamiliegjenforegningvilkår(
            familiegjenforegning = familiegjenforeningVilkår(),
            clock = fixedClock,
        ).shouldBeRight()
    }

    @Test
    fun `kan legge til familiegjenforegning ved vilkårsvurdert innvilget`() {
        val innvilget = søknadsbehandlingVilkårsvurdertInnvilget()
    }
}
