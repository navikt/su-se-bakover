package no.nav.su.se.bakover.tilbakekreving.domain

import no.nav.su.se.bakover.common.tid.Tidspunkt
import java.util.UUID

data class ManuellTilbakekrevingsbehandling(
    val id: UUID,
    val sakId: UUID,
    val opprettet: Tidspunkt,
    val kravgrunnlag: Kravgrunnlag,
)
