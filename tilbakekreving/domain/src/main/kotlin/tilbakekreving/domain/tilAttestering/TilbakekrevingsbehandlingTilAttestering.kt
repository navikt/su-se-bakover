@file:Suppress("PackageDirectoryMismatch")
// Må ligge i samme pakke som Tilbakekrevingsbehandling (siden det er et sealed interface), men trenger ikke ligge i samme mappe.

package tilbakekreving.domain

import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon

data class TilbakekrevingsbehandlingTilAttestering(
    val forrigeSteg: UnderBehandling.MedKravgrunnlag.Utfylt,
    override val hendelseId: HendelseId,
    override val versjon: Hendelsesversjon,
    val sendtTilAttesteringAv: NavIdentBruker.Saksbehandler,
) : ErUtfylt by forrigeSteg,
    UnderBehandlingEllerTilAttestering
