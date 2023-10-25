@file:Suppress("PackageDirectoryMismatch")
// Må ligge i samme pakke som Tilbakekrevingsbehandling (siden det er et sealed interface), men trenger ikke ligge i samme mappe.

package tilbakekreving.domain

import no.nav.su.se.bakover.common.domain.attestering.Attesteringshistorikk
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon

data class IverksattTilbakekrevingsbehandling(
    val forrigeSteg: TilbakekrevingsbehandlingTilAttestering,
    override val hendelseId: HendelseId,
    override val versjon: Hendelsesversjon,
    override val attesteringer: Attesteringshistorikk,
) : ErUtfylt by forrigeSteg
