package no.nav.su.se.bakover.web.services.klage

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.klage.Klagevedtak
import org.junit.jupiter.api.Test
import java.util.UUID

internal class KlagevedtakJobTest {

    @Test
    fun `deserializerer klagevedtak-melding`() {
        val klagevedtakId = UUID.randomUUID()

        val eventId = UUID.randomUUID()
        val kildeReferanse = UUID.randomUUID()
        val kabalReferanse = UUID.randomUUID()

        KlagevedtakJob.mapper(
            klagevedtakId,
            jsonMelding(
                eventId = eventId,
                kildeReferanse = kildeReferanse,
                kabalReferanse = kabalReferanse,
                vedtaksbrevReferanse = "210219347"
            )
        ) shouldBe Klagevedtak.Uprosessert(
            id = klagevedtakId,
            eventId = eventId.toString(),
            klageId = kildeReferanse,
            utfall = Klagevedtak.Utfall.STADFESTELSE,
            vedtaksbrevReferanse = "210219347",
        )
    }

    private fun jsonMelding(
        eventId: UUID,
        kildeReferanse: UUID,
        kabalReferanse: UUID,
        vedtaksbrevReferanse: String,
    ) =
        """
            {"eventId":"$eventId","kildeReferanse":"$kildeReferanse","kilde":"SUPSTONAD","utfall":"STADFESTELSE","vedtaksbrevReferanse":"$vedtaksbrevReferanse","kabalReferanse":"$kabalReferanse"}
        """.trimIndent()
}
