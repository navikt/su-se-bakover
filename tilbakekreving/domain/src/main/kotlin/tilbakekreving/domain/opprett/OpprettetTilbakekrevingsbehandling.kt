@file:Suppress("PackageDirectoryMismatch")
// Må ligge i samme pakke som Tilbakekrevingsbehandling (siden det er et sealed interface), men trenger ikke ligge i samme mappe.

package tilbakekreving.domain

import dokument.domain.brev.Brevvalg
import no.nav.su.se.bakover.common.domain.NonBlankString
import no.nav.su.se.bakover.common.domain.attestering.Attesteringshistorikk
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import tilbakekreving.domain.forhåndsvarsel.ForhåndsvarselMetaInfo
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import tilbakekreving.domain.vurdert.VurderingerMedKrav
import java.util.UUID

data class OpprettetTilbakekrevingsbehandling(
    override val id: TilbakekrevingsbehandlingId,
    override val sakId: UUID,
    override val opprettet: Tidspunkt,
    override val opprettetAv: NavIdentBruker.Saksbehandler,
    override val kravgrunnlag: Kravgrunnlag,
    override val versjon: Hendelsesversjon,
    override val hendelseId: HendelseId,
    override val erKravgrunnlagUtdatert: Boolean,
    override val notat: NonBlankString?,
) : KanForhåndsvarsle, KanVurdere, KanOppdatereKravgrunnlag {

    override val attesteringer: Attesteringshistorikk = Attesteringshistorikk.empty()
    override val forhåndsvarselsInfo: List<ForhåndsvarselMetaInfo> = emptyList()

    override fun erÅpen() = true

    override fun leggTilForhåndsvarselDokumentId(
        dokumentId: UUID,
        hendelseId: HendelseId,
        versjon: Hendelsesversjon,
        hendelsesTidspunkt: Tidspunkt,
    ) = UnderBehandling.Påbegynt(
        forrigeSteg = this,
        hendelseId = hendelseId,
        versjon = versjon,
        vurderingerMedKrav = this.vurderingerMedKrav,
        forhåndsvarselsInfo = listOf(ForhåndsvarselMetaInfo(dokumentId, hendelsesTidspunkt)),
        kravgrunnlag = kravgrunnlag,
        erKravgrunnlagUtdatert = this.erKravgrunnlagUtdatert,
        notat = this.notat,
    )

    override fun leggTilVurderinger(
        månedsvurderinger: VurderingerMedKrav,
        hendelseId: HendelseId,
        versjon: Hendelsesversjon,
    ) = UnderBehandling.Påbegynt(
        forrigeSteg = this,
        hendelseId = hendelseId,
        vurderingerMedKrav = månedsvurderinger,
        forhåndsvarselsInfo = listOf(),
        versjon = versjon,
        kravgrunnlag = kravgrunnlag,
        erKravgrunnlagUtdatert = this.erKravgrunnlagUtdatert,
        notat = this.notat,
    )

    override val vurderingerMedKrav: VurderingerMedKrav? = null
    override val vedtaksbrevvalg: Brevvalg.SaksbehandlersValg? = null
}
