@file:Suppress("PackageDirectoryMismatch")
// Må ligge i samme pakke som TilbakekrevingsbehandlingHendelse (siden det er et sealed interface), men trenger ikke ligge i samme mappe.

package tilbakekreving.domain

import dokument.domain.brev.Brevvalg
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.HendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.hendelse.domain.Sakshendelse
import java.util.UUID

data class BrevTilbakekrevingsbehandlingHendelse(
    override val hendelseId: HendelseId,
    override val sakId: UUID,
    override val hendelsestidspunkt: Tidspunkt,
    override val versjon: Hendelsesversjon,
    override val meta: HendelseMetadata,
    override val tidligereHendelseId: HendelseId,
    override val id: TilbakekrevingsbehandlingId,
    val utførtAv: NavIdentBruker.Saksbehandler,
    val brevvalg: Brevvalg.SaksbehandlersValg,
) : TilbakekrevingsbehandlingHendelse {
    override val entitetId: UUID = sakId
    override fun compareTo(other: Sakshendelse): Int {
        require(this.entitetId == other.entitetId && this.sakId == other.sakId)
        return this.versjon.compareTo(other.versjon)
    }

    init {
        when (brevvalg) {
            is Brevvalg.SaksbehandlersValg.SkalSendeBrev.InformasjonsbrevMedFritekst ->
                throw IllegalStateException("Ved tilbakekreving for sak $sakId, må brevet være av typen Vedtaksbrev. Tidligere hendelse var $tidligereHendelseId")

            is Brevvalg.SaksbehandlersValg.SkalIkkeSendeBrev,
            is Brevvalg.SaksbehandlersValg.SkalSendeBrev.Vedtaksbrev,
            -> Unit
        }
    }
}

internal fun Tilbakekrevingsbehandling.applyHendelse(
    hendelse: BrevTilbakekrevingsbehandlingHendelse,
): VurdertTilbakekrevingsbehandling.Utfylt {
    return when (this) {
        is OpprettetTilbakekrevingsbehandling -> throw IllegalArgumentException("Kan ikke gå fra en OpprettetTilbakekrevingsbehandling til BrevTilbakekrevingshendelse. Den må månedsvurderes først. Hendelse ${this.hendelseId}, for sak ${this.sakId} ")
        is VurdertTilbakekrevingsbehandling -> VurdertTilbakekrevingsbehandling.Utfylt(
            forrigeSteg = this,
            månedsvurderinger = this.månedsvurderinger,
            hendelseId = hendelse.hendelseId,
            brevvalg = hendelse.brevvalg,
            attesteringer = this.attesteringer,
        )

        is AvbruttTilbakekrevingsbehandling,
        is IverksattTilbakekrevingsbehandling,
        is TilbakekrevingsbehandlingTilAttestering,
        -> TODO("implementer")
    }
}
