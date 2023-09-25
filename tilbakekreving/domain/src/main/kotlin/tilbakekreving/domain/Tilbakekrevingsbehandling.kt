package tilbakekreving.domain

import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import java.util.UUID

interface Tilbakekrevingsbehandling {
    val id: TilbakekrevingsbehandlingId
    val sakId: UUID
    val opprettet: Tidspunkt
    val opprettetAv: NavIdentBruker.Saksbehandler
    val kravgrunnlag: Kravgrunnlag
}
