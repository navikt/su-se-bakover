@file:Suppress("PackageDirectoryMismatch")
// Må ligge i samme pakke som Tilbakekrevingsbehandling (siden det er et sealed interface), men trenger ikke ligge i samme mappe.

package tilbakekreving.domain

import no.nav.su.se.bakover.common.domain.attestering.Attesteringshistorikk
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag

data class IverksattTilbakekrevingsbehandling(
    val forrigeSteg: TilbakekrevingsbehandlingTilAttestering,
    override val hendelseId: HendelseId,
    override val versjon: Hendelsesversjon,
    override val attesteringer: Attesteringshistorikk,
    override val kravgrunnlag: Kravgrunnlag,
) : ErUtfylt by forrigeSteg {
    init {
        require(attesteringer.hentSisteIverksatteAttesteringOrNull() != null) {
            "Kan ikke opprette en iverksatt tilbakekrevingsbehandling uten en iverksatt attestering"
        }
        require(!erKravgrunnlagUtdatert) {
            // Nye kravgrunnlag etter dette vil få en annen eksternVedtakId og eksternKravgrunnlagId og skal ikke knyttes til denne behandlingen.
            "En iverksatt tilbakekrevingsbehandling sitt kravgrunnlag kan ikke være utdatert"
        }
    }

    override fun erÅpen() = false
}
