package no.nav.su.se.bakover.client.kabal

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.test.oversendtKlage
import org.junit.jupiter.api.Test

internal class KabalRequestMapperTest {

    @Test
    fun `vedtaksnotat journalpostId inkluderes i tilknyttedeJournalposter`() {
        val klage = oversendtKlage().second
        val journalpostIdForVedtak = JournalpostId("vedtakJournalpost")
        val journalpostIdForVedtaksnotat = JournalpostId("vedtaksnotatJournalpost")

        val request = KabalRequestMapper.map(
            klage = klage,
            journalpostIdForVedtak = journalpostIdForVedtak,
            journalpostIdForVedtaksnotat = journalpostIdForVedtaksnotat,
        )

        request.tilknyttedeJournalposter shouldHaveSize 3
        request.tilknyttedeJournalposter shouldContain KabalRequest.TilknyttedeJournalposter(
            journalpostId = journalpostIdForVedtaksnotat,
            type = KabalRequest.TilknyttedeJournalposter.Type.ANNET,
        )
    }

    @Test
    fun `vedtaksnotat journalpostId utelates når null`() {
        val klage = oversendtKlage().second
        val journalpostIdForVedtak = JournalpostId("vedtakJournalpost")

        val request = KabalRequestMapper.map(
            klage = klage,
            journalpostIdForVedtak = journalpostIdForVedtak,
            journalpostIdForVedtaksnotat = null,
        )

        request.tilknyttedeJournalposter shouldHaveSize 2
        request.tilknyttedeJournalposter.none {
            it.type == KabalRequest.TilknyttedeJournalposter.Type.ANNET
        } shouldBe true
    }
}
