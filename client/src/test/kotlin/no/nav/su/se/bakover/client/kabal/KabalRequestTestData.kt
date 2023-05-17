package no.nav.su.se.bakover.client.kabal

import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.generer
import java.time.LocalDate
import java.time.ZoneOffset

internal object KabalRequestTestData {
    val fnr = Fnr.generer()

    val request = KabalRequest(
        klager = KabalRequest.Klager(id = KabalRequest.PartId(verdi = fnr.toString())),
        fagsak = KabalRequest.Fagsak(fagsakId = "2021"),
        kildeReferanse = "klageId",
        dvhReferanse = "dvhReferanse",
        hjemler = listOf(
            KabalRequest.Hjemmel.LOV_OM_SUPPLERENDE_STØNAD_PARAGRAF_3,
            KabalRequest.Hjemmel.LOV_OM_SUPPLERENDE_STØNAD_PARAGRAF_4,
        ),
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
        brukersHenvendelseMottattNavDato = Tidspunkt.now(fixedClock).toLocalDate(ZoneOffset.UTC),
        innsendtTilNav = LocalDate.now(fixedClock),
    )

    val expected = """
        {
            "klager": {
              "id": {
                "type": "PERSON",
                "verdi": "$fnr"
              }
            },
            "fagsak": {
                "fagsystem": "SUPSTONAD",
                "fagsakId": "2021"
            },
            "kildeReferanse": "klageId",
            "dvhReferanse": "dvhReferanse",
            "hjemler": [
                "SUP_ST_L_3",
                "SUP_ST_L_4"
            ],
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
            "brukersHenvendelseMottattNavDato": "2021-01-01",
            "innsendtTilNav": "2021-01-01",
            "type": "KLAGE",
            "forrigeBehandlendeEnhet": "4815",
            "kilde": "SUPSTONAD",
            "ytelse": "SUP_UFF"
        }
    """.trimIndent()
}
