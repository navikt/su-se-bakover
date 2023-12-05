@file:Suppress("PackageDirectoryMismatch")
// Må ligge i samme pakke som TilbakekrevingsbehandlingHendelse (siden det er et sealed interface), men trenger ikke ligge i samme mappe.

package tilbakekreving.domain

import no.nav.su.se.bakover.common.domain.attestering.Attestering
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.hendelse.domain.Sakshendelse
import java.time.Clock
import java.util.UUID

data class IverksattHendelse(
    override val hendelseId: HendelseId,
    override val sakId: UUID,
    override val hendelsestidspunkt: Tidspunkt,
    override val versjon: Hendelsesversjon,
    override val id: TilbakekrevingsbehandlingId,
    override val tidligereHendelseId: HendelseId,
    override val utførtAv: NavIdentBruker.Attestant,
) : TilbakekrevingsbehandlingHendelse {

    override val entitetId: UUID = sakId
    override fun compareTo(other: Sakshendelse): Int {
        require(this.entitetId == other.entitetId && this.sakId == other.sakId)
        return this.versjon.compareTo(other.versjon)
    }

    companion object {
        fun iverksett(
            sakId: UUID,
            tidligereHendelseId: HendelseId,
            versjon: Hendelsesversjon,
            clock: Clock,
            id: TilbakekrevingsbehandlingId,
            utførtAv: NavIdentBruker.Attestant,
        ) = IverksattHendelse(
            hendelseId = HendelseId.generer(),
            sakId = sakId,
            hendelsestidspunkt = Tidspunkt.now(clock),
            versjon = versjon,
            id = id,
            tidligereHendelseId = tidligereHendelseId,
            utførtAv = utførtAv,
        )
    }

    fun applyToState(behandling: Tilbakekrevingsbehandling): IverksattTilbakekrevingsbehandling {
        return when (behandling) {
            is OpprettetTilbakekrevingsbehandling,
            is UnderBehandling,
            is AvbruttTilbakekrevingsbehandling,
            is IverksattTilbakekrevingsbehandling,
            -> throw IllegalArgumentException("Kan ikke gå fra [Opprettet, Vurdert, Avbrutt, Iverksatt] -> Iverksatt. Støtter kun å fra TilAttestering Hendelse ${this.hendelseId}, for sak ${this.sakId} ")

            is TilbakekrevingsbehandlingTilAttestering -> IverksattTilbakekrevingsbehandling(
                forrigeSteg = behandling,
                hendelseId = this.hendelseId,
                versjon = this.versjon,
                attesteringer = behandling.attesteringer.leggTilNyAttestering(
                    attestering = Attestering.Iverksatt(
                        attestant = this.utførtAv,
                        opprettet = this.hendelsestidspunkt,
                    ),
                ),

            )
        }
    }
}

fun TilbakekrevingsbehandlingTilAttestering.iverksett(
    nesteVersjon: Hendelsesversjon,
    clock: Clock,
    utførtAv: NavIdentBruker.Attestant,
): IverksattHendelse {
    return IverksattHendelse.iverksett(
        sakId = this.sakId,
        tidligereHendelseId = this.hendelseId,
        versjon = nesteVersjon,
        clock = clock,
        id = this.id,
        utførtAv = utførtAv,
    )
}
