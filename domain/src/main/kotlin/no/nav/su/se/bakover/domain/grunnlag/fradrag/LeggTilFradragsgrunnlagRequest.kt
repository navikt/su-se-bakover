package no.nav.su.se.bakover.domain.grunnlag.fradrag

import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import java.util.UUID

data class LeggTilFradragsgrunnlagRequest(
    val behandlingId: UUID,
    val fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>,
)
