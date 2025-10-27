@file:Suppress("PackageDirectoryMismatch")
// Må ligge i samme pakke som TilbakekrevingsbehandlingHendelse (siden det er et sealed interface), men trenger ikke ligge i samme mappe.

package tilbakekreving.domain

import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.hendelse.domain.Sakshendelse
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import java.util.UUID

/**
 * Forholder seg til eksterne oppdateringer fra oppdrag.
 * Lag en ny hendelsestype dersom du skal oppdatere kravgrunnlaget fra vår side
 */
data class OppdatertKravgrunnlagPåTilbakekrevingHendelse(
    override val hendelseId: HendelseId,
    override val sakId: UUID,
    override val hendelsestidspunkt: Tidspunkt,
    override val versjon: Hendelsesversjon,
    override val tidligereHendelseId: HendelseId,
    override val id: TilbakekrevingsbehandlingId,
    override val utførtAv: NavIdentBruker.Saksbehandler,
    val kravgrunnlagPåSakHendelseId: HendelseId,
) : TilbakekrevingsbehandlingHendelse {
    override val entitetId: UUID = sakId
    override fun compareTo(other: Sakshendelse): Int {
        require(this.entitetId == other.entitetId && this.sakId == other.sakId)
        return this.versjon.compareTo(other.versjon)
    }

    fun applyToState(
        behandling: Tilbakekrevingsbehandling,
        kravgrunnlag: Kravgrunnlag,
    ): UnderBehandling.Påbegynt {
        return when (behandling) {
            is TilbakekrevingsbehandlingTilAttestering,
            is AvbruttTilbakekrevingsbehandling,
            is IverksattTilbakekrevingsbehandling,
            is OpprettetTilbakekrevingsbehandlingUtenKravgrunnlag,
            -> throw IllegalArgumentException("Tilstandene [Avbrutt, Iverksatt, TilAttestering] kan ikke oppdatere kravgrunnlag. Hendelse ${this.hendelseId}, for sak ${this.sakId} ")

            is KanOppdatereKravgrunnlag -> UnderBehandling.Påbegynt(
                forrigeSteg = behandling,
                hendelseId = hendelseId,
                versjon = versjon,
                vurderingerMedKrav = null,
                forhåndsvarselsInfo = behandling.forhåndsvarselsInfo,
                vedtaksbrevvalg = behandling.vedtaksbrevvalg,
                kravgrunnlag = kravgrunnlag,
                erKravgrunnlagUtdatert = false,
                notat = behandling.notat,
            )
        }
    }
}
