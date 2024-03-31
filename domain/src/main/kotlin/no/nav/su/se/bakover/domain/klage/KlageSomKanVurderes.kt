package no.nav.su.se.bakover.domain.klage

import behandling.klage.domain.VurderingerTilKlage
import no.nav.su.se.bakover.common.ident.NavIdentBruker

interface KlageSomKanVurderes {
    fun vurder(
        saksbehandler: NavIdentBruker.Saksbehandler,
        vurderinger: VurderingerTilKlage,
    ): VurdertKlage
}
