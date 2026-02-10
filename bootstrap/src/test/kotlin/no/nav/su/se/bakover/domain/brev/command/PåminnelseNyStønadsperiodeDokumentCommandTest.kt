package no.nav.su.se.bakover.domain.brev.command

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.tid.juli
import no.nav.su.se.bakover.common.domain.tid.juni
import no.nav.su.se.bakover.common.person.AktørId
import no.nav.su.se.bakover.common.person.Ident
import no.nav.su.se.bakover.test.fnr
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.nySakAlder
import no.nav.su.se.bakover.test.nySakUføre
import org.junit.jupiter.api.Test
import person.domain.Person

class PåminnelseNyStønadsperiodeDokumentCommandTest {
    @Test
    fun `Skal sette flagg uføre fyller 67 hvis bruker fyller 67 samme måned som jobbkjøring`() {
        with(
            PåminnelseNyStønadsperiodeDokumentCommand.ny(
                sak = nySakUføre().first,
                person = Person(
                    ident = Ident(fnr, AktørId("123")),
                    navn = Person.Navn("", "", ""),
                    fødsel = Person.Fødsel.MedFødselsdato(15.juni(1958)),
                    kontaktinfo = { null },
                ),
                utløpsdato = 1.juni(2025),
            ).getOrFail(),
        ) {
            uføreSomFyller67 shouldBe true
        }
    }

    @Test
    fun `Skal ikke sette flagg uføre fyller 67 hvis bruker er 67+ og har alderssakstype`() {
        with(
            PåminnelseNyStønadsperiodeDokumentCommand.ny(
                sak = nySakAlder().first,
                person = Person(
                    ident = Ident(fnr, AktørId("123")),
                    navn = Person.Navn("", "", ""),
                    fødsel = Person.Fødsel.MedFødselsdato(15.juli(1958)),
                    kontaktinfo = { null },
                ),
                utløpsdato = 1.juni(2025),
            ).getOrFail(),
        ) {
            uføreSomFyller67 shouldBe false
        }
    }
}
