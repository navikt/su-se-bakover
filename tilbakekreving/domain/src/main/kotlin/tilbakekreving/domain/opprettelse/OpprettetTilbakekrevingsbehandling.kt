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

sealed interface OpprettetTilbakekrevingsbehandling :
    KanForhåndsvarsle,
    KanOppdatereKravgrunnlag,
    KanAnnullere {
    override val id: TilbakekrevingsbehandlingId
    override val sakId: UUID
    override val saksnummer: Saksnummer
    override val fnr: Fnr
    override val opprettet: Tidspunkt
    override val opprettetAv: NavIdentBruker.Saksbehandler
    override val versjon: Hendelsesversjon
    override val hendelseId: HendelseId
    override val erKravgrunnlagUtdatert: Boolean

    override fun erAvsluttet(): Boolean = false
    override fun erAvbrutt(): Boolean = false

    data class MedKravgrunnlag(
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
    ) : OpprettetTilbakekrevingsbehandling,
        KanVurdere,
        KanOppdatereNotat,
        KanEndresHarKravgrunnlag {

        override val attesteringer: Attesteringshistorikk = Attesteringshistorikk.empty()
        override val forhåndsvarselsInfo: List<ForhåndsvarselMetaInfo> = emptyList()
        override val vurderingerMedKrav: VurderingerMedKrav? = null
        override val vedtaksbrevvalg: Brevvalg.SaksbehandlersValg? = null
        override val notat: NonBlankString? = null

        override fun erÅpen() = true

        override fun leggTilForhåndsvarselDokumentId(
            dokumentId: UUID,
            hendelseId: HendelseId,
            versjon: Hendelsesversjon,
            hendelsesTidspunkt: Tidspunkt,
        ) = UnderBehandling.MedKravgrunnlag.Påbegynt(
            forrigeSteg = this,
            hendelseId = hendelseId,
            versjon = versjon,
            forhåndsvarselsInfo = listOf(ForhåndsvarselMetaInfo(dokumentId, hendelsesTidspunkt)),
            kravgrunnlag = this.kravgrunnlag,
        )

        override fun leggTilVurderinger(
            månedsvurderinger: VurderingerMedKrav,
            hendelseId: HendelseId,
            versjon: Hendelsesversjon,
        ) = UnderBehandling.MedKravgrunnlag.Påbegynt(
            forrigeSteg = this,
            hendelseId = hendelseId,
            vurderingerMedKrav = månedsvurderinger,
            versjon = versjon,
            kravgrunnlag = this.kravgrunnlag,
        )

        override fun oppdaterNotat(
            notat: NonBlankString?,
            hendelseId: HendelseId,
            versjon: Hendelsesversjon,
        ) = UnderBehandling.MedKravgrunnlag.Påbegynt(
            forrigeSteg = this,
            hendelseId = hendelseId,
            versjon = versjon,
            notat = notat,
            kravgrunnlag = this.kravgrunnlag,
        )
    }

    data class UtenKravgrunnlag(
        override val id: TilbakekrevingsbehandlingId,
        override val sakId: UUID,
        override val saksnummer: Saksnummer,
        override val fnr: Fnr,
        override val opprettet: Tidspunkt,
        override val opprettetAv: NavIdentBruker.Saksbehandler,
        override val versjon: Hendelsesversjon,
        override val hendelseId: HendelseId,
        override val erKravgrunnlagUtdatert: Boolean,
    ) : OpprettetTilbakekrevingsbehandling,
        KanForhåndsvarsle,
        KanAnnullere {

        override val attesteringer: Attesteringshistorikk = Attesteringshistorikk.empty()
        override val vurderingerMedKrav: VurderingerMedKrav? = null
        override val vedtaksbrevvalg: Brevvalg.SaksbehandlersValg? = null
        override val notat: NonBlankString? = null
        override val kravgrunnlag: Kravgrunnlag? = null
        override val forhåndsvarselsInfo: List<ForhåndsvarselMetaInfo> = emptyList()

        override fun leggTilForhåndsvarselDokumentId(
            dokumentId: UUID,
            hendelseId: HendelseId,
            versjon: Hendelsesversjon,
            hendelsesTidspunkt: Tidspunkt,
        ) = UnderBehandling.UtenKravgrunnlag(
            forrigeSteg = this,
            hendelseId = hendelseId,
            versjon = versjon,
            forhåndsvarselsInfo = listOf(ForhåndsvarselMetaInfo(dokumentId, hendelsesTidspunkt)),
        )
    }
}
