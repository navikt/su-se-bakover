@file:Suppress("PackageDirectoryMismatch")
// Må ligge i samme pakke som Tilbakekrevingsbehandling (siden det er et sealed interface), men trenger ikke ligge i samme mappe.

package tilbakekreving.domain

import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import tilbakekreving.domain.kravgrunnlag.OppdaterKravgrunnlagCommand
import java.time.Clock

sealed interface KanOppdatereKravgrunnlag : KanEndres {
    override val kravgrunnlag: Kravgrunnlag

    fun oppdaterKravgrunnlag(
        command: OppdaterKravgrunnlagCommand,
        nesteVersjon: Hendelsesversjon,
        nyttKravgrunnlag: Kravgrunnlag,
        clock: Clock,
    ): Pair<OppdatertKravgrunnlagPåTilbakekrevingHendelse, UnderBehandling.Påbegynt> {
        if (this.kravgrunnlag.hendelseId == nyttKravgrunnlag.hendelseId) {
            throw IllegalStateException("Prøvde å oppdatere kravgrunnlag for behandling ${this.id}, men kravgrunnlags-IDen er lik: ${nyttKravgrunnlag.hendelseId}")
        }

        val hendelse = OppdatertKravgrunnlagPåTilbakekrevingHendelse(
            hendelseId = HendelseId.generer(),
            sakId = command.sakId,
            hendelsestidspunkt = Tidspunkt.now(clock),
            versjon = nesteVersjon,
            tidligereHendelseId = this.hendelseId,
            id = command.behandlingId,
            utførtAv = command.oppdatertAv,
            kravgrunnlagPåSakHendelseId = nyttKravgrunnlag.hendelseId,
        )
        return hendelse to hendelse.applyToState(this, kravgrunnlag)
    }
}
