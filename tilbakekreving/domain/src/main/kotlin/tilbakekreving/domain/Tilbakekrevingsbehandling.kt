package tilbakekreving.domain

import dokument.domain.brev.Brevvalg
import no.nav.su.se.bakover.common.domain.Attesteringshistorikk
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import tilbakekreving.domain.vurdert.Vurderinger
import java.util.UUID

/**
 * Starter som [OpprettetTilbakekrevingsbehandling] & aksepterer kun 1 åpen behandling om gangen
 * Deretter tar de stilling til om hver måned i kravgrunnlaget skal tilbakekreves, eller ikke.
 * Vi får deretter en tilstand [UnderBehandling]
 *
 * @property versjon versjonen til den siste hendelsen knyttet til denne tilbakekrevingsbehandlingen
 * @property hendelseId hendelses iden til den siste hendelsen knyttet til denne tilbakekrevingsbehandlingen
 */
sealed interface Tilbakekrevingsbehandling {
    val id: TilbakekrevingsbehandlingId
    val sakId: UUID
    val opprettet: Tidspunkt
    val opprettetAv: NavIdentBruker.Saksbehandler
    val kravgrunnlag: Kravgrunnlag
    val månedsvurderinger: Vurderinger?
    val vedtaksbrevvalg: Brevvalg.SaksbehandlersValg?
    val attesteringer: Attesteringshistorikk
    val versjon: Hendelsesversjon
    val hendelseId: HendelseId
    val forhåndsvarselDokumentIder: List<UUID>

    fun erÅpen(): Boolean
}
