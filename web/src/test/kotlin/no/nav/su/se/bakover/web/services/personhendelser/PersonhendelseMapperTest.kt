package no.nav.su.se.bakover.web.services.personhendelser

import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.person.pdl.leesah.Endringstype
import no.nav.person.pdl.leesah.bostedsadresse.Bostedsadresse
import no.nav.person.pdl.leesah.common.adresse.Koordinater
import no.nav.person.pdl.leesah.common.adresse.Vegadresse
import no.nav.person.pdl.leesah.doedsfall.Doedsfall
import no.nav.person.pdl.leesah.kontaktadresse.Kontaktadresse
import no.nav.person.pdl.leesah.sivilstand.Sivilstand
import no.nav.person.pdl.leesah.utflytting.UtflyttingFraNorge
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.person.SivilstandTyper
import no.nav.su.se.bakover.domain.personhendelse.Personhendelse
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.generer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.Test
import java.time.Instant
import no.nav.person.pdl.leesah.Personhendelse as EksternPersonhendelse

internal class PersonhendelseMapperTest {
    private val TOPIC = "topic"
    private val PARTITION = 0
    private val OFFSET = 0L

    private val aktørId = "1234567890000"
    private val fnr = Fnr.generer().toString()
    private val opprettet = Instant.now(fixedClock)

    @Test
    fun `mapper fra ekstern dødsfalltype til intern`() {
        val personhendelse = EksternPersonhendelse(
            "hendelseId",
            listOf(fnr, aktørId),
            "FREG",
            opprettet,
            "DOEDSFALL_V1",
            Endringstype.OPPRETTET,
            null,
            Doedsfall(fixedLocalDate),
            null,
            null,
            null,
            null,
        )
        val message = ConsumerRecord(TOPIC, PARTITION, OFFSET, aktørId, personhendelse)
        val actual = PersonhendelseMapper.map(message).getOrElse { throw RuntimeException("Feil skjedde i test") }

        actual shouldBe Personhendelse.IkkeTilknyttetSak(
            endringstype = Personhendelse.Endringstype.OPPRETTET,
            hendelse = Personhendelse.Hendelse.Dødsfall(fixedLocalDate),
            metadata = Personhendelse.Metadata(
                personidenter = personhendelse.personidenter.toNonEmptyList(),
                hendelseId = "hendelseId",
                tidligereHendelseId = null,
                offset = OFFSET,
                partisjon = PARTITION,
                master = "FREG",
                key = aktørId,
            ),
        )
    }

    @Test
    fun `mapper fra tom ekstern dødsfalltype til intern`() {
        val personhendelse = EksternPersonhendelse(
            "hendelseId",
            listOf(fnr, aktørId),
            "FREG",
            opprettet,
            "DOEDSFALL_V1",
            Endringstype.OPPRETTET,
            null,
            null,
            null,
            null,
            null,
            null,
        )
        val message = ConsumerRecord(TOPIC, PARTITION, OFFSET, aktørId, personhendelse)
        val actual = PersonhendelseMapper.map(message).getOrElse { throw RuntimeException("Feil skjedde i test") }

        actual shouldBe Personhendelse.IkkeTilknyttetSak(
            endringstype = Personhendelse.Endringstype.OPPRETTET,
            hendelse = Personhendelse.Hendelse.Dødsfall.EMPTY,
            metadata = Personhendelse.Metadata(
                personidenter = personhendelse.personidenter.toNonEmptyList(),
                hendelseId = "hendelseId",
                tidligereHendelseId = null,
                offset = OFFSET,
                partisjon = PARTITION,
                master = "FREG",
                key = aktørId,
            ),
        )
    }

    @Test
    fun `mapper fra ekstern utflyttingstype til intern`() {
        val personhendelse = EksternPersonhendelse(
            "hendelseId",
            listOf(fnr, aktørId),
            "FREG",
            opprettet,
            "UTFLYTTING_FRA_NORGE",
            Endringstype.OPPRETTET,
            null,
            null,
            null,
            UtflyttingFraNorge("Sverige", "Stockholm", fixedLocalDate),
            null,
            null,
        )
        val message = ConsumerRecord(TOPIC, PARTITION, OFFSET, aktørId, personhendelse)
        val actual = PersonhendelseMapper.map(message).getOrElse { throw RuntimeException("Feil skjedde i test") }

        actual shouldBe Personhendelse.IkkeTilknyttetSak(
            endringstype = Personhendelse.Endringstype.OPPRETTET,
            hendelse = Personhendelse.Hendelse.UtflyttingFraNorge(fixedLocalDate),
            metadata = Personhendelse.Metadata(
                personidenter = personhendelse.personidenter.toNonEmptyList(),
                hendelseId = "hendelseId",
                tidligereHendelseId = null,
                offset = OFFSET,
                partisjon = PARTITION,
                master = "FREG",
                key = aktørId,
            ),
        )
    }

    @Test
    fun `mapper fra tom ekstern utflyttingstype til intern`() {
        val personhendelse = EksternPersonhendelse(
            "hendelseId",
            listOf(fnr, aktørId),
            "FREG",
            opprettet,
            "UTFLYTTING_FRA_NORGE",
            Endringstype.OPPRETTET,
            null,
            null,
            null,
            null,
            null,
            null,
        )
        val message = ConsumerRecord(TOPIC, PARTITION, OFFSET, aktørId, personhendelse)
        val actual = PersonhendelseMapper.map(message).getOrElse { throw RuntimeException("Feil skjedde i test") }

        actual shouldBe Personhendelse.IkkeTilknyttetSak(
            endringstype = Personhendelse.Endringstype.OPPRETTET,
            hendelse = Personhendelse.Hendelse.UtflyttingFraNorge.EMPTY,
            metadata = Personhendelse.Metadata(
                personidenter = personhendelse.personidenter.toNonEmptyList(),
                hendelseId = "hendelseId",
                tidligereHendelseId = null,
                offset = OFFSET,
                partisjon = PARTITION,
                master = "FREG",
                key = aktørId,
            ),
        )
    }

    @Test
    fun `mapper fra ekstern sivilstand til intern`() {
        val personhendelse = EksternPersonhendelse(
            "hendelseId",
            listOf(fnr, aktørId),
            "FREG",
            opprettet,
            "SIVILSTAND_V1",
            Endringstype.OPPRETTET,
            null,
            null,
            Sivilstand("UGIFT", null, null, null),
            null,
            null,
            null,
        )
        val message = ConsumerRecord(TOPIC, PARTITION, OFFSET, aktørId, personhendelse)
        val actual = PersonhendelseMapper.map(message).getOrElse { throw RuntimeException("Feil skjedde i test") }

        actual shouldBe Personhendelse.IkkeTilknyttetSak(
            endringstype = Personhendelse.Endringstype.OPPRETTET,
            hendelse = Personhendelse.Hendelse.Sivilstand(SivilstandTyper.UGIFT, null, null, null),
            metadata = Personhendelse.Metadata(
                hendelseId = "hendelseId",
                personidenter = personhendelse.personidenter.toNonEmptyList(),
                tidligereHendelseId = null,
                offset = OFFSET,
                partisjon = PARTITION,
                master = "FREG",
                key = aktørId,
            ),
        )
    }

    @Test
    fun `mapper fra tom ekstern sivilstand til intern`() {
        val personhendelse = EksternPersonhendelse(
            "hendelseId",
            listOf(fnr, aktørId),
            "FREG",
            opprettet,
            "SIVILSTAND_V1",
            Endringstype.OPPRETTET,
            null,
            null,
            null,
            null,
            null,
            null,
        )
        val message = ConsumerRecord(TOPIC, PARTITION, OFFSET, aktørId, personhendelse)
        val actual = PersonhendelseMapper.map(message).getOrElse { throw RuntimeException("Feil skjedde i test") }

        actual shouldBe Personhendelse.IkkeTilknyttetSak(
            endringstype = Personhendelse.Endringstype.OPPRETTET,
            hendelse = Personhendelse.Hendelse.Sivilstand.EMPTY,
            metadata = Personhendelse.Metadata(
                hendelseId = "hendelseId",
                personidenter = personhendelse.personidenter.toNonEmptyList(),
                tidligereHendelseId = null,
                offset = OFFSET,
                partisjon = PARTITION,
                master = "FREG",
                key = aktørId,
            ),
        )
    }

    @Test
    fun `fjerner prepend i key`() {
        val personhendelse = EksternPersonhendelse(
            "hendelseId",
            listOf(fnr, aktørId),
            "FREG",
            opprettet,
            "UTFLYTTING_FRA_NORGE",
            Endringstype.OPPRETTET,
            null,
            null,
            null,
            UtflyttingFraNorge("Sverige", "Stockholm", fixedLocalDate),
            null,
            null,
        )
        val message = ConsumerRecord(TOPIC, PARTITION, OFFSET, "\u0000$aktørId", personhendelse)
        val actual = PersonhendelseMapper.map(message)

        actual shouldBe Personhendelse.IkkeTilknyttetSak(
            endringstype = Personhendelse.Endringstype.OPPRETTET,
            hendelse = Personhendelse.Hendelse.UtflyttingFraNorge(fixedLocalDate),
            metadata = Personhendelse.Metadata(
                hendelseId = "hendelseId",
                personidenter = personhendelse.personidenter.toNonEmptyList(),
                tidligereHendelseId = null,
                offset = OFFSET,
                partisjon = PARTITION,
                master = "FREG",
                key = aktørId,
            ),
        ).right()
    }

    @Test
    fun `skipper hendelser vi ikke er intressert i`() {
        val personhendelse = EksternPersonhendelse(
            "hendelseId",
            listOf(fnr, aktørId),
            "FREG",
            opprettet,
            "FOEDSEL_V1",
            Endringstype.OPPRETTET,
            null,
            null,
            null,
            UtflyttingFraNorge("Sverige", "Stockholm", fixedLocalDate),
            null,
            null,
        )
        val message = ConsumerRecord(TOPIC, PARTITION, OFFSET, aktørId, personhendelse)
        val actual = PersonhendelseMapper.map(message)

        actual shouldBe KunneIkkeMappePersonhendelse.IkkeAktuellOpplysningstype("hendelseId", "FOEDSEL_V1").left()
    }

    @Test
    fun `mapper fra ekstern bostedsadresse til intern`() {
        val personhendelse = EksternPersonhendelse(
            "hendelseId",
            listOf(fnr, aktørId),
            "FREG",
            opprettet,
            "BOSTEDSADRESSE_V1",
            Endringstype.OPPRETTET,
            null,
            null,
            null,
            null,
            null,
            Bostedsadresse(
                fixedLocalDate,
                fixedLocalDate,
                fixedLocalDate,
                "coAdressenavn",
                Vegadresse(
                    "matrikkelId", "husnummer", "husbokstav",
                    "bruksenhetsnummer", "adressenavn",
                    "kommunenummer", "bydelsnummer",
                    "tilleggsnavn", "postnummer",
                    Koordinater(1F, 2F, 3F),
                ),
                null,
                null,
                null,
            ),
        )
        val message = ConsumerRecord(TOPIC, PARTITION, OFFSET, aktørId, personhendelse)
        val actual = PersonhendelseMapper.map(message).getOrElse { throw RuntimeException("Feil skjedde i test") }

        actual shouldBe Personhendelse.IkkeTilknyttetSak(
            endringstype = Personhendelse.Endringstype.OPPRETTET,
            hendelse = Personhendelse.Hendelse.Bostedsadresse,
            metadata = Personhendelse.Metadata(
                hendelseId = "hendelseId",
                personidenter = personhendelse.personidenter.toNonEmptyList(),
                tidligereHendelseId = null,
                offset = OFFSET,
                partisjon = PARTITION,
                master = "FREG",
                key = aktørId,
            ),
        )
    }

    @Test
    fun `mapper fra ekstern kontaktadresse til intern`() {
        val personhendelse = EksternPersonhendelse(
            "hendelseId",
            listOf(fnr, aktørId),
            "FREG",
            opprettet,
            "KONTAKTADRESSE_V1",
            Endringstype.OPPRETTET,
            null,
            null,
            null,
            null,
            Kontaktadresse(
                fixedLocalDate,
                fixedLocalDate,
                "innland",
                "coAdressenavn",
                null,
                Vegadresse(
                    "matrikkelId", "husnummer", "husbokstav",
                    "bruksenhetsnummer", "adressenavn",
                    "kommunenummer", "bydelsnummer",
                    "tilleggsnavn", "postnummer",
                    Koordinater(1F, 2F, 3F),
                ),
                null,
                null,
                null,
            ),
            null,
        )
        val message = ConsumerRecord(TOPIC, PARTITION, OFFSET, aktørId, personhendelse)
        val actual = PersonhendelseMapper.map(message).getOrElse { throw RuntimeException("Feil skjedde i test") }

        actual shouldBe Personhendelse.IkkeTilknyttetSak(
            endringstype = Personhendelse.Endringstype.OPPRETTET,
            hendelse = Personhendelse.Hendelse.Kontaktadresse,
            metadata = Personhendelse.Metadata(
                hendelseId = "hendelseId",
                personidenter = personhendelse.personidenter.toNonEmptyList(),
                tidligereHendelseId = null,
                offset = OFFSET,
                partisjon = PARTITION,
                master = "FREG",
                key = aktørId,
            ),
        )
    }
}
