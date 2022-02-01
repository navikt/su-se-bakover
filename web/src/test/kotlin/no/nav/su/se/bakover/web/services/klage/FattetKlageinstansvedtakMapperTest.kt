package no.nav.su.se.bakover.web.services.klage

import arrow.core.left
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.klage.UprosessertFattetKlageinstansvedtak
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.Test
import java.util.UUID

internal class FattetKlageinstansvedtakMapperTest {

    private val topic = "topic"
    private val partition = 0
    private val offset = 0L
    private val key = UUID.randomUUID()

    @Test
    fun `Fant ikke kilde`() {
        KlageinstansvedtakMapper.map(
            message = ConsumerRecord(topic, partition, offset, key.toString(), "{}"),
            clock = fixedClock,
        ) shouldBe KunneIkkeMappeKlagevedtak.FantIkkeKilde.left()
    }

    @Test
    fun `Ikke aktuell opplysningstype`() {
        KlageinstansvedtakMapper.map(
            message = ConsumerRecord(topic, partition, offset, key.toString(), "{\"kilde\":\"OTHER_STONAD\"}"),
            clock = fixedClock,
        ) shouldBe KunneIkkeMappeKlagevedtak.IkkeAktuellOpplysningstype("OTHER_STONAD").left()
    }

    @Test
    fun `Fant ikke eventId`() {
        KlageinstansvedtakMapper.map(
            message = ConsumerRecord(topic, partition, offset, key.toString(), "{\"kilde\":\"SUPSTONAD\"}"),
            clock = fixedClock,
        ) shouldBe KunneIkkeMappeKlagevedtak.FantIkkeEventId.left()
    }

    @Test
    fun `Gyldig hendelse`() {
        val value = "{\"kilde\":\"SUPSTONAD\",\"eventId\":\"eventId\"}"
        val actual = KlageinstansvedtakMapper.map(
            message = ConsumerRecord(topic, partition, offset, key.toString(), value),
            clock = fixedClock,
        ).orNull()!!
        actual shouldBe UprosessertFattetKlageinstansvedtak(
            id = actual.id,
            opprettet = fixedTidspunkt,
            metadata = UprosessertFattetKlageinstansvedtak.Metadata(
                hendelseId = "eventId",
                offset = offset,
                partisjon = partition,
                key = key.toString(),
                value = value,
            ),
        )
    }
}
