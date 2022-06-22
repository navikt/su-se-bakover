package no.nav.su.se.bakover.domain.søknadsbehandling

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.test.søknadsbehandlingLukket
import no.nav.su.se.bakover.test.søknadsbehandlingTilAttesteringInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertUavklart
import org.junit.jupiter.api.Test

internal class LukketSøknadsbehandlingTest {

    @Test
    fun `skal ikke kunne lukke en lukket søknadsbehandling`() {
        LukketSøknadsbehandling.tryCreate(søknadsbehandlingLukket().second) shouldBe KunneIkkeLukkeSøknadsbehandling.KanIkkeLukkeEnAlleredeLukketSøknadsbehandling.left()
    }

    @Test
    fun `skal ikke kunne lukke en søknadsbehandling til attestering`() {
        LukketSøknadsbehandling.tryCreate(søknadsbehandlingTilAttesteringInnvilget().second) shouldBe KunneIkkeLukkeSøknadsbehandling.KanIkkeLukkeEnSøknadsbehandlingTilAttestering.left()
    }

    @Test
    fun `skal ikke kunne lukke en iverksatt søknadsbehandling`() {
        LukketSøknadsbehandling.tryCreate(søknadsbehandlingTilAttesteringInnvilget().second) shouldBe KunneIkkeLukkeSøknadsbehandling.KanIkkeLukkeEnSøknadsbehandlingTilAttestering.left()
    }

    @Test
    fun `skal kunne lukke en opprettet søknadsbehandling uten stønadsperiode`() {
        søknadsbehandlingVilkårsvurdertUavklart().second.copy(stønadsperiode = null).let {
            LukketSøknadsbehandling.tryCreate(it) shouldBe LukketSøknadsbehandling.create(
                lukketSøknadsbehandling = it
            ).right()
        }
    }
}
