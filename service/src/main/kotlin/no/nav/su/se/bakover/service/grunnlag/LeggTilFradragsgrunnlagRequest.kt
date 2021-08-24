package no.nav.su.se.bakover.service.grunnlag

import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import java.util.UUID

data class LeggTilFradragsgrunnlagRequest(
    val behandlingId: UUID,
    val fradragsrunnlag: List<Grunnlag.Fradragsgrunnlag>,
)
