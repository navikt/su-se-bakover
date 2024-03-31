package no.nav.su.se.bakover.domain.klage

import no.nav.su.se.bakover.common.ident.NavIdentBruker

interface KanBekrefteKlagevurdering {
    fun bekreftVurderinger(
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): VurdertKlage.Bekreftet
}
