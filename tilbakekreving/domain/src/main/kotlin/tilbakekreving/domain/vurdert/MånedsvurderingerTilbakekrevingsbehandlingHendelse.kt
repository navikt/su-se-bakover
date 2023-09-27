@file:Suppress("PackageDirectoryMismatch")
// Må ligge i samme pakke som TilbakekrevingsbehandlingHendelse (siden det er et sealed interface), men trenger ikke ligge i samme mappe.

package tilbakekreving.domain

import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.HendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.hendelse.domain.Sakshendelse
import tilbakekreving.domain.vurdert.Månedsvurderinger
import java.util.UUID

data class MånedsvurderingerTilbakekrevingsbehandlingHendelse(
    override val hendelseId: HendelseId,
    override val sakId: UUID,
    override val hendelsestidspunkt: Tidspunkt,
    override val versjon: Hendelsesversjon,
    override val meta: HendelseMetadata,
    override val tidligereHendelseId: HendelseId,
    override val id: TilbakekrevingsbehandlingId,
    val utførtAv: NavIdentBruker.Saksbehandler,
    val vurderinger: Månedsvurderinger,
) : TilbakekrevingsbehandlingHendelse {
    override val entitetId: UUID = sakId
    override fun compareTo(other: Sakshendelse): Int {
        require(this.entitetId == other.entitetId && this.sakId == other.sakId)
        return this.versjon.compareTo(other.versjon)
    }
}

internal fun Tilbakekrevingsbehandling.applyHendelse(
    hendelse: MånedsvurderingerTilbakekrevingsbehandlingHendelse,
): VurdertTilbakekrevingsbehandling {
    return when (this) {
        is OpprettetTilbakekrevingsbehandling -> VurdertTilbakekrevingsbehandling.Påbegynt(
            forrigeSteg = this,
            månedsvurderinger = hendelse.vurderinger,
        )
        is VurdertTilbakekrevingsbehandling.Påbegynt -> VurdertTilbakekrevingsbehandling.Påbegynt(
            forrigeSteg = this,
            månedsvurderinger = hendelse.vurderinger,
        )
        is VurdertTilbakekrevingsbehandling.Utfylt -> VurdertTilbakekrevingsbehandling.Utfylt(
            forrigeSteg = this,
            månedsvurderinger = hendelse.vurderinger,
        )
        is AvbruttTilbakekrevingsbehandling,
        is IverksattTilbakekrevingsbehandling,
        is TilbakekrevingsbehandlingTilAttestering,
        -> TODO("implementer")
    }
}
