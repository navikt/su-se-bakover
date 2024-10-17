@file:Suppress("PackageDirectoryMismatch")
// Må ligge i samme pakke som Tilbakekrevingsbehandling (siden det er et sealed interface), men trenger ikke ligge i samme mappe.

package tilbakekreving.domain

import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon

sealed interface KanAnnullere : KanEndres {
    fun annuller(
        annulleringstidspunkt: Tidspunkt,
        annullertAv: NavIdentBruker.Saksbehandler,
        versjon: Hendelsesversjon,
    ): Pair<AvbruttHendelse, AvbruttTilbakekrevingsbehandling> {
        val hendelse = AvbruttHendelse(
            hendelseId = HendelseId.generer(),
            id = this.id,
            utførtAv = annullertAv,
            tidligereHendelseId = this.hendelseId,
            hendelsestidspunkt = annulleringstidspunkt,
            sakId = this.sakId,
            versjon = versjon,
            begrunnelse = "Behandling er blitt avbrutt fordi kravgrunnlaget skal annulleres.",
        )
        return hendelse to hendelse.applyToState(this)
    }
}
