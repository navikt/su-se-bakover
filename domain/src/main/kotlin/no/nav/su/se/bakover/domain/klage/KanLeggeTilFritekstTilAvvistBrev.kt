package no.nav.su.se.bakover.domain.klage

import no.nav.su.se.bakover.common.ident.NavIdentBruker

interface KanLeggeTilFritekstTilAvvistBrev {
    fun leggTilFritekstTilAvvistVedtaksbrev(
        saksbehandler: NavIdentBruker.Saksbehandler,
        fritekstTilAvvistVedtaksbrev: String,
    ): AvvistKlage
}
