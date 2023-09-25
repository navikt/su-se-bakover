package tilbakekreving.domain.avbrutt

import no.nav.su.se.bakover.common.domain.Avbrutt
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import tilbakekreving.domain.Tilbakekrevingsbehandling

data class AvbruttTilbakekrevingsbehandling(
    val forrigeSteg: Tilbakekrevingsbehandling,
    override val avsluttetTidspunkt: Tidspunkt,
    override val avsluttetAv: NavIdentBruker,
) : Tilbakekrevingsbehandling by forrigeSteg, Avbrutt
