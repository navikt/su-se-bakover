package no.nav.su.se.bakover.web.services.klage.klageinstans

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.klage.KlageinstansUtfall
import no.nav.su.se.bakover.domain.klage.KunneIkkeTolkeKlageinstanshendelse
import no.nav.su.se.bakover.domain.klage.TolketKlageinstanshendelse
import no.nav.su.se.bakover.test.fixedTidspunkt
import org.junit.jupiter.api.Test
import java.util.UUID

internal class KlageinstanshendelseDtoTest {

    @Test
    fun `Kan deserialisere avsluttet klagebehandling`() {
        val id = UUID.randomUUID()
        val kildeReferanse = UUID.randomUUID()

        KlageinstanshendelseDto.toDomain(
            id = id,
            opprettet = fixedTidspunkt,
            json = jsonMelding(
                kildeReferanse = kildeReferanse,
                type = "KLAGEBEHANDLING_AVSLUTTET",
                journalpostReferanser = listOf("123", "456"),
            ),
        ) shouldBe TolketKlageinstanshendelse(
            id = id,
            opprettet = fixedTidspunkt,
            avsluttetTidspunkt = fixedTidspunkt,
            klageId = kildeReferanse,
            utfall = KlageinstansUtfall.STADFESTELSE,
            journalpostIDer = listOf(JournalpostId("123"), JournalpostId("456")),
        ).right()
    }

    @Test
    fun `Kan deserialisere opprettet ankebehandling`() {
        val id = UUID.randomUUID()
        val kildeReferanse = UUID.randomUUID()

        KlageinstanshendelseDto.toDomain(
            id = id,
            opprettet = fixedTidspunkt,
            json = jsonMelding(
                kildeReferanse = kildeReferanse,
                type = "ANKEBEHANDLING_OPPRETTET",
                journalpostReferanser = listOf("123", "456"),
            ),
        ) shouldBe KunneIkkeTolkeKlageinstanshendelse.AnkehendelserStøttesIkke.left()
    }

    @Test
    fun `Kan deserialisere avsluttet ankebehandling`() {
        val id = UUID.randomUUID()
        val kildeReferanse = UUID.randomUUID()

        KlageinstanshendelseDto.toDomain(
            id = id,
            opprettet = fixedTidspunkt,
            json = jsonMelding(
                kildeReferanse = kildeReferanse,
                type = "ANKEBEHANDLING_AVSLUTTET",
                journalpostReferanser = listOf("123", "456"),
            ),
        ) shouldBe KunneIkkeTolkeKlageinstanshendelse.AnkehendelserStøttesIkke.left()
    }

    private fun jsonMelding(
        eventId: UUID = UUID.randomUUID(),
        kildeReferanse: UUID = UUID.randomUUID(),
        type: String,
        journalpostReferanser: List<String> = listOf("123", "456"),
    ) =
        """
            {
              "eventId": "$eventId",
              "kildeReferanse":"$kildeReferanse",
              "kilde":"SUPSTONAD",
              "kabalReferanse":"123456",
              "type":"$type",
              "detaljer":{
                "avsluttet":"2021-01-01T01:02:03.456789000Z",
                "utfall":"STADFESTELSE",
                "journalpostReferanser":[${journalpostReferanser.joinToString(",")}]
              }
            }
        """.trimIndent()
}
