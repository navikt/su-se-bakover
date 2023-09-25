package tilbakekreving.domain.vurdert

import dokument.domain.brev.Brevvalg
import no.nav.su.se.bakover.common.domain.Attesteringshistorikk
import tilbakekreving.domain.opprett.OpprettetTilbakekrevingsbehandling

sealed interface VurdertTilbakekrevingsbehandling : KanVurdere {

    val forrigeSteg: KanVurdere
    override val månedsvurderinger: Månedsvurderinger

    /**
     * Kan kun gå fra opprettet til denne, men aldri tilbake til denne igjen.
     */
    data class Påbegynt(
        override val forrigeSteg: OpprettetTilbakekrevingsbehandling,
        override val månedsvurderinger: Månedsvurderinger,
    ) : VurdertTilbakekrevingsbehandling, KanVurdere by forrigeSteg {
        override val brevvalg: Brevvalg.SaksbehandlersValg? = null
        override val attesteringer: Attesteringshistorikk = Attesteringshistorikk.empty()
    }

    data class Utfylt(
        override val forrigeSteg: Påbegynt,
        override val månedsvurderinger: Månedsvurderinger,
        override val brevvalg: Brevvalg.SaksbehandlersValg,
        override val attesteringer: Attesteringshistorikk,
    ) : VurdertTilbakekrevingsbehandling, KanVurdere by forrigeSteg
}
