package no.nav.su.se.bakover.web.routes.revurdering

import no.nav.su.se.bakover.service.revurdering.RevurdertBeregning
import no.nav.su.se.bakover.web.routes.behandling.BeregningJson
import no.nav.su.se.bakover.web.routes.behandling.toJson

internal data class RevurdertBeregningJson(
    val beregning: BeregningJson,
    val revurdert: BeregningJson
)

internal fun RevurdertBeregning.toJson() = RevurdertBeregningJson(
    beregning = beregning.toJson(),
    revurdert = revurdert.toJson()
)


