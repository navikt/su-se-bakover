package tilbakekreving.domain

import behandling.domain.BehandlingMedAttestering
import dokument.domain.brev.Brevvalg
import no.nav.su.se.bakover.common.domain.NonBlankString
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.attestering.Attesteringshistorikk
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import tilbakekreving.domain.forhåndsvarsel.ForhåndsvarselMetaInfo
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import tilbakekreving.domain.vurdering.VurderingerMedKrav
import java.util.UUID

/**
 * Starter som [OpprettetTilbakekrevingsbehandling] & aksepterer kun 1 åpen behandling om gangen
 * Deretter tar de stilling til om hver måned i kravgrunnlaget skal tilbakekreves, eller ikke.
 * Vi får deretter en tilstand [UnderBehandling]
 *
 * @property versjon versjonen til den siste hendelsen knyttet til denne tilbakekrevingsbehandlingen
 * @property hendelseId hendelses iden til den siste hendelsen knyttet til denne tilbakekrevingsbehandlingen
 */
sealed interface Tilbakekrevingsbehandling : BehandlingMedAttestering {
    override val id: TilbakekrevingsbehandlingId
    override val sakId: UUID
    override val saksnummer: Saksnummer
    override val fnr: Fnr
    override val opprettet: Tidspunkt
    override val attesteringer: Attesteringshistorikk
    val opprettetAv: NavIdentBruker.Saksbehandler
    val kravgrunnlag: Kravgrunnlag
    val erKravgrunnlagUtdatert: Boolean
    val vurderingerMedKrav: VurderingerMedKrav?

    // TODO jah: Brevvalg.SaksbehandlersValg er for generell. Vi trenger en mer spesifikk type for tilbakekreving.
    val vedtaksbrevvalg: Brevvalg.SaksbehandlersValg?
    val versjon: Hendelsesversjon
    val hendelseId: HendelseId
    val forhåndsvarselsInfo: List<ForhåndsvarselMetaInfo>
    val notat: NonBlankString?

    fun erÅpen(): Boolean
}
