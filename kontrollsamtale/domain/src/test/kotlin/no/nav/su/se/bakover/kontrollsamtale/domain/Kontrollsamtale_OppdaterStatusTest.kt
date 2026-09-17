package no.nav.su.se.bakover.kontrollsamtale.domain

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.kontrollsamtale.innkaltKontrollsamtale
import no.nav.su.se.bakover.test.kontrollsamtale.planlagtKontrollsamtale
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class Kontrollsamtale_OppdaterStatusTest {

    @Test
    fun `Kan ikke sette planlagt kontrollsamtale til gjennomført før en måned før innkallingsdato`() {
        val clock = TikkendeKlokke()
        val kontrollsamtale = planlagtKontrollsamtale(
            innkallingsdato = LocalDate.now(clock).plusMonths(1).plusDays(1),
        )
        val resultat = kontrollsamtale.settGjennomført(
            journalpostId = JournalpostId("123456789"),
            clock = clock,
        )
        resultat.isLeft() shouldBe true
    }

    @Test
    fun `kan sette planlagt kontrollsamtale til gjennomført etter en måned før innkallingsdato`() {
        val clock = TikkendeKlokke()
        val kontrollsamtale = planlagtKontrollsamtale(
            innkallingsdato = LocalDate.now(clock).plusMonths(1),
        )
        val resultat = kontrollsamtale.settGjennomført(
            journalpostId = JournalpostId("123456789"),
            clock = clock,
        )
        resultat.isRight() shouldBe true
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
