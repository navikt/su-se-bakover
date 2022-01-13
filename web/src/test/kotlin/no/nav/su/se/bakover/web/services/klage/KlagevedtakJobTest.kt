package no.nav.su.se.bakover.web.services.klage

import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.klage.KlagevedtakUtfall
import no.nav.su.se.bakover.domain.klage.UprosessertKlagevedtak
import no.nav.su.se.bakover.test.fixedTidspunkt
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
            fixedTidspunkt,
            jsonMelding(
                eventId = eventId,
                kildeReferanse = kildeReferanse,
                kabalReferanse = kabalReferanse,
                vedtaksbrevReferanse = "210219347"
            )
        ) shouldBe UprosessertKlagevedtak(
            id = klagevedtakId,
            opprettet = fixedTidspunkt,
            eventId = eventId.toString(),
            klageId = kildeReferanse,
            utfall = KlagevedtakUtfall.STADFESTELSE,
            vedtaksbrevReferanse = "210219347",
        ).right()
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
