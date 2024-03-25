package no.nav.su.se.bakover.client.oppgave

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.personhendelse.Personhendelse
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.nyPersonhendelseKnyttetTilSak
import org.junit.jupiter.api.Test
import person.domain.SivilstandTyper
import java.time.LocalDate

/**
 * hvis du endrer på noen av expected, må du sjekke at indenten faktisk er tabs, or ikke spaces. Selv om du taster
 * inn tab, så kan det være at intellij setter inn 4 spaces. bruk piltastene for å verifisere
 */
internal class OppgavebeskrivelseMapperTest {
    @Test
    fun `mapper dødsfallshendelser riktig`() {
        val nyHendelse = nyPersonhendelseKnyttetTilSak(
            hendelse = Personhendelse.Hendelse.Dødsfall(fixedLocalDate),
        )
        OppgavebeskrivelseMapper.mapOne(nyHendelse) shouldBe """
            Dødsfall
            	Dødsdato: 2021-01-01
            	Mottatt hendelsestidspunkt: 01.01.2021 02:02
            	Endringstype: OPPRETTET
            	HendelseId: ${nyHendelse.id}
            	Tidligere hendelseid: Ingen tidligere
        """.trimIndent()
    }

    @Test
    fun `mapper endringer i sivilstand riktig`() {
        val nyHendelse = nyPersonhendelseKnyttetTilSak(
            hendelse = Personhendelse.Hendelse.Sivilstand(
                type = SivilstandTyper.GIFT,
                gyldigFraOgMed = LocalDate.now(fixedClock),
                relatertVedSivilstand = null,
                bekreftelsesdato = null,
            ),
        )

        OppgavebeskrivelseMapper.mapOne(nyHendelse) shouldBe """
            Endring i sivilstand
            	Type: Gift
            	Gyldig fra og med: 2021-01-01
            	Bekreftelsesdato: Ikke oppgitt
            	Mottatt hendelsestidspunkt: 01.01.2021 02:02
            	Endringstype: OPPRETTET
            	HendelseId: ${nyHendelse.id}
            	Tidligere hendelseid: Ingen tidligere
        """.trimIndent()
    }

    @Test
    fun `mapper utflytting fra norge riktig`() {
        val nyHendelse = nyPersonhendelseKnyttetTilSak(
            hendelse = Personhendelse.Hendelse.UtflyttingFraNorge(LocalDate.now(fixedClock)),
        )
        OppgavebeskrivelseMapper.mapOne(nyHendelse).trimIndent() shouldBe """
            Utflytting fra Norge
            	Utflyttingsdato: 2021-01-01
            	Mottatt hendelsestidspunkt: 01.01.2021 02:02
            	Endringstype: OPPRETTET
            	HendelseId: ${nyHendelse.id}
            	Tidligere hendelseid: Ingen tidligere
        """.trimIndent()
    }

    @Test
    fun `mapper bostedsadresse riktig`() {
        val nyHendelse = nyPersonhendelseKnyttetTilSak(
            hendelse = Personhendelse.Hendelse.Bostedsadresse,
        )
        OppgavebeskrivelseMapper.mapOne(nyHendelse) shouldBe """
            Endring i bostedsadresse
            	Mottatt hendelsestidspunkt: 01.01.2021 02:02
            	Endringstype: OPPRETTET
            	HendelseId: ${nyHendelse.id}
            	Tidligere hendelseid: Ingen tidligere
        """.trimIndent()
    }

    @Test
    fun `mapper kontaktadresse riktig`() {
        val nyHendelse = nyPersonhendelseKnyttetTilSak(
            hendelse = Personhendelse.Hendelse.Kontaktadresse,
        )
        OppgavebeskrivelseMapper.mapOne(nyHendelse) shouldBe """
            Endring i kontaktadresse
            	Mottatt hendelsestidspunkt: 01.01.2021 02:02
            	Endringstype: OPPRETTET
            	HendelseId: ${nyHendelse.id}
            	Tidligere hendelseid: Ingen tidligere
        """.trimIndent()
    }
}
