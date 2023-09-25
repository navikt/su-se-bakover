@file:Suppress("PackageDirectoryMismatch")
// Må ligge i samme pakke som Tilbakekrevingsbehandling (siden det er et sealed interface), men trenger ikke ligge i samme mappe.

package tilbakekreving.domain

import dokument.domain.brev.Brevvalg
import no.nav.su.se.bakover.common.domain.Attesteringshistorikk
import tilbakekreving.domain.vurdert.Månedsvurderinger

sealed interface VurdertTilbakekrevingsbehandling : KanVurdere {

    val forrigeSteg: KanVurdere
    override val månedsvurderinger: Månedsvurderinger

    /**
     * Kan kun gå fra opprettet til denne, men aldri tilbake til denne igjen.
     */
    data class Påbegynt(
        override val forrigeSteg: KanVurdere,
        override val månedsvurderinger: Månedsvurderinger,
    ) : VurdertTilbakekrevingsbehandling, KanVurdere by forrigeSteg {
        override val brevvalg: Brevvalg.SaksbehandlersValg? = null
        override val attesteringer: Attesteringshistorikk = Attesteringshistorikk.empty()
    }

    data class Utfylt(
        override val forrigeSteg: VurdertTilbakekrevingsbehandling,
        override val månedsvurderinger: Månedsvurderinger,
        override val brevvalg: Brevvalg.SaksbehandlersValg,
        override val attesteringer: Attesteringshistorikk,
    ) : VurdertTilbakekrevingsbehandling, KanVurdere by forrigeSteg {
        constructor(
            forrigeSteg: Utfylt,
            månedsvurderinger: Månedsvurderinger,
        ) : this(
            forrigeSteg = forrigeSteg,
            månedsvurderinger = månedsvurderinger,
            brevvalg = forrigeSteg.brevvalg,
            attesteringer = forrigeSteg.attesteringer,
        )
    }
}
