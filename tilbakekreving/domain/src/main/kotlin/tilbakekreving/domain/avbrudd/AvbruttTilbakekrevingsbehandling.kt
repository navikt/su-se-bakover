@file:Suppress("PackageDirectoryMismatch")
// Må ligge i samme pakke som Tilbakekrevingsbehandling (siden det er et sealed interface), men trenger ikke ligge i samme mappe.

package tilbakekreving.domain

import no.nav.su.se.bakover.common.domain.Avbrutt
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon

data class AvbruttTilbakekrevingsbehandling(
    val forrigeSteg: KanEndres,
    override val avsluttetTidspunkt: Tidspunkt,
    override val avsluttetAv: NavIdentBruker,
    override val versjon: Hendelsesversjon,
    val begrunnelse: String,
) : Tilbakekrevingsbehandling by forrigeSteg,
    Avbrutt {

    override fun erÅpen() = false
    override fun erAvsluttet() = true
    override fun erAvbrutt() = true
}
