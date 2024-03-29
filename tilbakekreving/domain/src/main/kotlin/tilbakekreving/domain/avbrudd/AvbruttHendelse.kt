@file:Suppress("PackageDirectoryMismatch")
// Må ligge i samme pakke som TilbakekrevingsbehandlingHendelse (siden det er et sealed interface), men trenger ikke ligge i samme mappe.

package tilbakekreving.domain

import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.hendelse.domain.Sakshendelse
import java.time.Clock
import java.util.UUID

data class AvbruttHendelse(
    override val hendelseId: HendelseId,
    override val sakId: UUID,
    override val hendelsestidspunkt: Tidspunkt,
    override val versjon: Hendelsesversjon,
    override val id: TilbakekrevingsbehandlingId,
    override val tidligereHendelseId: HendelseId,
    override val utførtAv: NavIdentBruker.Saksbehandler,
    val begrunnelse: String,
) : TilbakekrevingsbehandlingHendelse {

    override val entitetId: UUID = sakId
    override fun compareTo(other: Sakshendelse): Int {
        require(this.entitetId == other.entitetId && this.sakId == other.sakId)
        return this.versjon.compareTo(other.versjon)
    }

    companion object {
        fun create(
            sakId: UUID,
            tidligereHendelseId: HendelseId,
            versjon: Hendelsesversjon,
            clock: Clock,
            id: TilbakekrevingsbehandlingId,
            utførtAv: NavIdentBruker.Saksbehandler,
            begrunnelse: String,
        ) = AvbruttHendelse(
            hendelseId = HendelseId.generer(),
            sakId = sakId,
            hendelsestidspunkt = Tidspunkt.now(clock),
            versjon = versjon,
            id = id,
            tidligereHendelseId = tidligereHendelseId,
            utførtAv = utførtAv,
            begrunnelse = begrunnelse,
        )
    }

    fun applyToState(behandling: Tilbakekrevingsbehandling): AvbruttTilbakekrevingsbehandling {
        return when (behandling) {
            is AvbruttTilbakekrevingsbehandling,
            is TilbakekrevingsbehandlingTilAttestering,
            is IverksattTilbakekrevingsbehandling,
            -> throw IllegalArgumentException("Kan ikke gå fra [Avbrutt, TilAttestering, Iverksatt] -> Avbrutt. Støtter kun tilstander der behandlingen kan endres. Hendelse ${this.hendelseId}, for sak ${this.sakId} ")

            is KanEndres -> AvbruttTilbakekrevingsbehandling(
                forrigeSteg = behandling,
                avsluttetTidspunkt = hendelsestidspunkt,
                avsluttetAv = utførtAv,
                begrunnelse = begrunnelse,
                versjon = this.versjon,
            )
        }
    }
}

fun KanEndres.avbryt(
    nesteVersjon: Hendelsesversjon,
    clock: Clock,
    utførtAv: NavIdentBruker.Saksbehandler,
    begrunnelse: String,
): Pair<AvbruttHendelse, AvbruttTilbakekrevingsbehandling> {
    return AvbruttHendelse.create(
        sakId = this.sakId,
        tidligereHendelseId = this.hendelseId,
        versjon = nesteVersjon,
        clock = clock,
        id = this.id,
        utførtAv = utførtAv,
        begrunnelse = begrunnelse,
    ).let { it to it.applyToState(this) }
}
