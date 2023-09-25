package tilbakekreving.domain.opprett

import dokument.domain.brev.Brevvalg
import no.nav.su.se.bakover.common.domain.Attesteringshistorikk
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import tilbakekreving.domain.TilbakekrevingsbehandlingId
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import tilbakekreving.domain.vurdert.KanVurdere
import tilbakekreving.domain.vurdert.Månedsvurderinger
import tilbakekreving.domain.vurdert.VurdertTilbakekrevingsbehandling
import java.util.UUID

data class OpprettetTilbakekrevingsbehandling(
    override val id: TilbakekrevingsbehandlingId,
    override val sakId: UUID,
    override val opprettet: Tidspunkt,
    override val opprettetAv: NavIdentBruker.Saksbehandler,
    override val kravgrunnlag: Kravgrunnlag,
) : KanVurdere {
    override val attesteringer: Attesteringshistorikk = Attesteringshistorikk.empty()
    override val månedsvurderinger: Månedsvurderinger? = null
    override val brevvalg: Brevvalg.SaksbehandlersValg? = null

    override fun leggTilBrevtekst(): VurdertTilbakekrevingsbehandling.Utfylt {
        TODO("Not yet implemented")
    }
}
