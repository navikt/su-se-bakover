package no.nav.su.se.bakover.service.revurdering

import no.nav.su.se.bakover.domain.grunnlag.Formuegrunnlag
import java.util.UUID

data class LeggTilFormuegrunnlagRequest(
    val revurderingId: UUID,
    val epsFormue: Formuegrunnlag.Verdier?,
    val søkersFormue: Formuegrunnlag.Verdier,
    val begrunnelse: String?,
)
