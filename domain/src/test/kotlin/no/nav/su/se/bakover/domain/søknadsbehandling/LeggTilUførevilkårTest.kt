package no.nav.su.se.bakover.domain.søknadsbehandling

import arrow.core.left
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beInstanceOf
import no.nav.su.se.bakover.common.extensions.januar
import no.nav.su.se.bakover.common.extensions.mapSecond
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.test.beregnetSøknadsbehandling
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.nySøknadsbehandlingMedStønadsperiode
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.simulertSøknadsbehandling
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
import no.nav.su.se.bakover.test.vilkårsvurderinger.innvilgetUførevilkår
import org.junit.jupiter.api.Test

internal class LeggTilUførevilkårTest {
    @Test
    fun `får ikke legge til uførevilkår utenfor perioden`() {
        val uavklart = nySøknadsbehandlingMedStønadsperiode().second

        uavklart.leggTilUførevilkår(
            uførhet = innvilgetUførevilkår(
                periode = januar(2020),
            ),
            saksbehandler = saksbehandler,
            clock = fixedClock,
        ) shouldBe KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilUførevilkår.VurderingsperiodeUtenforBehandlingsperiode.left()

        uavklart.leggTilUførevilkår(
            uførhet = innvilgetUførevilkår(
                periode = Periode.create(1.januar(2020), 31.januar(2025)),
            ),
            saksbehandler = saksbehandler,
            clock = fixedClock,
        ) shouldBe KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilUførevilkår.VurderingsperiodeUtenforBehandlingsperiode.left()

        uavklart.leggTilUførevilkår(
            uførhet = innvilgetUførevilkår(
                periode = uavklart.periode,
            ),
            saksbehandler = saksbehandler,
            clock = fixedClock,
        ).isRight() shouldBe true
    }

    @Test
    fun `får bare lagt til uførevilkår for enkelte typer`() {
        listOf(
            nySøknadsbehandlingMedStønadsperiode().let {
                it.first to it.second
            },
            søknadsbehandlingVilkårsvurdertAvslag(),
            søknadsbehandlingVilkårsvurdertInnvilget(),
            søknadsbehandlingBeregnetAvslag(),
            beregnetSøknadsbehandling().mapSecond { it as BeregnetSøknadsbehandling.Innvilget },
            simulertSøknadsbehandling(),
            søknadsbehandlingUnderkjentInnvilget(),
            søknadsbehandlingUnderkjentAvslagUtenBeregning(),
            søknadsbehandlingUnderkjentAvslagMedBeregning(),
        ).map {
            it.second
        }.forEach {
            it.leggTilUførevilkår(
                uførhet = innvilgetUførevilkår(),
                saksbehandler = saksbehandler,
                clock = fixedClock,
            ).let { oppdatert ->
                oppdatert.isRight() shouldBe true
                oppdatert.getOrFail() shouldBe beInstanceOf<VilkårsvurdertSøknadsbehandling>()
            }
        }

        listOf(
            søknadsbehandlingTilAttesteringInnvilget().second,
            søknadsbehandlingTilAttesteringAvslagMedBeregning().second,
            søknadsbehandlingTilAttesteringAvslagUtenBeregning().second,
            søknadsbehandlingIverksattInnvilget().second,
            søknadsbehandlingIverksattAvslagMedBeregning().second,
            søknadsbehandlingIverksattAvslagUtenBeregning().second,
        ).forEach {
            it.leggTilUførevilkår(
                uførhet = innvilgetUførevilkår(),
                saksbehandler = saksbehandler,
                clock = fixedClock,
            ) shouldBe KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilUførevilkår.UgyldigTilstand(
                fra = it::class,
                til = VilkårsvurdertSøknadsbehandling::class,
            ).left()
        }
    }
}
