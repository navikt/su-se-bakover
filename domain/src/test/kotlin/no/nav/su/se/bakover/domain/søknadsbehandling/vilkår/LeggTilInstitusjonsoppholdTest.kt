package no.nav.su.se.bakover.domain.søknadsbehandling.vilkår

import beregning.domain.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.søknadsbehandling.BeregnetSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.UnderkjentSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.VilkårsvurdertSøknadsbehandling
import no.nav.su.se.bakover.test.beregnetSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.nySøknadsbehandlingUføre
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.shouldBeType
import no.nav.su.se.bakover.test.underkjentSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.vilkår.institusjonsoppholdvilkårAvslag
import no.nav.su.se.bakover.test.vilkår.institusjonsoppholdvilkårInnvilget
import no.nav.su.se.bakover.test.vilkårsvurdertSøknadsbehandlingUføre
import org.junit.jupiter.api.Test

internal class LeggTilInstitusjonsoppholdTest {
    @Test
    fun `opprettet til vilkårsvurdert avslag`() {
        nySøknadsbehandlingUføre().also { (_, ny) ->
            ny.shouldBeType<VilkårsvurdertSøknadsbehandling.Uavklart>().also {
                it.leggTilInstitusjonsoppholdVilkår(
                    vilkår = institusjonsoppholdvilkårAvslag(),
                    saksbehandler = saksbehandler,
                ).getOrFail().also {
                    it.shouldBeType<VilkårsvurdertSøknadsbehandling.Avslag>()
                }
            }
        }
    }

    @Test
    fun `vilkårsvurdert innvilget til vilkårsvurdert avslag`() {
        vilkårsvurdertSøknadsbehandlingUføre().also { (_, innvilget) ->
            innvilget.shouldBeType<VilkårsvurdertSøknadsbehandling.Innvilget>().also {
                it.leggTilInstitusjonsoppholdVilkår(
                    saksbehandler = saksbehandler,
                    vilkår = institusjonsoppholdvilkårAvslag(),
                ).getOrFail().also { avslag ->
                    avslag.shouldBeType<VilkårsvurdertSøknadsbehandling.Avslag>()
                }
            }
        }
    }

    @Test
    fun `vilkårsvurdert avslag til vilkårsvurdert innvilget`() {
        vilkårsvurdertSøknadsbehandlingUføre(
            customVilkår = listOf(institusjonsoppholdvilkårAvslag()),
        ).also { (_, avslag) ->
            avslag.shouldBeType<VilkårsvurdertSøknadsbehandling.Avslag>().also {
                it.leggTilInstitusjonsoppholdVilkår(
                    saksbehandler = saksbehandler,
                    institusjonsoppholdvilkårInnvilget(),
                ).getOrFail()
                    .also { innvilget ->
                        innvilget.shouldBeType<VilkårsvurdertSøknadsbehandling.Innvilget>()
                    }
            }
        }
    }

    @Test
    fun `beregnet innvilget til vilkårsvurdert innvilget`() {
        beregnetSøknadsbehandlingUføre().also { (_, beregnet) ->
            beregnet.shouldBeType<BeregnetSøknadsbehandling.Innvilget>().also {
                beregnet.leggTilInstitusjonsoppholdVilkår(
                    saksbehandler = saksbehandler,
                    institusjonsoppholdvilkårInnvilget(),
                ).getOrFail()
                    .also { innvilget ->
                        innvilget.shouldBeType<VilkårsvurdertSøknadsbehandling.Innvilget>()
                    }
            }
        }
    }

    @Test
    fun `beregnet avslag til vilkårsvurdert avslag`() {
        beregnetSøknadsbehandlingUføre(
            customGrunnlag = listOf(
                fradragsgrunnlagArbeidsinntekt(
                    arbeidsinntekt = 50000.0,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
        ).also { (_, beregnet) ->
            beregnet.shouldBeType<BeregnetSøknadsbehandling.Avslag>().also {
                beregnet.leggTilInstitusjonsoppholdVilkår(
                    saksbehandler = saksbehandler,
                    institusjonsoppholdvilkårAvslag(),
                ).getOrFail()
                    .also { innvilget -> innvilget.shouldBeType<VilkårsvurdertSøknadsbehandling.Avslag>() }
            }
        }
    }

    @Test
    fun `underkjent innvilget til vilkårsvurdert avslag`() {
        underkjentSøknadsbehandlingUføre().also { (_, underkjent) ->
            underkjent.shouldBeType<UnderkjentSøknadsbehandling.Innvilget>().also {
                underkjent.leggTilInstitusjonsoppholdVilkår(
                    saksbehandler = saksbehandler,
                    institusjonsoppholdvilkårAvslag(),
                ).getOrFail().also { avslag ->
                    avslag.shouldBeType<VilkårsvurdertSøknadsbehandling.Avslag>()
                }
            }
        }
    }

    @Test
    fun `underkjent avslag beregning til vilkårsvurdert avslag`() {
        underkjentSøknadsbehandlingUføre(
            customGrunnlag = listOf(fradragsgrunnlagArbeidsinntekt(arbeidsinntekt = 50000.0)),
        ).also { (_, underkjent) ->
            underkjent.shouldBeType<UnderkjentSøknadsbehandling.Avslag.MedBeregning>().also {
                underkjent.leggTilInstitusjonsoppholdVilkår(
                    saksbehandler = saksbehandler,
                    vilkår = institusjonsoppholdvilkårAvslag(),
                ).getOrFail().also { avslag ->
                    avslag.shouldBeType<VilkårsvurdertSøknadsbehandling.Avslag>()
                }
            }
        }
    }
}
