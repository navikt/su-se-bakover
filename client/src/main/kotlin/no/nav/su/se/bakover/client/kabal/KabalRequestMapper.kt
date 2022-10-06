package no.nav.su.se.bakover.client.kabal

import no.nav.su.se.bakover.client.kabal.KabalRequest.Hjemmel.Companion.toKabalHjemler
import no.nav.su.se.bakover.common.application.journal.JournalpostId
import no.nav.su.se.bakover.domain.klage.OversendtKlage
import no.nav.su.se.bakover.domain.klage.VurderingerTilKlage
import java.time.ZoneOffset

internal object KabalRequestMapper {
    fun map(
        klage: OversendtKlage,
        journalpostIdForVedtak: JournalpostId,
    ): KabalRequest {
        return KabalRequest(
            avsenderSaksbehandlerIdent = klage.saksbehandler.navIdent,
            dvhReferanse = klage.id.toString(),
            fagsak = KabalRequest.Fagsak(klage.saksnummer.toString()),
            hjemler = (klage.vurderinger.vedtaksvurdering as VurderingerTilKlage.Vedtaksvurdering.Utfylt.Oppretthold).hjemler.toKabalHjemler(),
            innsendtTilNav = klage.datoKlageMottatt,
            mottattFoersteinstans = klage.opprettet.toLocalDate(ZoneOffset.UTC),
            kildeReferanse = klage.id.toString(),
            klager = KabalRequest.Klager(
                id = KabalRequest.PartId(
                    verdi = klage.fnr.toString(),
                ),
            ),
            /* TODO ai: Se på å sende med journalpostId:n for OVERSENDELSESBREV:et, via en jobb */
            tilknyttedeJournalposter = listOf(
                KabalRequest.TilknyttedeJournalposter(
                    journalpostId = klage.journalpostId,
                    type = KabalRequest.TilknyttedeJournalposter.Type.BRUKERS_KLAGE,
                ),
                KabalRequest.TilknyttedeJournalposter(
                    journalpostId = journalpostIdForVedtak,
                    type = KabalRequest.TilknyttedeJournalposter.Type.OPPRINNELIG_VEDTAK,
                ),
            ),
        )
    }
}
