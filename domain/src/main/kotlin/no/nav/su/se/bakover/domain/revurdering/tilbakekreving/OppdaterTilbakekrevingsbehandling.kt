package no.nav.su.se.bakover.domain.revurdering.tilbakekreving

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.oppdrag.tilbakekrevingUnderRevurdering.IkkeAvgjort
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.domain.revurdering.UnderkjentRevurdering
import java.time.Clock
import java.util.UUID

fun Revurdering.oppdaterTilbakekrevingsbehandling(
    request: OppdaterTilbakekrevingsbehandlingRequest,
    clock: Clock,
): Either<KunneIkkeOppdatereTilbakekrevingsbehandling, Revurdering> {
    if (this !is SimulertRevurdering && this !is UnderkjentRevurdering) {
        return KunneIkkeOppdatereTilbakekrevingsbehandling.UgyldigTilstand(fra = this::class).left()
    }

    val ikkeAvgjort = IkkeAvgjort(
        id = UUID.randomUUID(),
        opprettet = Tidspunkt.now(clock),
        sakId = this.sakId,
        revurderingId = this.id,
        periode = this.periode,
    )

    val req = when (request.avgjørelse) {
        OppdaterTilbakekrevingsbehandlingRequest.Avgjørelse.TILBAKEKREV -> {
            ikkeAvgjort.tilbakekrev()
        }

        OppdaterTilbakekrevingsbehandlingRequest.Avgjørelse.IKKE_TILBAKEKREV -> {
            ikkeAvgjort.ikkeTilbakekrev()
        }
    }

    return when (this) {
        is SimulertRevurdering -> oppdaterTilbakekrevingsbehandling(req)
        is UnderkjentRevurdering -> oppdaterTilbakekrevingsbehandling(req)
        else -> throw IllegalStateException("Skal ikke kunne oppdatere tilbakekreving ved tilstander utenom Simulert & Underkjent. $id")
    }.right()
}
