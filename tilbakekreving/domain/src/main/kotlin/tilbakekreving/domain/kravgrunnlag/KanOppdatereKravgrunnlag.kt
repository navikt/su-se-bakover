@file:Suppress("PackageDirectoryMismatch")
// Må ligge i samme pakke som Tilbakekrevingsbehandling (siden det er et sealed interface), men trenger ikke ligge i samme mappe.

package tilbakekreving.domain

import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import tilbakekreving.domain.kravgrunnlag.OppdaterKravgrunnlagCommand
import java.lang.IllegalStateException
import java.time.Clock

sealed interface KanOppdatereKravgrunnlag : KanEndres {
    override val kravgrunnlag: Kravgrunnlag
    override val erKravgrunnlagUtdatert: Boolean

    fun oppdaterKravgrunnlag(
        command: OppdaterKravgrunnlagCommand,
        nesteVersjon: Hendelsesversjon,
        nyttKravgrunnlag: Kravgrunnlag,
        clock: Clock,
    ): Pair<OppdatertKravgrunnlagPåTilbakekrevingHendelse, UnderBehandling.Påbegynt> {
        if (this.kravgrunnlag.eksternKravgrunnlagId == nyttKravgrunnlag.eksternKravgrunnlagId) {
            throw IllegalStateException("Prøvde å oppdatere kravgrunnlag for behandling ${this.id}, men kravgrunnlags-id'en er lik")
        }

        val hendelse = OppdatertKravgrunnlagPåTilbakekrevingHendelse(
            hendelseId = HendelseId.generer(),
            sakId = command.sakId,
            hendelsestidspunkt = Tidspunkt.now(clock),
            versjon = nesteVersjon,
            tidligereHendelseId = this.hendelseId,
            meta = command.toDefaultHendelsesMetadata(),
            id = command.behandlingId,
            utførtAv = command.oppdatertAv,
            oppdatertKravgrunnlag = nyttKravgrunnlag,
        )
        return hendelse to hendelse.applyToState(this)
    }
}