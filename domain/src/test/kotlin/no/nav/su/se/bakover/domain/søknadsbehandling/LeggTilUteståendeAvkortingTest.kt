package no.nav.su.se.bakover.domain.søknadsbehandling

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.simuleringFeilutbetaling
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
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class LeggTilUteståendeAvkortingTest {
    @Test
    fun `kan ikke håndtere avkorting for typer hvor vilkår ikke er oppfyllt`() {
        listOf(
            søknadsbehandlingVilkårsvurdertUavklart(),
            søknadsbehandlingVilkårsvurdertAvslag(),
            søknadsbehandlingUnderkjentAvslagUtenBeregning(),
        ).forEach { (sak, søknadsbehandling) ->
            val uteståendeAvkorting = AvkortingVedSøknadsbehandling.Uhåndtert.UteståendeAvkorting(
                avkortingsvarsel = Avkortingsvarsel.Utenlandsopphold.SkalAvkortes(
                    objekt = Avkortingsvarsel.Utenlandsopphold.Opprettet(
                        sakId = sak.id,
                        revurderingId = UUID.randomUUID(),
                        simulering = simuleringFeilutbetaling(
                            søknadsbehandling.periode.tilMånedsperioder().first(),
                        ),
                    ),
                ),
            )

            søknadsbehandling.leggTilUteståendeAvkorting(
                avkorting = uteståendeAvkorting,
                clock = fixedClock,
            ).let {
                println(it::class)
                it.avkorting shouldBe AvkortingVedSøknadsbehandling.Uhåndtert.KanIkkeHåndtere
            }
        }
    }

    @Test
    fun `kan legge til avkorting for typer hvor vilkår er oppfyllt`() {
        listOf(
            søknadsbehandlingVilkårsvurdertInnvilget(),
            søknadsbehandlingBeregnetAvslag(),
            søknadsbehandlingBeregnetInnvilget(),
            søknadsbehandlingSimulert(),
            søknadsbehandlingUnderkjentInnvilget(),
            søknadsbehandlingUnderkjentAvslagMedBeregning(),
        ).forEach { (sak, søknadsbehandling) ->
            val uteståendeAvkorting = AvkortingVedSøknadsbehandling.Uhåndtert.UteståendeAvkorting(
                avkortingsvarsel = Avkortingsvarsel.Utenlandsopphold.SkalAvkortes(
                    objekt = Avkortingsvarsel.Utenlandsopphold.Opprettet(
                        sakId = sak.id,
                        revurderingId = UUID.randomUUID(),
                        simulering = simuleringFeilutbetaling(
                            søknadsbehandling.periode.tilMånedsperioder().first(),
                        ),
                    ),
                ),
            )

            søknadsbehandling.leggTilUteståendeAvkorting(
                avkorting = uteståendeAvkorting,
                clock = fixedClock,
            ).let {
                println(it::class)
                it.avkorting shouldBe uteståendeAvkorting
            }
        }
    }

    @Test
    fun `kan ikke legge til avkorting for enkelte tilstander`() {
        listOf(
            søknadsbehandlingTilAttesteringInnvilget(),
            søknadsbehandlingTilAttesteringAvslagMedBeregning(),
            søknadsbehandlingTilAttesteringAvslagUtenBeregning(),
            søknadsbehandlingIverksattInnvilget(),
            søknadsbehandlingIverksattAvslagMedBeregning(),
            søknadsbehandlingIverksattAvslagUtenBeregning(),
        ).forEach { (sak, søknadsbehandling) ->
            val uteståendeAvkorting = AvkortingVedSøknadsbehandling.Uhåndtert.UteståendeAvkorting(
                avkortingsvarsel = Avkortingsvarsel.Utenlandsopphold.SkalAvkortes(
                    objekt = Avkortingsvarsel.Utenlandsopphold.Opprettet(
                        sakId = sak.id,
                        revurderingId = UUID.randomUUID(),
                        simulering = simuleringFeilutbetaling(
                            søknadsbehandling.periode.tilMånedsperioder().first(),
                        ),
                    ),
                ),
            )

            assertThrows<IllegalStateException> {
                søknadsbehandling.leggTilUteståendeAvkorting(
                    avkorting = uteståendeAvkorting,
                    clock = fixedClock,
                ).let {
                    println(it::class)
                    it.avkorting shouldBe uteståendeAvkorting
                }
            }
        }
    }
}
