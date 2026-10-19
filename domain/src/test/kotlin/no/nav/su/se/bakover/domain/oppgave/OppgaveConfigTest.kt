package no.nav.su.se.bakover.domain.oppgave

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.oppgave.domain.Oppgavetype
import no.nav.su.se.bakover.test.fnr
import no.nav.su.se.bakover.test.saksnummer
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

internal class OppgaveConfigTest {

    @Test
    fun `bruker Oslo-dato for aktivDato i oppgaveconfig`() {
        val clock = Clock.fixed(Instant.parse("2026-06-27T22:30:00Z"), ZoneOffset.UTC)

        val actual = OppgaveConfig.Søknad(
            journalpostId = JournalpostId("123"),
            saksnummer = saksnummer,
            fnr = fnr,
            tilordnetRessurs = null,
            clock = clock,
            sakstype = Sakstype.UFØRE,
        )

        actual.aktivDato shouldBe LocalDate.parse("2026-06-28")
        actual.fristFerdigstillelse shouldBe LocalDate.parse("2026-07-28")
    }

    @Test
    fun `BrukerErDød har høy prioritet, kort frist og beskriver dødsdato og årsak`() {
        val clock = Clock.fixed(Instant.parse("2026-06-27T22:30:00Z"), ZoneOffset.UTC)

        val actual = OppgaveConfig.BrukerErDød(
            saksnummer = saksnummer,
            dødsdato = LocalDate.parse("2026-05-15"),
            årsak = "Oppdaget av testen.",
            fnr = fnr,
            clock = clock,
            sakstype = Sakstype.ALDER,
        )

        actual.oppgavetype shouldBe Oppgavetype.VURDER_KONSEKVENS_FOR_YTELSE
        actual.prioritet shouldBe OppgavePrioritet.HOY
        actual.saksreferanse shouldBe saksnummer.toString()
        actual.aktivDato shouldBe LocalDate.parse("2026-06-28")
        actual.fristFerdigstillelse shouldBe LocalDate.parse("2026-07-05")
        actual.beskrivelse shouldContain "Bruker er registrert død med dødsdato 2026-05-15."
        actual.beskrivelse shouldContain "Oppdaget av testen."
    }
}
