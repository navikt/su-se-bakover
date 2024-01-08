@file:Suppress("PackageDirectoryMismatch")
// Må ligge i samme pakke som Tilbakekrevingsbehandling (siden det er et sealed interface), men trenger ikke ligge i samme mappe.

package tilbakekreving.domain

import no.nav.su.se.bakover.common.domain.NonBlankString
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon

sealed interface KanOppdatereNotat : KanEndres {
    override val notat: NonBlankString?

    fun oppdaterNotat(
        notat: NonBlankString?,
        hendelseId: HendelseId,
        versjon: Hendelsesversjon,
    ): UnderBehandling

    override fun erÅpen() = true
}
