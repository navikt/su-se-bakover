package no.nav.su.se.bakover.domain.avkorting

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.juni
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.simulering.simuleringFeilutbetaling
import org.junit.jupiter.api.Test
import java.util.UUID

internal class AvkortingVedRevurderingTest {
    private val id: UUID = UUID.randomUUID()
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
        original.håndter(avkortingsvarsel) shouldBe AvkortingVedRevurdering.Håndtert.OpprettNyttAvkortingsvarsel(
            avkortingsvarsel,
        )
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
        val original = AvkortingVedRevurdering.Uhåndtert.UteståendeAvkorting(
            avkortingsvarsel,
        )
        original.uhåndtert() shouldBe original
        original.håndter() shouldBe AvkortingVedRevurdering.DelvisHåndtert.AnnullerUtestående(
            avkortingsvarsel,
        )
        original.kanIkke() shouldBe AvkortingVedRevurdering.Uhåndtert.KanIkkeHåndtere(
            original,
        )
    }

    @Test
    fun `flyt kan ikke håndtere uhåndtert utestående`() {
        val original = AvkortingVedRevurdering.Uhåndtert.UteståendeAvkorting(
            avkortingsvarsel,
        ).kanIkke()
        original.uhåndtert() shouldBe AvkortingVedRevurdering.Uhåndtert.UteståendeAvkorting(
            avkortingsvarsel,
        )
        original.håndter() shouldBe AvkortingVedRevurdering.DelvisHåndtert.KanIkkeHåndtere(
            AvkortingVedRevurdering.DelvisHåndtert.AnnullerUtestående(
                avkortingsvarsel,
            ),
        )
        original.kanIkke() shouldBe original
    }

    @Test
    fun `normalflyt delvis håndtert utesteående`() {
        val original = AvkortingVedRevurdering.DelvisHåndtert.AnnullerUtestående(
            avkortingsvarsel,
        )
        original.uhåndtert() shouldBe AvkortingVedRevurdering.Uhåndtert.UteståendeAvkorting(
            avkortingsvarsel,
        )
        original.håndter() shouldBe AvkortingVedRevurdering.Håndtert.AnnullerUtestående(
            avkortingsvarsel,
        )
        original.håndter(avkortingsvarsel) shouldBe AvkortingVedRevurdering.Håndtert.OpprettNyttAvkortingsvarselOgAnnullerUtestående(
            avkortingsvarsel,
            avkortingsvarsel,
        )
        original.kanIkke() shouldBe AvkortingVedRevurdering.DelvisHåndtert.KanIkkeHåndtere(
            original,
        )
    }

    @Test
    fun `flyt kan ikke håndtere delvis håndtert utestående`() {
        val original = AvkortingVedRevurdering.DelvisHåndtert.AnnullerUtestående(
            avkortingsvarsel,
        ).kanIkke()
        original.uhåndtert() shouldBe AvkortingVedRevurdering.Uhåndtert.UteståendeAvkorting(
            avkortingsvarsel,
        )
        original.håndter() shouldBe AvkortingVedRevurdering.Håndtert.KanIkkeHåndteres(
            AvkortingVedRevurdering.Håndtert.AnnullerUtestående(
                avkortingsvarsel,
            ),
        )
        original.kanIkke() shouldBe original
    }

    @Test
    fun `normalflyt håndtert utesteående`() {
        val original = AvkortingVedRevurdering.Håndtert.AnnullerUtestående(
            avkortingsvarsel,
        )
        original.uhåndtert() shouldBe AvkortingVedRevurdering.Uhåndtert.UteståendeAvkorting(
            avkortingsvarsel,
        )
        original.iverksett(id) shouldBe AvkortingVedRevurdering.Iverksatt.AnnullerUtestående(
            avkortingsvarsel.annuller(id),
        )
        original.kanIkke() shouldBe AvkortingVedRevurdering.Håndtert.KanIkkeHåndteres(
            original,
        )
        val nyttVarsel = AvkortingVedRevurdering.Håndtert.OpprettNyttAvkortingsvarsel(
            avkortingsvarsel,
        )
        nyttVarsel.uhåndtert() shouldBe AvkortingVedRevurdering.Uhåndtert.IngenUtestående
        nyttVarsel.iverksett(id) shouldBe AvkortingVedRevurdering.Iverksatt.OpprettNyttAvkortingsvarsel(
            avkortingsvarsel,
        )
        nyttVarsel.kanIkke() shouldBe AvkortingVedRevurdering.Håndtert.KanIkkeHåndteres(
            nyttVarsel,
        )

        val nyttOgEksisterende = AvkortingVedRevurdering.Håndtert.OpprettNyttAvkortingsvarselOgAnnullerUtestående(
            avkortingsvarsel,
            avkortingsvarsel,
        )
        nyttOgEksisterende.uhåndtert() shouldBe AvkortingVedRevurdering.Uhåndtert.UteståendeAvkorting(
            avkortingsvarsel,
        )
        nyttOgEksisterende.iverksett(id) shouldBe AvkortingVedRevurdering.Iverksatt.OpprettNyttAvkortingsvarselOgAnnullerUtestående(
            avkortingsvarsel,
            avkortingsvarsel.annuller(id),
        )
        nyttOgEksisterende.kanIkke() shouldBe AvkortingVedRevurdering.Håndtert.KanIkkeHåndteres(
            nyttOgEksisterende,
        )
    }

    @Test
    fun `flyt kan ikke håndtere håndtert utestående`() {
        val original = AvkortingVedRevurdering.Håndtert.AnnullerUtestående(
            avkortingsvarsel,
        ).kanIkke()
        original.uhåndtert() shouldBe AvkortingVedRevurdering.Uhåndtert.UteståendeAvkorting(
            avkortingsvarsel,
        )
        original.iverksett(id) shouldBe AvkortingVedRevurdering.Iverksatt.KanIkkeHåndteres(
            AvkortingVedRevurdering.Håndtert.AnnullerUtestående(
                avkortingsvarsel,
            ),
        )
        original.kanIkke() shouldBe original
    }

    @Test
    fun `potpurri`() {
        val start = AvkortingVedRevurdering.Uhåndtert.UteståendeAvkorting(
            avkortingsvarsel,
        )

        start.kanIkke().håndter().uhåndtert() shouldBe start
        start.håndter().kanIkke().uhåndtert() shouldBe start
        start.kanIkke().håndter().håndter().iverksett(id) shouldBe AvkortingVedRevurdering.Iverksatt.KanIkkeHåndteres(
            AvkortingVedRevurdering.Håndtert.AnnullerUtestående(
                avkortingsvarsel,
            ),
        )
        start.kanIkke().håndter().uhåndtert().uhåndtert() shouldBe start
    }
}
