package no.nav.su.se.bakover.client.kabal

import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.application.journal.JournalpostId
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.generer
import java.time.LocalDate
import java.time.ZoneOffset

internal object KabalRequestTestData {
    val fnr = Fnr.generer()

    val request = KabalRequest(
        avsenderSaksbehandlerIdent = "123456",
        dvhReferanse = "dvhReferanse",
        fagsak = KabalRequest.Fagsak(fagsakId = "2021"),
        hjemler = listOf(
            KabalRequest.Hjemmel.LOV_OM_SUPPLERENDE_STØNAD_PARAGRAF_3,
            KabalRequest.Hjemmel.LOV_OM_SUPPLERENDE_STØNAD_PARAGRAF_4,
        ),
        innsendtTilNav = LocalDate.now(fixedClock),
        mottattFoersteinstans = Tidspunkt.now(fixedClock).toLocalDate(ZoneOffset.UTC),
        kilde = "SUPSTONAD",
        kildeReferanse = "klageId",
        klager = KabalRequest.Klager(id = KabalRequest.PartId(verdi = fnr.toString())),
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
        ytelse = "SUP_UFF",
    )
}
