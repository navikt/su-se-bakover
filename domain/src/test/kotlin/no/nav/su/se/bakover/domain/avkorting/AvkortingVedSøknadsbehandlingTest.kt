package no.nav.su.se.bakover.domain.avkorting

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.juni
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.simuleringFeilutbetaling
import org.junit.jupiter.api.Test
import java.util.UUID

internal class AvkortingVedSøknadsbehandlingTest {

    private val id = UUID.randomUUID()
    private val avkortingsvarsel = Avkortingsvarsel.Utenlandsopphold.SkalAvkortes(
        objekt = Avkortingsvarsel.Utenlandsopphold.Opprettet(
            sakId = UUID.randomUUID(),
            revurderingId = UUID.randomUUID(),
            simulering = simuleringFeilutbetaling(juni(2021)),
            opprettet = Tidspunkt.now(fixedClock),
        ),
    )

    @Test
    fun `normalflyt uhåndtert ingen utestående`() {
        val original = AvkortingVedSøknadsbehandling.Uhåndtert.IngenUtestående
        original.uhåndtert() shouldBe original
        original.håndter() shouldBe AvkortingVedSøknadsbehandling.Håndtert.IngenUtestående
        original.kanIkke() shouldBe AvkortingVedSøknadsbehandling.Uhåndtert.KanIkkeHåndtere(original)
    }

    @Test
    fun `flyt kan ikke håndtere uhåndtert ingen utestående`() {
        val original = AvkortingVedSøknadsbehandling.Uhåndtert.IngenUtestående.kanIkke()
        original.uhåndtert() shouldBe AvkortingVedSøknadsbehandling.Uhåndtert.IngenUtestående
        original.kanIkke() shouldBe original
        original.håndter() shouldBe AvkortingVedSøknadsbehandling.Håndtert.KanIkkeHåndtere(
            AvkortingVedSøknadsbehandling.Håndtert.IngenUtestående,
        )
    }

    @Test
    fun `normalflyt håndtert ingen utestående`() {
        val original = AvkortingVedSøknadsbehandling.Håndtert.IngenUtestående
        original.uhåndtert() shouldBe AvkortingVedSøknadsbehandling.Uhåndtert.IngenUtestående
        original.iverksett(UUID.randomUUID()) shouldBe AvkortingVedSøknadsbehandling.Iverksatt.IngenUtestående
        original.kanIkke() shouldBe AvkortingVedSøknadsbehandling.Håndtert.KanIkkeHåndtere(original)
    }

    @Test
    fun `flyt kan ikke håndtere håndtert ingen utestående`() {
        val original = AvkortingVedSøknadsbehandling.Håndtert.IngenUtestående.kanIkke()
        original.uhåndtert() shouldBe AvkortingVedSøknadsbehandling.Uhåndtert.IngenUtestående
        original.kanIkke() shouldBe original
        original.iverksett(UUID.randomUUID()) shouldBe AvkortingVedSøknadsbehandling.Iverksatt.KanIkkeHåndtere(
            AvkortingVedSøknadsbehandling.Håndtert.IngenUtestående,
        )
    }

    @Test
    fun `normalflyt uhåndtert utesteående`() {
        val original = AvkortingVedSøknadsbehandling.Uhåndtert.UteståendeAvkorting(
            avkortingsvarsel,
        )
        original.uhåndtert() shouldBe original
        original.håndter() shouldBe AvkortingVedSøknadsbehandling.Håndtert.AvkortUtestående(
            avkortingsvarsel,
        )
        original.kanIkke() shouldBe AvkortingVedSøknadsbehandling.Uhåndtert.KanIkkeHåndtere(
            original,
        )
    }

    @Test
    fun `flyt kan ikke håndtere uhåndtert utestående`() {
        val original = AvkortingVedSøknadsbehandling.Uhåndtert.UteståendeAvkorting(
            avkortingsvarsel,
        ).kanIkke()
        original.uhåndtert() shouldBe AvkortingVedSøknadsbehandling.Uhåndtert.UteståendeAvkorting(
            avkortingsvarsel,
        )
        original.håndter() shouldBe AvkortingVedSøknadsbehandling.Håndtert.KanIkkeHåndtere(
            AvkortingVedSøknadsbehandling.Håndtert.AvkortUtestående(
                avkortingsvarsel,
            ),
        )
        original.kanIkke() shouldBe original
    }

    @Test
    fun `normalflyt håndtert utesteående`() {
        val original = AvkortingVedSøknadsbehandling.Håndtert.AvkortUtestående(
            avkortingsvarsel,
        )
        original.uhåndtert() shouldBe AvkortingVedSøknadsbehandling.Uhåndtert.UteståendeAvkorting(
            avkortingsvarsel,
        )
        original.iverksett(id) shouldBe AvkortingVedSøknadsbehandling.Iverksatt.AvkortUtestående(
            avkortingsvarsel.avkortet(id),
        )
        original.kanIkke() shouldBe AvkortingVedSøknadsbehandling.Håndtert.KanIkkeHåndtere(
            original,
        )
    }

    @Test
    fun `flyt kan ikke håndtere håndtert utestående`() {
        val original = AvkortingVedSøknadsbehandling.Håndtert.AvkortUtestående(
            avkortingsvarsel,
        ).kanIkke()
        original.uhåndtert() shouldBe AvkortingVedSøknadsbehandling.Uhåndtert.UteståendeAvkorting(
            avkortingsvarsel,
        )
        original.iverksett(UUID.randomUUID()) shouldBe AvkortingVedSøknadsbehandling.Iverksatt.KanIkkeHåndtere(
            AvkortingVedSøknadsbehandling.Håndtert.AvkortUtestående(
                avkortingsvarsel,
            ),
        )
        original.kanIkke() shouldBe original
    }

    @Test
    fun `potpurri`() {
        val start = AvkortingVedSøknadsbehandling.Uhåndtert.UteståendeAvkorting(
            avkortingsvarsel,
        )

        start.kanIkke().håndter().uhåndtert() shouldBe start
        start.håndter().kanIkke().uhåndtert() shouldBe start
        start.kanIkke().håndter().iverksett(id) shouldBe AvkortingVedSøknadsbehandling.Iverksatt.KanIkkeHåndtere(
            AvkortingVedSøknadsbehandling.Håndtert.AvkortUtestående(
                avkortingsvarsel,
            ),
        )
        start.håndter().iverksett(id) shouldBe AvkortingVedSøknadsbehandling.Iverksatt.AvkortUtestående(
            avkortingsvarsel.avkortet(id),
        )
        start.kanIkke().håndter().uhåndtert().uhåndtert() shouldBe start
    }
}
