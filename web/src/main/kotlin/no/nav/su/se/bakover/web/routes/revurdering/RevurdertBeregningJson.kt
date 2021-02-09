package no.nav.su.se.bakover.web.routes.revurdering

import com.fasterxml.jackson.annotation.JsonInclude
import no.nav.su.se.bakover.domain.behandling.OpprettetRevurdering
import no.nav.su.se.bakover.domain.behandling.Revurdering
import no.nav.su.se.bakover.domain.behandling.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.behandling.SimulertRevurdering
import no.nav.su.se.bakover.web.routes.behandling.BehandlingJson
import no.nav.su.se.bakover.web.routes.behandling.beregning.BeregningJson
import no.nav.su.se.bakover.web.routes.behandling.beregning.toJson
import no.nav.su.se.bakover.web.routes.behandling.toJson
import java.time.format.DateTimeFormatter

internal enum class RevurderingsStatus {
    OPPRETTET,
    SIMULERT,
    TIL_ATTESTERING,
}

internal data class RevurdertBeregningJson(
    val beregning: BeregningJson,
    val revurdert: BeregningJson
)

internal data class OpprettetRevurderingJson(
    val id: String,
    val opprettet: String,
    val tilRevurdering: BehandlingJson
) {
    @JsonInclude
    val status = RevurderingsStatus.OPPRETTET
}

internal data class SimulertRevurderingJson(
    val id: String,
    val opprettet: String,
    val tilRevurdering: BehandlingJson,
    val beregninger: RevurdertBeregningJson,
    val saksbehandler: String
) {
    @JsonInclude
    val status = RevurderingsStatus.SIMULERT
}

internal data class TilAttesteringJson(
    val id: String,
    val opprettet: String,
    val tilRevurdering: BehandlingJson,
    val beregninger: RevurdertBeregningJson,
    val saksbehandler: String
) {
    @JsonInclude
    val status = RevurderingsStatus.TIL_ATTESTERING
}

internal fun Revurdering.toJson(): Any = when (this) {
    is OpprettetRevurdering -> OpprettetRevurderingJson(
        id = id.toString(),
        opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
        tilRevurdering = tilRevurdering.toJson()
    )
    is RevurderingTilAttestering -> TilAttesteringJson(
        id = id.toString(),
        opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
        tilRevurdering = tilRevurdering.toJson(),
        beregninger = RevurdertBeregningJson(
            beregning = tilRevurdering.beregning.toJson(),
            revurdert = beregning.toJson()
        ),
        saksbehandler = saksbehandler.toString(),
    )
    is SimulertRevurdering -> SimulertRevurderingJson(
        id = id.toString(),
        opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
        tilRevurdering = tilRevurdering.toJson(),
        beregninger = RevurdertBeregningJson(
            beregning = tilRevurdering.beregning.toJson(),
            revurdert = beregning.toJson()
        ),
        saksbehandler = saksbehandler.toString(),
    )
    else -> throw NotImplementedError()
}
