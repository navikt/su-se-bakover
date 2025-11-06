@file:Suppress("PackageDirectoryMismatch")
// Må ligge i samme pakke som TilbakekrevingsbehandlingHendelse (siden det er et sealed interface), men trenger ikke ligge i samme mappe.

package tilbakekreving.domain

import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.hendelse.domain.Sakshendelse
import tilbakekreving.domain.forhåndsvarsel.ForhåndsvarselTilbakekrevingsbehandlingCommand
import java.time.Clock
import java.util.UUID

data class ForhåndsvarselRedigerTilbakekrevingsbehandlingHendelse(
    override val hendelseId: HendelseId,
    override val sakId: UUID,
    override val hendelsestidspunkt: Tidspunkt,
    override val versjon: Hendelsesversjon,
    override val tidligereHendelseId: HendelseId,
    override val id: TilbakekrevingsbehandlingId,
    override val utførtAv: NavIdentBruker.Saksbehandler,
    val fritekst: String,
    val dokumentId: UUID = UUID.randomUUID(),
) : TilbakekrevingsbehandlingHendelse {
    override val entitetId: UUID = sakId
    override fun compareTo(other: Sakshendelse): Int {
        require(this.entitetId == other.entitetId && this.sakId == other.sakId)
        return this.versjon.compareTo(other.versjon)
    }

    /**
     * Et forhåndsvarsel endrer ikke tilstanden til behandlingen for øyeblikket. Brevene hentes separat.
     */
    fun applyToState(behandling: Tilbakekrevingsbehandling): KanForhåndsvarsle {
        return when (behandling) {
            is TilbakekrevingsbehandlingTilAttestering,
            is AvbruttTilbakekrevingsbehandling,
            is IverksattTilbakekrevingsbehandling,
            -> throw IllegalArgumentException("Kan ikke gå fra [Avbrutt, Iverksatt, TilAttestering] -> Vurdert. Hendelse ${this.hendelseId}, for sak ${this.sakId} ")

            is KanForhåndsvarsle -> behandling.oppdaterForhåndsvarselFritekst(
                forhåndsvarselFritekst = fritekst,
                hendelseId = hendelseId,
                versjon = versjon,
            )
        }
    }
}

fun KanForhåndsvarsle.leggTilForhåndsvarselFritekst(
    command: ForhåndsvarselTilbakekrevingsbehandlingCommand,
    tidligereHendelsesId: HendelseId,
    nesteVersjon: Hendelsesversjon,
    clock: Clock,
): Pair<ForhåndsvarselRedigerTilbakekrevingsbehandlingHendelse, KanForhåndsvarsle> =
    ForhåndsvarselRedigerTilbakekrevingsbehandlingHendelse(
        hendelseId = HendelseId.generer(),
        sakId = command.sakId,
        hendelsestidspunkt = Tidspunkt.now(clock),
        versjon = nesteVersjon,
        tidligereHendelseId = tidligereHendelsesId,
        id = command.behandlingId,
        utførtAv = command.utførtAv,
        fritekst = command.fritekst,
    ).let { it to it.applyToState(this) }
