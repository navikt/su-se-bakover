package no.nav.su.se.bakover.client.kabal

import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.generer
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import java.time.LocalDate

internal class KabalRequestTest {
    val fnr = Fnr.generer()

    val request = KabalRequest(
        avsenderSaksbehandlerIdent = "123456",
        dvhReferanse = "dvhReferanse",
        fagsak = KabalRequest.Fagsak(fagsakId = "2021"),
        hjemler = listOf(
            KabalRequest.Hjemler(
                kapittel = 9,
                lov = KabalRequest.Hjemler.Lov.FOLKETRYGDLOVEN,
                paragraf = 1,
            ),
        ),
        innsendtTilNav = LocalDate.now(fixedClock),
        mottattFoersteinstans = LocalDate.now(fixedClock),
        kilde = "su-se-bakover",
        kildeReferanse = "klageId",
        klager = KabalRequest.Klager(id = KabalRequest.PartId(verdi = fnr.toString()), skalKlagerMottaKopi = false),
        tilknyttedeJournalposter = listOf(
            KabalRequest.TilknyttedeJournalposter(
                journalpostId = JournalpostId(value = "journalpostId1"),
                type = KabalRequest.TilknyttedeJournalposter.Type.OVERSENDELSESBREV,
            ),
            KabalRequest.TilknyttedeJournalposter(
                journalpostId = JournalpostId(value = "journalpostId2"),
                type = KabalRequest.TilknyttedeJournalposter.Type.BRUKERS_KLAGE,
            ),
        ),
        kommentar = null,
        frist = null,
        sakenGjelder = null,
        oversendtKaDato = null,
        type = "KLAGE",
        ytelse = "", // todo ai: aventer til Kabal får støtte for flere ytelser
    )

    @Test
    fun `serialisering av requesten`() {
        val actual = serialize(request)
        val expected = """
    {
        "avsenderEnhet": "4815",
        "avsenderSaksbehandlerIdent": "123456",
        "dvhReferanse": "dvhReferanse",
        "fagsak": {
            "fagsystem": "SUPSTONAD",
            "fagsakId": "2021"
        },
        "hjemler": [
          {
            "kapittel": 9,
            "lov": "FOLKETRYGDLOVEN",
            "paragraf": 1
          }
        ],
        "innsendtTilNav": "2021-01-01",
        "mottattFoersteinstans": "2021-01-01",
        "innsynUrl": null,
        "kilde": "su-se-bakover",
        "kildeReferanse": "klageId",
        "klager": {
          "id": {
            "type": "PERSON",
            "verdi": "$fnr"
          },
          "skalKlagerMottaKopi": false
        },
        "tilknyttedeJournalposter": [
          {
            "journalpostId": "journalpostId1",
            "type": "OVERSENDELSESBREV"
          },
          {
            "journalpostId": "journalpostId2",
            "type": "BRUKERS_KLAGE"
          }
        ],
        "kommentar": null,
        "frist": null,
        "sakenGjelder": null,
        "oversendtKaDato": null,
        "type": "KLAGE",
        "ytelse": "" 
    }
        """.trimIndent()

        JSONAssert.assertEquals(expected, actual, true)
    }
}
