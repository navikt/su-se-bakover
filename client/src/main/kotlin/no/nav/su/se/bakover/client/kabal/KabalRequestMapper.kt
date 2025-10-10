package no.nav.su.se.bakover.client.kabal

import behandling.klage.domain.VurderingerTilKlage
import no.nav.su.se.bakover.client.kabal.KabalRequest.Hjemmel.Companion.toKabalHjemler
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.domain.klage.OversendtKlage

internal data object KabalRequestMapper {
    fun map(
        klage: OversendtKlage,
        journalpostIdForVedtak: JournalpostId,
    ): KabalRequest {
        return KabalRequest(
            klager = KabalRequest.Klager(
                id = KabalRequest.PartId(
                    verdi = klage.fnr.toString(),
                ),
            ),
            fagsak = KabalRequest.Fagsak(klage.saksnummer.toString()),
            kildeReferanse = klage.id.toString(),
            dvhReferanse = klage.id.toString(),
            hjemler = (klage.vurderinger.vedtaksvurdering as VurderingerTilKlage.Vedtaksvurdering.Utfylt.Oppretthold).hjemler.toKabalHjemler(),
            tilknyttedeJournalposter = listOf(
                KabalRequest.TilknyttedeJournalposter(
                    journalpostId = klage.journalpostId,
                    type = KabalRequest.TilknyttedeJournalposter.Type.BRUKERS_KLAGE,
                ),
                KabalRequest.TilknyttedeJournalposter(
                    journalpostId = journalpostIdForVedtak,
                    type = KabalRequest.TilknyttedeJournalposter.Type.OPPRINNELIG_VEDTAK,
                ),
                // TODO jah: Vi har ikke journalført klagebrevet på dette tidspunktet. Da måtte vi ha byttet til en jobb, som ikke sendte denne requesten før vi journalførte brevet.
                // TODO jah: En klage kan bunne i en eller flere søknader, siden et revurderingsvedtak kan gjøre det samme. Her kunne vi lenket til en eller flere søknader.
            ),
            brukersHenvendelseMottattNavDato = klage.datoKlageMottatt,
            innsendtTilNav = klage.datoKlageMottatt,
            kommentar = klage.genererKommentar(),
        )
    }
}
