@file:Suppress("PackageDirectoryMismatch")
// Må ligge i samme pakke som Tilbakekrevingsbehandling (siden det er et sealed interface), men trenger ikke ligge i samme mappe.

package tilbakekreving.domain

import no.nav.su.se.bakover.hendelse.domain.HendelseId
import tilbakekreving.domain.vurdert.Månedsvurderinger

sealed interface KanVurdere : KanEndres {

    fun leggTilVurderinger(
        hendelseId: HendelseId,
        månedsvurderinger: Månedsvurderinger,
    ): UnderBehandling

    override fun erÅpen() = true
}
