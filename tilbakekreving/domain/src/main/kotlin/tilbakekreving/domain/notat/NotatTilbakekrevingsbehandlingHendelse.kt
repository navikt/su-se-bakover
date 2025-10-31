@file:Suppress("PackageDirectoryMismatch")
// Må ligge i samme pakke som TilbakekrevingsbehandlingHendelse (siden det er et sealed interface), men trenger ikke ligge i samme mappe.

package tilbakekreving.domain

import no.nav.su.se.bakover.common.domain.NonBlankString
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.hendelse.domain.Sakshendelse
import tilbakekreving.domain.notat.OppdaterNotatCommand
import java.time.Clock
import java.util.UUID

data class NotatTilbakekrevingsbehandlingHendelse(
    override val hendelseId: HendelseId,
    override val sakId: UUID,
    override val hendelsestidspunkt: Tidspunkt,
    override val versjon: Hendelsesversjon,
    override val tidligereHendelseId: HendelseId,
    override val id: TilbakekrevingsbehandlingId,
    override val utførtAv: NavIdentBruker.Saksbehandler,
    val notat: NonBlankString?,
) : TilbakekrevingsbehandlingHendelse {
    override val entitetId: UUID = sakId
    override fun compareTo(other: Sakshendelse): Int {
        require(this.entitetId == other.entitetId && this.sakId == other.sakId)
        return this.versjon.compareTo(other.versjon)
    }

    fun applyToState(behandling: Tilbakekrevingsbehandling): UnderBehandling {
        return when (behandling) {
            is TilbakekrevingsbehandlingTilAttestering,
            is AvbruttTilbakekrevingsbehandling,
            is IverksattTilbakekrevingsbehandling,
            is OpprettetTilbakekrevingsbehandling.UtenKravgrunnlag,
            -> throw IllegalArgumentException("Kan ikke gå fra [TilAttestering, Avbrutt, Iverksatt,] -> Vurdert.Utfylt. Hendelse ${this.hendelseId}, for sak ${this.sakId} ")

            is KanOppdatereNotat -> behandling.oppdaterNotat(
                notat = this.notat,
                hendelseId = this.hendelseId,
                versjon = this.versjon,
            )
        }
    }
}

fun KanOppdatereNotat.leggTilNotat(
    command: OppdaterNotatCommand,
    tidligereHendelsesId: HendelseId,
    nesteVersjon: Hendelsesversjon,
    clock: Clock,
): Pair<NotatTilbakekrevingsbehandlingHendelse, UnderBehandling> {
    val hendelse = NotatTilbakekrevingsbehandlingHendelse(
        hendelseId = HendelseId.generer(),
        sakId = command.sakId,
        hendelsestidspunkt = Tidspunkt.now(clock),
        versjon = nesteVersjon,
        tidligereHendelseId = tidligereHendelsesId,
        id = command.behandlingId,
        utførtAv = command.utførtAv,
        notat = command.notat,
    )

    return hendelse to hendelse.applyToState(this)
}
