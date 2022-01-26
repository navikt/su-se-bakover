package no.nav.su.se.bakover.domain.avkorting

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.periode.juni
import no.nav.su.se.bakover.test.simuleringFeilutbetaling
import org.junit.jupiter.api.Test
import java.util.UUID

internal class AvkortingVedSøknadsbehandlingTest {

    val id = UUID.randomUUID()

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
        val utestående = Avkortingsvarsel.Utenlandsopphold.SkalAvkortes(
            objekt = Avkortingsvarsel.Utenlandsopphold.Opprettet(
                sakId = UUID.randomUUID(),
                revurderingId = UUID.randomUUID(),
                simulering = simuleringFeilutbetaling(juni(2021)),
            ),
        )
        val original = AvkortingVedSøknadsbehandling.Uhåndtert.UteståendeAvkorting(
            utestående,
        )
        original.uhåndtert() shouldBe original
        original.håndter() shouldBe AvkortingVedSøknadsbehandling.Håndtert.AvkortUtestående(
            utestående,
        )
        original.kanIkke() shouldBe AvkortingVedSøknadsbehandling.Uhåndtert.KanIkkeHåndtere(
            original,
        )
    }

    @Test
    fun `flyt kan ikke håndtere uhåndtert utestående`() {
        val utestående = Avkortingsvarsel.Utenlandsopphold.SkalAvkortes(
            objekt = Avkortingsvarsel.Utenlandsopphold.Opprettet(
                sakId = UUID.randomUUID(),
                revurderingId = UUID.randomUUID(),
                simulering = simuleringFeilutbetaling(juni(2021)),
            ),
        )
        val original = AvkortingVedSøknadsbehandling.Uhåndtert.UteståendeAvkorting(
            utestående,
        ).kanIkke()
        original.uhåndtert() shouldBe AvkortingVedSøknadsbehandling.Uhåndtert.UteståendeAvkorting(
            utestående,
        )
        original.håndter() shouldBe AvkortingVedSøknadsbehandling.Håndtert.KanIkkeHåndtere(
            AvkortingVedSøknadsbehandling.Håndtert.AvkortUtestående(
                utestående,
            ),
        )
        original.kanIkke() shouldBe original
    }

    @Test
    fun `normalflyt håndtert utesteående`() {
        val utestående = Avkortingsvarsel.Utenlandsopphold.SkalAvkortes(
            objekt = Avkortingsvarsel.Utenlandsopphold.Opprettet(
                sakId = UUID.randomUUID(),
                revurderingId = UUID.randomUUID(),
                simulering = simuleringFeilutbetaling(juni(2021)),
            ),
        )
        val original = AvkortingVedSøknadsbehandling.Håndtert.AvkortUtestående(
            utestående,
        )
        original.uhåndtert() shouldBe AvkortingVedSøknadsbehandling.Uhåndtert.UteståendeAvkorting(
            utestående,
        )
        original.iverksett(id) shouldBe AvkortingVedSøknadsbehandling.Iverksatt.AvkortUtestående(
            utestående.avkortet(id),
        )
        original.kanIkke() shouldBe AvkortingVedSøknadsbehandling.Håndtert.KanIkkeHåndtere(
            original,
        )
    }

    @Test
    fun `flyt kan ikke håndtere håndtert utestående`() {
        val utestående = Avkortingsvarsel.Utenlandsopphold.SkalAvkortes(
            objekt = Avkortingsvarsel.Utenlandsopphold.Opprettet(
                sakId = UUID.randomUUID(),
                revurderingId = UUID.randomUUID(),
                simulering = simuleringFeilutbetaling(juni(2021)),
            ),
        )
        val original = AvkortingVedSøknadsbehandling.Håndtert.AvkortUtestående(
            utestående,
        ).kanIkke()
        original.uhåndtert() shouldBe AvkortingVedSøknadsbehandling.Uhåndtert.UteståendeAvkorting(
            utestående,
        )
        original.iverksett(UUID.randomUUID()) shouldBe AvkortingVedSøknadsbehandling.Iverksatt.KanIkkeHåndtere(
            AvkortingVedSøknadsbehandling.Håndtert.AvkortUtestående(
                utestående,
            ),
        )
        original.kanIkke() shouldBe original
    }

    @Test
    fun `potpurri`() {
        val utestående = Avkortingsvarsel.Utenlandsopphold.SkalAvkortes(
            objekt = Avkortingsvarsel.Utenlandsopphold.Opprettet(
                sakId = UUID.randomUUID(),
                revurderingId = UUID.randomUUID(),
                simulering = simuleringFeilutbetaling(juni(2021)),
            ),
        )
        val start = AvkortingVedSøknadsbehandling.Uhåndtert.UteståendeAvkorting(
            utestående,
        )

        start.kanIkke().håndter().uhåndtert() shouldBe start
        start.håndter().kanIkke().uhåndtert() shouldBe start
        start.kanIkke().håndter().iverksett(id) shouldBe AvkortingVedSøknadsbehandling.Iverksatt.KanIkkeHåndtere(
            AvkortingVedSøknadsbehandling.Håndtert.AvkortUtestående(
                utestående,
            ),
        )
        start.håndter().iverksett(id) shouldBe AvkortingVedSøknadsbehandling.Iverksatt.AvkortUtestående(
            utestående.avkortet(id),
        )
        start.kanIkke().håndter().uhåndtert().uhåndtert() shouldBe start
    }
}
