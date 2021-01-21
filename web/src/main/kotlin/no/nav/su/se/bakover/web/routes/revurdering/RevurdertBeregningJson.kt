package no.nav.su.se.bakover.web.routes.revurdering

import no.nav.su.se.bakover.domain.behandling.OpprettetRevurdering
import no.nav.su.se.bakover.domain.behandling.Revurdering
import no.nav.su.se.bakover.service.revurdering.RevurdertBeregning
import no.nav.su.se.bakover.web.routes.behandling.BehandlingJson
import no.nav.su.se.bakover.web.routes.behandling.beregning.BeregningJson
import no.nav.su.se.bakover.web.routes.behandling.beregning.toJson
import no.nav.su.se.bakover.web.routes.behandling.toJson
import java.time.format.DateTimeFormatter

internal data class RevurdertBeregningJson(
    val beregning: BeregningJson,
    val revurdert: BeregningJson
)

internal fun RevurdertBeregning.toJson() = RevurdertBeregningJson(
    beregning = beregning.toJson(),
    revurdert = revurdert.toJson()
)

internal data class OpprettetRevurderingJson(
    val id: String,
    val opprettet: String,
    val behandling: BehandlingJson
)

internal fun Revurdering.toJson() = when (this) {
    is OpprettetRevurdering -> OpprettetRevurderingJson(
        id = id.toString(),
        opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
        behandling = tilRevurdering.toJson()
    )
    else -> throw NotImplementedError()
}
