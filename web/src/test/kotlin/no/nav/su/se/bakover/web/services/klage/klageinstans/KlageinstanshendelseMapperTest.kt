package no.nav.su.se.bakover.web.services.klage.klageinstans

import arrow.core.left
import behandling.klage.domain.UprosessertKlageinstanshendelse
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.Test
import java.util.UUID

internal class KlageinstanshendelseMapperTest {

    private val topic = "topic"
    private val partition = 0
    private val offset = 0L
    private val key = UUID.randomUUID()

    @Test
    fun `Fant ikke kilde`() {
        KlageinstanshendelseMapper.map(
            message = ConsumerRecord(topic, partition, offset, key.toString(), "{}"),
            topic = topic,
            clock = fixedClock,
        ) shouldBe KunneIkkeMappeKlageinstanshendelse.FantIkkeKilde.left()
    }

    @Test
    fun `Ikke aktuell opplysningstype`() {
        KlageinstanshendelseMapper.map(
            message = ConsumerRecord(topic, partition, offset, key.toString(), "{\"kilde\":\"OTHER_STONAD\"}"),
            topic = topic,
            clock = fixedClock,
        ) shouldBe KunneIkkeMappeKlageinstanshendelse.IkkeAktuellOpplysningstype("OTHER_STONAD").left()
    }

    @Test
    fun `Fant ikke eventId`() {
        KlageinstanshendelseMapper.map(
            message = ConsumerRecord(topic, partition, offset, key.toString(), "{\"kilde\":\"SUPSTONAD\"}"),
            topic = topic,
            clock = fixedClock,
        ) shouldBe KunneIkkeMappeKlageinstanshendelse.FantIkkeEventId.left()
    }

    @Test
    fun `Gyldig hendelse`() {
        val value = "{\"kilde\":\"SUPSTONAD\",\"eventId\":\"eventId\"}"
        val actual = KlageinstanshendelseMapper.map(
            message = ConsumerRecord(topic, partition, offset, key.toString(), value),
            topic = topic,
            clock = fixedClock,
        ).getOrFail()
        actual shouldBe UprosessertKlageinstanshendelse(
            id = actual.id,
            opprettet = fixedTidspunkt,
            metadata = UprosessertKlageinstanshendelse.Metadata(
                topic = topic,
                hendelseId = "eventId",
                offset = offset,
                partisjon = partition,
                key = key.toString(),
                value = value,
            ),
        )
    }
}
