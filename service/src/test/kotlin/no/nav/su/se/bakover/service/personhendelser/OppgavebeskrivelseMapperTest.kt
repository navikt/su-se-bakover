package no.nav.su.se.bakover.service.personhendelser

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.domain.hendelse.Personhendelse
import no.nav.su.se.bakover.domain.person.SivilstandTyper
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

internal class OppgavebeskrivelseMapperTest {
    private val fixedClock: Clock = Clock.fixed(1.januar(2021).startOfDay().instant, ZoneOffset.UTC)

    @Test
    fun `mapper dødsfallshendelser riktig`() {
        val personhendelse = Personhendelse.Hendelse.Dødsfall(LocalDate.now(fixedClock))

        OppgavebeskrivelseMapper.map(personhendelse) shouldBe "Dødsfall\n" +
            "\tDødsdato: 2020-12-31"
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
            "\tGyldig fra og med: 2020-12-31\n" +
            "\tBekreftelsesdato: Ikke oppgitt"
    }

    @Test
    fun `mapper utflytting fra norge riktig`() {
        val personhendelse = Personhendelse.Hendelse.UtflyttingFraNorge(LocalDate.now(fixedClock))

        OppgavebeskrivelseMapper.map(personhendelse) shouldBe "Utflytting fra Norge\n" +
            "\tUtflyttingsdato: 2020-12-31"
    }
}
