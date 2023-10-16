@file:Suppress("PackageDirectoryMismatch")
// Må ligge i samme pakke som TilbakekrevingsbehandlingHendelse (siden det er et sealed interface), men trenger ikke ligge i samme mappe.

package tilbakekreving.domain

import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.DefaultHendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.hendelse.domain.Sakshendelse
import java.time.Clock
import java.util.UUID

data class IverksettHendelse(
    override val hendelseId: HendelseId,
    override val sakId: UUID,
    override val hendelsestidspunkt: Tidspunkt,
    override val versjon: Hendelsesversjon,
    override val meta: DefaultHendelseMetadata,
    override val id: TilbakekrevingsbehandlingId,
    override val tidligereHendelseId: HendelseId,
    override val utførtAv: NavIdentBruker.Saksbehandler,
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
            meta: DefaultHendelseMetadata,
            versjon: Hendelsesversjon,
            clock: Clock,
            id: TilbakekrevingsbehandlingId,
            utførtAv: NavIdentBruker.Saksbehandler,
        ) = IverksettHendelse(
            hendelseId = HendelseId.generer(),
            sakId = sakId,
            hendelsestidspunkt = Tidspunkt.now(clock),
            versjon = versjon,
            meta = meta,
            id = id,
            tidligereHendelseId = tidligereHendelseId,
            utførtAv = utførtAv,
        )
    }

    override fun applyToState(behandling: Tilbakekrevingsbehandling): IverksattTilbakekrevingsbehandling {
        return when (behandling) {
            is OpprettetTilbakekrevingsbehandling,
            is UnderBehandling,
            is AvbruttTilbakekrevingsbehandling,
            is IverksattTilbakekrevingsbehandling,
            -> throw IllegalArgumentException("Kan ikke gå fra [Opprettet, Vurdert, Avbrutt, Iverksatt] -> Iverksatt. Støtter kun å fra TilAttestering Hendelse ${this.hendelseId}, for sak ${this.sakId} ")

            is TilbakekrevingsbehandlingTilAttestering -> IverksattTilbakekrevingsbehandling(
                forrigeSteg = behandling,
            )
        }
    }
}

fun TilbakekrevingsbehandlingTilAttestering.iverksett(
    meta: DefaultHendelseMetadata,
    nesteVersjon: Hendelsesversjon,
    clock: Clock,
    utførtAv: NavIdentBruker.Saksbehandler,
): IverksettHendelse {
    return IverksettHendelse.iverksett(
        sakId = this.sakId,
        tidligereHendelseId = this.hendelseId,
        meta = meta,
        versjon = nesteVersjon,
        clock = clock,
        id = this.id,
        utførtAv = utførtAv,
    )
}
