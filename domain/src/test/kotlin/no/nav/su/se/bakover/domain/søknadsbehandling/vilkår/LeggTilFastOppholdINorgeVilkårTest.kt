package no.nav.su.se.bakover.domain.søknadsbehandling.vilkår

import no.nav.su.se.bakover.domain.søknadsbehandling.VilkårsvurdertSøknadsbehandling
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.nySøknadsbehandlingUføre
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.shouldBeType
import no.nav.su.se.bakover.test.vilkår.fastOppholdVilkårVurdertTilUavklart
import org.junit.jupiter.api.Test

internal class LeggTilFastOppholdINorgeVilkårTest {
    @Test
    fun `opprettet til vilkårsvurdert uavklart (opprettet)`() {
        nySøknadsbehandlingUføre().also { (_, ny) ->
            ny.shouldBeType<VilkårsvurdertSøknadsbehandling.Uavklart>().also {
                it.leggTilFastOppholdINorgeVilkår(
                    saksbehandler = saksbehandler,
                    vilkår = fastOppholdVilkårVurdertTilUavklart(),
                ).getOrFail().also {
                    it.shouldBeType<VilkårsvurdertSøknadsbehandling.Uavklart>()
                }
            }
        }
    }
}
