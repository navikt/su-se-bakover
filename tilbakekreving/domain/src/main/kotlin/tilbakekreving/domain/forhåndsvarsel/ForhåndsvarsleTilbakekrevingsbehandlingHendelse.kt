@file:Suppress("PackageDirectoryMismatch")
// Må ligge i samme pakke som TilbakekrevingsbehandlingHendelse (siden det er et sealed interface), men trenger ikke ligge i samme mappe.

package tilbakekreving.domain

import dokument.domain.LagretDokumentHendelse
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.DefaultHendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.hendelse.domain.Sakshendelse
import java.util.UUID

data class ForhåndsvarsleTilbakekrevingsbehandlingHendelse(
    override val hendelseId: HendelseId,
    override val sakId: UUID,
    override val hendelsestidspunkt: Tidspunkt,
    override val versjon: Hendelsesversjon,
    override val meta: DefaultHendelseMetadata,
    override val tidligereHendelseId: HendelseId,
    override val id: TilbakekrevingsbehandlingId,
    override val utførtAv: NavIdentBruker.Saksbehandler,
    val fritekst: String,
) : TilbakekrevingsbehandlingHendelse {
    override val entitetId: UUID = sakId
    override fun compareTo(other: Sakshendelse): Int {
        require(this.entitetId == other.entitetId && this.sakId == other.sakId)
        return this.versjon.compareTo(other.versjon)
    }
}

/**
 * Ved [ForhåndsvarsleTilbakekrevingsbehandlingHendelse] gjøres det ikke noe endringer på selve behandlingen
 */
@Suppress("UNUSED_PARAMETER")
internal fun Tilbakekrevingsbehandling.applyHendelse(
    hendelse: ForhåndsvarsleTilbakekrevingsbehandlingHendelse,
): Tilbakekrevingsbehandling {
    return this
}

/**
 * Denne burde bo på et finere sted.
 * Denne lagrer nye dokumenter på det som er forhåndsvarsel. Dersom det skal være andre brev, må noe endres
 */
internal fun Tilbakekrevingsbehandling.applyHendelse(
    hendelse: LagretDokumentHendelse,
): Tilbakekrevingsbehandling {
    return this.leggTilForhåndsvarselDokumentId(hendelse.dokument.id)
}
