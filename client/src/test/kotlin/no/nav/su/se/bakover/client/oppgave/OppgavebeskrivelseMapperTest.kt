package no.nav.su.se.bakover.client.oppgave

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.personhendelse.Personhendelse
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedLocalDate
import org.junit.jupiter.api.Test
import person.domain.SivilstandTyper
import java.time.LocalDate

internal class OppgavebeskrivelseMapperTest {
    @Test
    fun `mapper dødsfallshendelser riktig`() {
        val personhendelse = Personhendelse.Hendelse.Dødsfall(fixedLocalDate)

        OppgavebeskrivelseMapper.map(personhendelse) shouldBe "Dødsfall\n" +
            "\tDødsdato: 2021-01-01"
    }

    @Test
    fun `mapper endringer i sivilstand riktig`() {
        val personhendelse = Personhendelse.Hendelse.Sivilstand(
            type = SivilstandTyper.GIFT,
            gyldigFraOgMed = LocalDate.now(fixedClock),
            relatertVedSivilstand = null,
            bekreftelsesdato = null,
        )

        OppgavebeskrivelseMapper.map(personhendelse) shouldBe "Endring i sivilstand\n" +
            "\ttype: Gift\n" +
            "\tGyldig fra og med: 2021-01-01\n" +
            "\tBekreftelsesdato: Ikke oppgitt"
    }

    @Test
    fun `mapper utflytting fra norge riktig`() {
        val personhendelse = Personhendelse.Hendelse.UtflyttingFraNorge(LocalDate.now(fixedClock))

        OppgavebeskrivelseMapper.map(personhendelse) shouldBe "Utflytting fra Norge\n" +
            "\tUtflyttingsdato: 2021-01-01"
    }

    @Test
    fun `mapper bostedsadresse riktig`() {
        val personhendelse = Personhendelse.Hendelse.Bostedsadresse

        OppgavebeskrivelseMapper.map(personhendelse) shouldBe "Endring i bostedsadresse"
    }

    @Test
    fun `mapper kontaktadresse riktig`() {
        val personhendelse = Personhendelse.Hendelse.Kontaktadresse

        OppgavebeskrivelseMapper.map(personhendelse) shouldBe "Endring i kontaktadresse"
    }
}
