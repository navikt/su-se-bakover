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

/**
 * @param simulering Vi gjør en kontrollsimulering før vi sender behandlingen til attestering (på samme måte som vi gjør ved iverksetting).
 */
data class TilAttesteringHendelse(
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
        fun tilAttestering(
            sakId: UUID,
            tidligereHendelseId: HendelseId,
            meta: DefaultHendelseMetadata,
            versjon: Hendelsesversjon,
            clock: Clock,
            id: TilbakekrevingsbehandlingId,
            utførtAv: NavIdentBruker.Saksbehandler,
        ) = TilAttesteringHendelse(
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
}

fun VurdertTilbakekrevingsbehandling.Utfylt.tilAttestering(
    meta: DefaultHendelseMetadata,
    nesteVersjon: Hendelsesversjon,
    clock: Clock,
    utførtAv: NavIdentBruker.Saksbehandler,
): TilAttesteringHendelse {
    return TilAttesteringHendelse.tilAttestering(
        sakId = this.sakId,
        tidligereHendelseId = this.hendelseId,
        meta = meta,
        versjon = nesteVersjon,
        clock = clock,
        id = this.id,
        utførtAv = utførtAv,
    )
}

fun Tilbakekrevingsbehandling.applyHendelse(
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
