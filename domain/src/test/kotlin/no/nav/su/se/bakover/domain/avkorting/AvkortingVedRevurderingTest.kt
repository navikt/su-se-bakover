package no.nav.su.se.bakover.domain.avkorting

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.periode.juni
import no.nav.su.se.bakover.test.simuleringFeilutbetaling
import org.junit.jupiter.api.Test
import java.util.UUID

internal class AvkortingVedRevurderingTest {
    val id = UUID.randomUUID()

    @Test
    fun `normalflyt uhåndtert ingen utestående`() {
        val original = AvkortingVedRevurdering.Uhåndtert.IngenUtestående
        original.uhåndtert() shouldBe original
        original.håndter() shouldBe AvkortingVedRevurdering.DelvisHåndtert.IngenUtestående
        original.kanIkke() shouldBe AvkortingVedRevurdering.Uhåndtert.KanIkkeHåndtere(original)
    }

    @Test
    fun `flyt kan ikke håndtere uhåndtert ingen utestående`() {
        val original = AvkortingVedRevurdering.Uhåndtert.IngenUtestående.kanIkke()
        original.uhåndtert() shouldBe AvkortingVedRevurdering.Uhåndtert.IngenUtestående
        original.kanIkke() shouldBe original
        original.håndter() shouldBe AvkortingVedRevurdering.DelvisHåndtert.KanIkkeHåndtere(
            AvkortingVedRevurdering.DelvisHåndtert.IngenUtestående,
        )
    }

    @Test
    fun `normalflyt delvis håndtert ingen utestående`() {
        val original = AvkortingVedRevurdering.DelvisHåndtert.IngenUtestående
        original.uhåndtert() shouldBe AvkortingVedRevurdering.Uhåndtert.IngenUtestående
        original.håndter() shouldBe AvkortingVedRevurdering.Håndtert.IngenNyEllerUtestående
        original.kanIkke() shouldBe AvkortingVedRevurdering.DelvisHåndtert.KanIkkeHåndtere(
            original,
        )
    }

    @Test
    fun `flyt kan ikke håndtere delvis håndtert ingen utestående`() {
        val original = AvkortingVedRevurdering.DelvisHåndtert.IngenUtestående.kanIkke()
        original.uhåndtert() shouldBe AvkortingVedRevurdering.Uhåndtert.IngenUtestående
        original.håndter() shouldBe AvkortingVedRevurdering.Håndtert.KanIkkeHåndteres(
            AvkortingVedRevurdering.Håndtert.IngenNyEllerUtestående,
        )
        original.kanIkke() shouldBe original
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
        val original = AvkortingVedRevurdering.Uhåndtert.UteståendeAvkorting(
            utestående,
        )
        original.uhåndtert() shouldBe original
        original.håndter() shouldBe AvkortingVedRevurdering.DelvisHåndtert.AnnullerUtestående(
            utestående,
        )
        original.kanIkke() shouldBe AvkortingVedRevurdering.Uhåndtert.KanIkkeHåndtere(
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
        val original = AvkortingVedRevurdering.Uhåndtert.UteståendeAvkorting(
            utestående,
        ).kanIkke()
        original.uhåndtert() shouldBe AvkortingVedRevurdering.Uhåndtert.UteståendeAvkorting(
            utestående,
        )
        original.håndter() shouldBe AvkortingVedRevurdering.DelvisHåndtert.KanIkkeHåndtere(
            AvkortingVedRevurdering.DelvisHåndtert.AnnullerUtestående(
                utestående,
            ),
        )
        original.kanIkke() shouldBe original
    }

    @Test
    fun `normalflyt delvis håndtert utesteående`() {
        val utestående = Avkortingsvarsel.Utenlandsopphold.SkalAvkortes(
            objekt = Avkortingsvarsel.Utenlandsopphold.Opprettet(
                sakId = UUID.randomUUID(),
                revurderingId = UUID.randomUUID(),
                simulering = simuleringFeilutbetaling(juni(2021)),
            ),
        )
        val original = AvkortingVedRevurdering.DelvisHåndtert.AnnullerUtestående(
            utestående,
        )
        original.uhåndtert() shouldBe AvkortingVedRevurdering.Uhåndtert.UteståendeAvkorting(
            utestående,
        )
        original.håndter() shouldBe AvkortingVedRevurdering.Håndtert.AnnullerUtestående(
            utestående,
        )
        original.kanIkke() shouldBe AvkortingVedRevurdering.DelvisHåndtert.KanIkkeHåndtere(
            original,
        )
    }

    @Test
    fun `flyt kan ikke håndtere delvis håndtert utestående`() {
        val utestående = Avkortingsvarsel.Utenlandsopphold.SkalAvkortes(
            objekt = Avkortingsvarsel.Utenlandsopphold.Opprettet(
                sakId = UUID.randomUUID(),
                revurderingId = UUID.randomUUID(),
                simulering = simuleringFeilutbetaling(juni(2021)),
            ),
        )
        val original = AvkortingVedRevurdering.DelvisHåndtert.AnnullerUtestående(
            utestående,
        ).kanIkke()
        original.uhåndtert() shouldBe AvkortingVedRevurdering.Uhåndtert.UteståendeAvkorting(
            utestående,
        )
        original.håndter() shouldBe AvkortingVedRevurdering.Håndtert.KanIkkeHåndteres(
            AvkortingVedRevurdering.Håndtert.AnnullerUtestående(
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
        val original = AvkortingVedRevurdering.Håndtert.AnnullerUtestående(
            utestående,
        )
        original.uhåndtert() shouldBe AvkortingVedRevurdering.Uhåndtert.UteståendeAvkorting(
            utestående,
        )
        original.iverksett(id) shouldBe AvkortingVedRevurdering.Iverksatt.AnnullerUtestående(
            utestående.annuller(id),
        )
        original.kanIkke() shouldBe AvkortingVedRevurdering.Håndtert.KanIkkeHåndteres(
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
        val original = AvkortingVedRevurdering.Håndtert.AnnullerUtestående(
            utestående,
        ).kanIkke()
        original.uhåndtert() shouldBe AvkortingVedRevurdering.Uhåndtert.UteståendeAvkorting(
            utestående,
        )

        original.iverksett(id) shouldBe AvkortingVedRevurdering.Iverksatt.KanIkkeHåndteres(
            AvkortingVedRevurdering.Håndtert.AnnullerUtestående(
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
        val start = AvkortingVedRevurdering.Uhåndtert.UteståendeAvkorting(
            utestående,
        )

        start.kanIkke().håndter().uhåndtert() shouldBe start
        start.håndter().kanIkke().uhåndtert() shouldBe start
        start.kanIkke().håndter().håndter().iverksett(id) shouldBe AvkortingVedRevurdering.Iverksatt.KanIkkeHåndteres(
            AvkortingVedRevurdering.Håndtert.AnnullerUtestående(
                utestående,
            ),
        )
        start.kanIkke().håndter().uhåndtert().uhåndtert() shouldBe start
    }
}
