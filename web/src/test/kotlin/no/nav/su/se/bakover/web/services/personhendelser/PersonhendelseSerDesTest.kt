package no.nav.su.se.bakover.web.services.personhendelser

import common.infrastructure.kafka.AvroDeserializer
import common.infrastructure.kafka.AvroSerializer
import io.kotest.matchers.shouldBe
import io.ktor.util.moveToByteArray
import no.nav.person.pdl.leesah.Endringstype
import no.nav.person.pdl.leesah.Personhendelse
import no.nav.person.pdl.leesah.doedsfall.Doedsfall
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.fnr
import org.junit.jupiter.api.Test
import java.time.Instant

internal class PersonhendelseSerDesTest {
    @Test
    fun `kan serialisere`() {
        val personhendelse = Personhendelse(
            "hendelseId",
            listOf(fnr.toString(), "1234567890000"),
            "FREG",
            Instant.now(fixedClock).truncatedTo(java.time.temporal.ChronoUnit.MILLIS),
            "DOEDSFALL_V1",
            Endringstype.OPPRETTET,
            null,
            Doedsfall(fixedLocalDate),
            null,
            null,
            null,
            null,
        )
        val serialisert = AvroSerializer<Personhendelse> {
            Personhendelse.getEncoder().encode(it).moveToByteArray()
        }.serialize("topic", personhendelse)
        val deserialisert = AvroDeserializer<Personhendelse> {
            Personhendelse.getDecoder().decode(it)
        }.deserialize("topic", serialisert)
        deserialisert shouldBe personhendelse
    }
}
