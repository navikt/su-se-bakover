package no.nav.su.se.bakover.kontrollsamtale.domain

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.fixedClockAt
import no.nav.su.se.bakover.test.kontrollsamtale.innkaltKontrollsamtale
import no.nav.su.se.bakover.test.kontrollsamtale.planlagtKontrollsamtale
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class Kontrollsamtale_OppdaterStatusTest {

    @Test
    fun `Kan ikke sette planlagt kontrollsamtale til gjennomført før måneden før fristen`() {
        val clock = fixedClockAt(LocalDate.of(2026, 3, 31))
        val kontrollsamtale = planlagtKontrollsamtale(
            innkallingsdato = LocalDate.of(2026, 5, 1),
            frist = LocalDate.of(2026, 5, 31),
        )
        val resultat = kontrollsamtale.settGjennomført(
            journalpostId = JournalpostId("123456789"),
            clock = clock,
        )
        resultat.isLeft() shouldBe true
    }

    @Test
    fun `kan sette planlagt kontrollsamtale til gjennomført fra første dag i måneden før fristen`() {
        val clock = fixedClockAt(LocalDate.of(2026, 4, 1))
        val kontrollsamtale = planlagtKontrollsamtale(
            innkallingsdato = LocalDate.of(2026, 5, 1),
            frist = LocalDate.of(2026, 5, 31),
        )
        val resultat = kontrollsamtale.settGjennomført(
            journalpostId = JournalpostId("123456789"),
            clock = clock,
        )
        resultat.isRight() shouldBe true
    }

    @Test
    fun `Kan ikke sette planlagt kontrollsamtale til gjennomført etter fristen`() {
        val clock = fixedClockAt(LocalDate.of(2026, 6, 1))
        val kontrollsamtale = planlagtKontrollsamtale(
            innkallingsdato = LocalDate.of(2026, 5, 1),
            frist = LocalDate.of(2026, 5, 31),
        )
        val resultat = kontrollsamtale.settGjennomført(
            journalpostId = JournalpostId("123456789"),
            clock = clock,
        )
        resultat.isLeft() shouldBe true
    }

    @Test
    fun `kan sette innkalt kontrollsamtale til gjennomført`() {
        val clock = TikkendeKlokke()
        val kontrollsamtale = innkaltKontrollsamtale()

        val resultat = kontrollsamtale.settGjennomført(
            journalpostId = JournalpostId("123456789"),
            clock = clock,
        )
        resultat.isRight() shouldBe true
    }
}
