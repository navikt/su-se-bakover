package no.nav.su.se.bakover.domain.oppgave

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.journal.JournalpostId
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
}
