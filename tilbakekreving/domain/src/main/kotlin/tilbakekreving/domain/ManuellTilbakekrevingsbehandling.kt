package tilbakekreving.domain

import no.nav.su.se.bakover.common.tid.Tidspunkt
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import java.util.UUID

/**
 * TODO jah: Tenk gjennom navnene for tilbakekrevingsbehandling som skjer i 2 steg eller den raskere varianten som gjøres rett fra revurdering.
 *
 * Dette er behandlingen som startes manuelt av en saksbehandler etter vi har mottatt et kravgrunnlag.
 */
data class ManuellTilbakekrevingsbehandling(
    val id: UUID,
    val sakId: UUID,
    val opprettet: Tidspunkt,
    val kravgrunnlag: Kravgrunnlag,
)
