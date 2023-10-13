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

data class TilAttesteringHendelse(
    override val hendelseId: HendelseId,
    override val sakId: UUID,
    override val hendelsestidspunkt: Tidspunkt,
    override val versjon: Hendelsesversjon,
    override val meta: DefaultHendelseMetadata,
    override val id: TilbakekrevingsbehandlingId,
    override val tidligereHendelseId: HendelseId,
    override val utførtAv: NavIdentBruker.Saksbehandler,
    // val simulering: Simulering, TODO jah: Flytt Simulering en felles plass.
) : TilbakekrevingsbehandlingHendelse {

    override val entitetId: UUID = sakId
    override fun compareTo(other: Sakshendelse): Int {
        require(this.entitetId == other.entitetId && this.sakId == other.sakId)
        return this.versjon.compareTo(other.versjon)
    }

    companion object {
        fun tilAttestering(
            forrigeHendelse: TilbakekrevingsbehandlingHendelse,
            meta: DefaultHendelseMetadata,
            versjon: Hendelsesversjon,
            clock: Clock,
            id: TilbakekrevingsbehandlingId,
            utførtAv: NavIdentBruker.Saksbehandler,
        ) = TilAttesteringHendelse(
            hendelseId = HendelseId.generer(),
            sakId = forrigeHendelse.sakId,
            hendelsestidspunkt = Tidspunkt.now(clock),
            versjon = versjon,
            meta = meta,
            id = id,
            tidligereHendelseId = forrigeHendelse.hendelseId,
            utførtAv = utførtAv,
        )
    }
}

internal fun Tilbakekrevingsbehandling.applyHendelse(
    @Suppress("UNUSED_PARAMETER") hendelse: TilAttesteringHendelse,
): TilbakekrevingsbehandlingTilAttestering {
    return when (this) {
        is OpprettetTilbakekrevingsbehandling,
        is VurdertTilbakekrevingsbehandling.Påbegynt,
        is TilbakekrevingsbehandlingTilAttestering,
        is AvbruttTilbakekrevingsbehandling,
        is IverksattTilbakekrevingsbehandling,
        -> throw IllegalArgumentException("Kan ikke gå fra [Opprettet, Påbegynt, TilAttestering, Avbrutt, Iverksatt] -> TilAttestering. Den må vurderes først. Hendelse ${this.hendelseId}, for sak ${this.sakId} ")

        is VurdertTilbakekrevingsbehandling.Utfylt -> TilbakekrevingsbehandlingTilAttestering(
            forrigeSteg = this,
        )
    }
}
