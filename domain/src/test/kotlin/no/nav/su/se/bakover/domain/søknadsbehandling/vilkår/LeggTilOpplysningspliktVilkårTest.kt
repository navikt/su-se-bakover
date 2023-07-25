package no.nav.su.se.bakover.domain.søknadsbehandling.vilkår

import no.nav.su.se.bakover.domain.søknadsbehandling.SimulertSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.UnderkjentSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.VilkårsvurdertSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.simulert
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.shouldBeType
import no.nav.su.se.bakover.test.simulertSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.underkjentSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.vilkår.tilstrekkeligDokumentert
import no.nav.su.se.bakover.test.vilkår.utilstrekkeligDokumentert
import no.nav.su.se.bakover.test.vilkårsvurdertSøknadsbehandlingUføre
import org.junit.jupiter.api.Test

internal class LeggTilOpplysningspliktVilkårTest {

    @Test
    fun `simulert til vilkårsvurdert innvilget`() {
        simulertSøknadsbehandlingUføre().also { (_, simulert) ->
            simulert.shouldBeType<SimulertSøknadsbehandling>().also {
                simulert.leggTilOpplysningspliktVilkår(
                    vilkår = tilstrekkeligDokumentert(),
                    saksbehandler = simulert.saksbehandler,
                )
                    .getOrFail()
                    .also { innvilget ->
                        innvilget.shouldBeType<VilkårsvurdertSøknadsbehandling.Innvilget>()
                    }
            }
        }
    }

    @Test
    fun `simulert til vilkårsvurdert avslag`() {
        simulertSøknadsbehandlingUføre().also { (_, simulert) ->
            simulert.shouldBeType<SimulertSøknadsbehandling>().also {
                simulert.leggTilOpplysningspliktVilkår(
                    vilkår = utilstrekkeligDokumentert(),
                    saksbehandler = simulert.saksbehandler,
                )
                    .getOrFail()
                    .also { avslag ->
                        avslag.shouldBeType<VilkårsvurdertSøknadsbehandling.Avslag>()
                    }
            }
        }
    }

    @Test
    fun `underkjent innvilget til vilkårsvurdert innvilget`() {
        underkjentSøknadsbehandlingUføre().also { (_, underkjent) ->
            underkjent.shouldBeType<UnderkjentSøknadsbehandling.Innvilget>().also {
                underkjent.leggTilOpplysningspliktVilkår(
                    vilkår = tilstrekkeligDokumentert(),
                    saksbehandler = simulert.saksbehandler,
                )
                    .getOrFail()
                    .also { innvilget ->
                        innvilget.shouldBeType<VilkårsvurdertSøknadsbehandling.Innvilget>()
                    }
            }
        }
    }

    @Test
    fun `vilkårsvurdert innvilget til vilkårsvurdert avslag`() {
        vilkårsvurdertSøknadsbehandlingUføre().also { (_, innvilget) ->
            innvilget.shouldBeType<VilkårsvurdertSøknadsbehandling.Innvilget>().also {
                it.leggTilOpplysningspliktVilkår(
                    vilkår = utilstrekkeligDokumentert(),
                    saksbehandler = saksbehandler,
                )
                    .getOrFail()
                    .also { avslag ->
                        avslag.shouldBeType<VilkårsvurdertSøknadsbehandling.Avslag>()
                    }
            }
        }
    }
}
