package no.nav.su.se.bakover.client.kabal

import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.klage.IverksattKlage

internal object KabalRequestMapper {
    fun map(klage: IverksattKlage, sak: Sak): KabalRequest {
        return KabalRequest(
            avsenderSaksbehandlerIdent = klage.saksbehandler.navIdent,
            dvhReferanse = klage.id.toString(),
            fagsak = KabalRequest.Fagsak(sak.saksnummer.toString()),
            hjemler = listOf(),
            innsendtTilNav = klage.datoKlageMottatt,
            mottattFoersteinstans = klage.opprettet.toLocalDate(zoneIdOslo),
            kildeReferanse = klage.id.toString(),
            klager = KabalRequest.Klager(
                id = KabalRequest.PartId(
                    verdi = sak.fnr.toString(),
                ),
                skalKlagerMottaKopi = false,
            ),
            tilknyttedeJournalposter = listOf(
                KabalRequest.TilknyttedeJournalposter(
                    journalpostId = klage.journalpostId,
                    type = KabalRequest.TilknyttedeJournalposter.Type.BRUKERS_KLAGE,
                ),
            ),
            ytelse = "", // TODO ai: Fyll in riktig verdi
        )
    }
}
