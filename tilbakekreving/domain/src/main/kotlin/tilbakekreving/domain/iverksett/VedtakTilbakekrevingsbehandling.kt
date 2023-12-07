package tilbakekreving.domain.iverksett

import dokument.domain.Dokumenttilstand
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.vedtak.domain.Vedtak
import java.util.UUID

class VedtakTilbakekrevingsbehandling : Vedtak {
    override val id: UUID
        get() = TODO("Not yet implemented")
    override val opprettet: Tidspunkt
        get() = TODO("Not yet implemented")
    override val saksbehandler: NavIdentBruker.Saksbehandler
        get() = TODO("Not yet implemented")
    override val attestant: NavIdentBruker.Attestant
        get() = TODO("Not yet implemented")
    override val dokumenttilstand: Dokumenttilstand
        get() = TODO("Not yet implemented")
}
