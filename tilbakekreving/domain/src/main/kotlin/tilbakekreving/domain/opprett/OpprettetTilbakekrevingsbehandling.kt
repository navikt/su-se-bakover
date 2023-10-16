@file:Suppress("PackageDirectoryMismatch")
// Må ligge i samme pakke som Tilbakekrevingsbehandling (siden det er et sealed interface), men trenger ikke ligge i samme mappe.

package tilbakekreving.domain

import dokument.domain.brev.Brevvalg
import no.nav.su.se.bakover.common.domain.Attesteringshistorikk
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import tilbakekreving.domain.vurdert.Månedsvurderinger
import java.util.UUID

data class OpprettetTilbakekrevingsbehandling(
    override val id: TilbakekrevingsbehandlingId,
    override val sakId: UUID,
    override val opprettet: Tidspunkt,
    override val opprettetAv: NavIdentBruker.Saksbehandler,
    override val kravgrunnlag: Kravgrunnlag,
    override val versjon: Hendelsesversjon,
    override val hendelseId: HendelseId,
    override val forhåndsvarselDokumentIder: List<UUID>,
) : KanVurdere {
    override val attesteringer: Attesteringshistorikk = Attesteringshistorikk.empty()

    override val månedsvurderinger: Månedsvurderinger? = null
    override val brevvalg: Brevvalg.SaksbehandlersValg? = null

    override fun leggTilBrevtekst(): VurdertTilbakekrevingsbehandling.Utfylt {
        TODO("Not yet implemented")
    }

    override fun leggTilForhåndsvarselDokumentId(dokumentId: UUID): Tilbakekrevingsbehandling = this.copy(
        forhåndsvarselDokumentIder = this.forhåndsvarselDokumentIder.plus(dokumentId),
    )
}
