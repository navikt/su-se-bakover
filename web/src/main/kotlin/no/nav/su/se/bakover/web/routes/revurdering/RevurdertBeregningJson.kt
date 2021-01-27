package no.nav.su.se.bakover.web.routes.revurdering

import no.nav.su.se.bakover.domain.behandling.OpprettetRevurdering
import no.nav.su.se.bakover.domain.behandling.Revurdering
import no.nav.su.se.bakover.domain.behandling.RevurderingsStatus
import no.nav.su.se.bakover.domain.behandling.SimulertRevurdering
import no.nav.su.se.bakover.domain.behandling.TilAttesteringRevurdering
import no.nav.su.se.bakover.web.routes.behandling.BehandlingJson
import no.nav.su.se.bakover.web.routes.behandling.beregning.BeregningJson
import no.nav.su.se.bakover.web.routes.behandling.beregning.toJson
import no.nav.su.se.bakover.web.routes.behandling.toJson
import java.time.format.DateTimeFormatter

internal data class RevurdertBeregningJson(
    val beregning: BeregningJson,
    val revurdert: BeregningJson
)

internal data class OpprettetRevurderingJson(
    val id: String,
    val status: RevurderingsStatus,
    val opprettet: String,
    val tilRevurdering: BehandlingJson
)

internal data class TilAttesteringJson(
    val id: String,
    val status: RevurderingsStatus,
    val opprettet: String,
    val tilRevurdering: BehandlingJson,
    val beregninger: RevurdertBeregningJson,
    val saksbehandler: String
)

internal data class SimulertRevurderingJson(
    val id: String,
    val status: RevurderingsStatus,
    val opprettet: String,
    val tilRevurdering: BehandlingJson,
    val beregninger: RevurdertBeregningJson,
    val saksbehandler: String
)

internal fun Revurdering.toJson(): Any = when (this) {
    is OpprettetRevurdering -> OpprettetRevurderingJson(
        id = id.toString(),
        status = status,
        opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
        tilRevurdering = tilRevurdering.toJson()
    )
    is TilAttesteringRevurdering -> TilAttesteringJson(
        id = id.toString(),
        status = status,
        opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
        tilRevurdering = tilRevurdering.toJson(),
        beregninger = RevurdertBeregningJson(
            beregning = tilRevurdering.beregning()!!.toJson(),
            revurdert = beregning.toJson()
        ),
        saksbehandler = saksbehandler.toString(),
    )
    is SimulertRevurdering -> SimulertRevurderingJson(
        id = id.toString(),
        status = status,
        opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
        tilRevurdering = tilRevurdering.toJson(),
        beregninger = RevurdertBeregningJson(
            beregning = tilRevurdering.beregning()!!.toJson(),
            revurdert = beregning.toJson()
        ),
        saksbehandler = saksbehandler.toString(),
    )
    else -> throw NotImplementedError()
}
