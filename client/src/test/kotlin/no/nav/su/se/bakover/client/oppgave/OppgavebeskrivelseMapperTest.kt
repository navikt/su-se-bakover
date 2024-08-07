package no.nav.su.se.bakover.client.oppgave

import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.personhendelse.Personhendelse
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.nyPersonhendelseKnyttetTilSak
import org.junit.jupiter.api.Test
import person.domain.SivilstandTyper
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * hvis du endrer på noen av expected, må du sjekke at indenten faktisk er tabs, or ikke spaces. Selv om du taster
 * inn tab, så kan det være at intellij setter inn 4 spaces. bruk piltastene for å verifisere
 */
internal class OppgavebeskrivelseMapperTest {

    @Test
    fun `mapper et set med personhendelser`() {
        val dødsfallsHendelse = nyPersonhendelseKnyttetTilSak(
            hendelse = Personhendelse.Hendelse.Dødsfall(fixedLocalDate),
            eksternOpprettet = fixedTidspunkt.plus(5, ChronoUnit.MINUTES),
        )
        val utflyttingsHendelse = nyPersonhendelseKnyttetTilSak(
            hendelse = Personhendelse.Hendelse.UtflyttingFraNorge(LocalDate.now(fixedClock)),
        )

        val alleHendelser = nonEmptyListOf(dødsfallsHendelse, utflyttingsHendelse)
        OppgavebeskrivelseMapper.map(alleHendelser) shouldBe """
            Dødsfall
            	Dødsdato: 2021-01-01
            	Hendelsestidspunkt: 01.01.2021 02:07
            	Endringstype: OPPRETTET
            	HendelseId: ${dødsfallsHendelse.id}
            	Tidligere hendelseid: Ingen tidligere
            
            Utflytting fra Norge
            	Utflyttingsdato: 2021-01-01
            	Hendelsestidspunkt: 01.01.2021 02:02
            	Endringstype: OPPRETTET
            	HendelseId: ${utflyttingsHendelse.id}
            	Tidligere hendelseid: Ingen tidligere
        """.trimIndent()
    }

    @Test
    fun `mapper dødsfallshendelser riktig`() {
        val nyHendelse = nyPersonhendelseKnyttetTilSak(
            hendelse = Personhendelse.Hendelse.Dødsfall(fixedLocalDate),
            eksternOpprettet = fixedTidspunkt.plus(5, ChronoUnit.MINUTES),
        )
        OppgavebeskrivelseMapper.mapOne(nyHendelse) shouldBe """
            Dødsfall
            	Dødsdato: 2021-01-01
            	Hendelsestidspunkt: 01.01.2021 02:07
            	Endringstype: OPPRETTET
            	HendelseId: ${nyHendelse.id}
            	Tidligere hendelseid: Ingen tidligere
        """.trimIndent()
    }

    @Test
    fun `mapper endringer i sivilstand riktig`() {
        val nyHendelse = nyPersonhendelseKnyttetTilSak(
            opprettet = fixedTidspunkt.plus(10, ChronoUnit.MINUTES),
            eksternOpprettet = null,
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
            	Hendelsestidspunkt: 01.01.2021 02:12
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
            	Hendelsestidspunkt: 01.01.2021 02:02
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
            	Hendelsestidspunkt: 01.01.2021 02:02
            	Endringstype: OPPRETTET
            	HendelseId: ${nyHendelse.id}
            	Tidligere hendelseid: Ingen tidligere
        """.trimIndent()
    }

    @Test
    fun `mapper kontaktadresse riktig`() {
        val fnr = Fnr.generer()
        val nyHendelse = nyPersonhendelseKnyttetTilSak(
            fnr = fnr,
            hendelse = Personhendelse.Hendelse.Kontaktadresse,
            gjelderEps = true,
        )
        OppgavebeskrivelseMapper.mapOne(nyHendelse) shouldBe """
            Endring i kontaktadresse
            	Gjelder EPS - aktørId, $fnr
            	Hendelsestidspunkt: 01.01.2021 02:02
            	Endringstype: OPPRETTET
            	HendelseId: ${nyHendelse.id}
            	Tidligere hendelseid: Ingen tidligere
        """.trimIndent()
    }
}
