@file:Suppress("PackageDirectoryMismatch")
// Må ligge i samme pakke som Tilbakekrevingsbehandling (siden det er et sealed interface), men trenger ikke ligge i samme mappe.

package tilbakekreving.domain

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

data class OpprettetTilbakekrevingsbehandling(
    override val id: TilbakekrevingsbehandlingId,
    override val sakId: UUID,
    override val saksnummer: Saksnummer,
    override val fnr: Fnr,
    override val opprettet: Tidspunkt,
    override val opprettetAv: NavIdentBruker.Saksbehandler,
    override val kravgrunnlag: Kravgrunnlag,
    override val versjon: Hendelsesversjon,
    override val hendelseId: HendelseId,
    override val erKravgrunnlagUtdatert: Boolean,
) : KanForhåndsvarsle,
    KanVurdere,
    KanOppdatereKravgrunnlag,
    KanOppdatereNotat,
    KanAnnullere {

    override val attesteringer: Attesteringshistorikk = Attesteringshistorikk.empty()
    override val forhåndsvarselsInfo: List<ForhåndsvarselMetaInfo> = emptyList()
    override val vurderingerMedKrav: VurderingerMedKrav? = null
    override val vedtaksbrevvalg: Brevvalg.SaksbehandlersValg? = null
    override val notat: NonBlankString? = null

    override fun erÅpen() = true
    override fun erAvsluttet() = false
    override fun erAvbrutt() = false

    override fun leggTilForhåndsvarselDokumentId(
        dokumentId: UUID,
        hendelseId: HendelseId,
        versjon: Hendelsesversjon,
        hendelsesTidspunkt: Tidspunkt,
    ) = UnderBehandling.Påbegynt(
        forrigeSteg = this,
        hendelseId = hendelseId,
        versjon = versjon,
        forhåndsvarselsInfo = listOf(ForhåndsvarselMetaInfo(dokumentId, hendelsesTidspunkt)),
    )

    override fun leggTilVurderinger(
        månedsvurderinger: VurderingerMedKrav,
        hendelseId: HendelseId,
        versjon: Hendelsesversjon,
    ) = UnderBehandling.Påbegynt(
        forrigeSteg = this,
        hendelseId = hendelseId,
        vurderingerMedKrav = månedsvurderinger,
        versjon = versjon,
    )

    override fun oppdaterNotat(
        notat: NonBlankString?,
        hendelseId: HendelseId,
        versjon: Hendelsesversjon,
    ) = UnderBehandling.Påbegynt(
        forrigeSteg = this,
        hendelseId = hendelseId,
        versjon = versjon,
        notat = notat,
    )
}

data class OpprettetTilbakekrevingsbehandlingUtenKravgrunnlag(
    override val id: TilbakekrevingsbehandlingId,
    override val sakId: UUID,
    override val saksnummer: Saksnummer,
    override val fnr: Fnr,
    override val opprettet: Tidspunkt,
    override val opprettetAv: NavIdentBruker.Saksbehandler,
    override val versjon: Hendelsesversjon,
    override val hendelseId: HendelseId,
) : KanForhåndsvarsle,
    KanAnnullere {

    override val attesteringer: Attesteringshistorikk = Attesteringshistorikk.empty()
    override val forhåndsvarselsInfo: List<ForhåndsvarselMetaInfo> = emptyList()
    override val vurderingerMedKrav: VurderingerMedKrav? = null
    override val vedtaksbrevvalg: Brevvalg.SaksbehandlersValg? = null
    override val notat: NonBlankString? = null
    override val kravgrunnlag: Kravgrunnlag? = null
    override val erKravgrunnlagUtdatert: Boolean = false

    override fun leggTilForhåndsvarselDokumentId(
        dokumentId: UUID,
        hendelseId: HendelseId,
        versjon: Hendelsesversjon,
        hendelsesTidspunkt: Tidspunkt,
    ): UnderBehandling {
        TODO("Not yet implemented")
    }

    override fun erAvsluttet(): Boolean {
        TODO("Not yet implemented")
    }

    override fun erAvbrutt(): Boolean? {
        TODO("Not yet implemented")
    }
}
