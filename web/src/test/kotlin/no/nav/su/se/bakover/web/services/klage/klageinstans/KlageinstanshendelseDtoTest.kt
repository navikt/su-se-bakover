package no.nav.su.se.bakover.web.services.klage.klageinstans

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.application.journal.JournalpostId
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
                // Legger på en time for emulere tidssonen Europe/Oslo i januar. Skal være 1 time etter UTC.
                avsluttetTidspunkt = "2021-01-01T02:02:03.456789",
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
                avsluttetTidspunkt = "2021-01-01T01:02:03.456789Z",
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
                avsluttetTidspunkt = "2021-01-01T01:02:03.456789+01:00",
            ),
        ) shouldBe KunneIkkeTolkeKlageinstanshendelse.AnkehendelserStøttesIkke.left()
    }

    private fun jsonMelding(
        eventId: UUID = UUID.randomUUID(),
        kildeReferanse: UUID = UUID.randomUUID(),
        type: String,
        journalpostReferanser: List<String> = listOf("123", "456"),
        avsluttetTidspunkt: String,
    ) = //language=JSON
        """
            {
              "eventId": "$eventId",
              "kildeReferanse":"$kildeReferanse",
              "kilde":"SUPSTONAD",
              "kabalReferanse":"c0aef33a-da01-4262-ab55-1bbdde157e8a",
              "type":"$type",
              "detaljer":{
                "klagebehandlingAvsluttet":{
                  "avsluttet":"$avsluttetTidspunkt",
                  "utfall":"STADFESTELSE",
                  "journalpostReferanser":[${journalpostReferanser.joinToString(",")}]
                }
              },
              "ankebehandlingOpprettet": null,
              "ankebehandlingAvsluttet": null
            }
        """.trimIndent()
}
