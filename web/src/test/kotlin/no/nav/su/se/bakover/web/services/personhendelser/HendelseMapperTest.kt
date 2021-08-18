package no.nav.su.se.bakover.web.services.personhendelser

import arrow.core.getOrElse
import arrow.core.left
import io.kotest.matchers.shouldBe
import no.nav.person.pdl.leesah.Endringstype
import no.nav.person.pdl.leesah.doedsfall.Doedsfall
import no.nav.person.pdl.leesah.utflytting.UtflyttingFraNorge
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.hendelse.Personhendelse
import no.nav.su.se.bakover.service.FnrGenerator
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import no.nav.person.pdl.leesah.Personhendelse as EksternPersonhendelse

internal class HendelseMapperTest {
    private val TOPIC = "topic"
    private val PARTITION = 0
    private val OFFSET = 0L
    private val fixedClock: Clock = Clock.fixed(1.januar(2021).startOfDay().instant, ZoneOffset.UTC)

    private val preprendedText = "123456"
    private val aktørId = "1234567890000"
    private val fnr = FnrGenerator.random().toString()
    private val opprettet = Instant.now(fixedClock)
    private val tidspunkt = LocalDate.now(fixedClock)

    private val key = "${preprendedText}$aktørId"

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
            Doedsfall(tidspunkt),
            null,
            null,
        )
        val message = ConsumerRecord(TOPIC, PARTITION, OFFSET, key, personhendelse)
        val actual = HendelseMapper.map(message).getOrElse { throw RuntimeException("Feil skjedde i test") }

        actual shouldBe Personhendelse.Ny(
            gjeldendeAktørId = AktørId(aktørId),
            endringstype = Personhendelse.Endringstype.OPPRETTET,
            hendelse = Personhendelse.Hendelse.Dødsfall(tidspunkt),
            personidenter = personhendelse.getPersonidenter(),
            metadata = Personhendelse.Metadata(
                hendelseId = "hendelseId",
                tidligereHendelseId = null,
                offset = OFFSET,
                partisjon = PARTITION,
                master = "FREG",
                key = key,
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
            UtflyttingFraNorge("Sverige", "Stockholm", tidspunkt),
        )
        val message = ConsumerRecord(TOPIC, PARTITION, OFFSET, key, personhendelse)
        val actual = HendelseMapper.map(message).getOrElse { throw RuntimeException("Feil skjedde i test") }

        actual shouldBe Personhendelse.Ny(
            gjeldendeAktørId = AktørId(aktørId),
            endringstype = Personhendelse.Endringstype.OPPRETTET,
            hendelse = Personhendelse.Hendelse.UtflyttingFraNorge(tidspunkt),
            personidenter = personhendelse.getPersonidenter(),
            metadata = Personhendelse.Metadata(
                hendelseId = "hendelseId",
                tidligereHendelseId = null,
                offset = OFFSET,
                partisjon = PARTITION,
                master = "FREG",
                key = key,
            ),
        )
    }

    @Test
    fun `aktørId som ikke finnes i personidenter gir feil`() {
        val personhendelse = EksternPersonhendelse(
            "hendelseId",
            listOf(fnr),
            "master",
            opprettet,
            "UTFLYTTING_FRA_NORGE",
            Endringstype.OPPRETTET,
            null,
            null,
            null,
            UtflyttingFraNorge("Sverige", "Stockholm", tidspunkt),
        )
        val message = ConsumerRecord(TOPIC, PARTITION, OFFSET, key, personhendelse)
        val actual = HendelseMapper.map(message)

        actual shouldBe HendelseMapperException.KunneIkkeHenteAktørId.left()
    }

    @Test
    fun `skipper hendelser vi ikke er intressert i`() {
        val personhendelse = EksternPersonhendelse(
            "hendelseId",
            listOf(fnr, aktørId),
            "master",
            opprettet,
            "FOEDSEL_V1",
            Endringstype.OPPRETTET,
            null,
            null,
            null,
            UtflyttingFraNorge("Sverige", "Stockholm", tidspunkt),
        )
        val message = ConsumerRecord(TOPIC, PARTITION, OFFSET, key, personhendelse)
        val actual = HendelseMapper.map(message)

        actual shouldBe HendelseMapperException.IkkeAktuellOpplysningstype.left()
    }
}
