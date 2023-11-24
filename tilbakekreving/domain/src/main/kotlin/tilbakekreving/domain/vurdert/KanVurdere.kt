@file:Suppress("PackageDirectoryMismatch")
// Må ligge i samme pakke som Tilbakekrevingsbehandling (siden det er et sealed interface), men trenger ikke ligge i samme mappe.

package tilbakekreving.domain

import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import tilbakekreving.domain.vurdert.VurderingerMedKrav

sealed interface KanVurdere : KanEndres {

    fun leggTilVurderinger(
        månedsvurderinger: VurderingerMedKrav,
        hendelseId: HendelseId,
        versjon: Hendelsesversjon,
    ): UnderBehandling

    override fun erÅpen() = true
}
