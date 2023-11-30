package no.nav.su.se.bakover.domain.grunnlag.fradrag

import no.nav.su.se.bakover.domain.grunnlag.Fradragsgrunnlag
import java.util.UUID

data class LeggTilFradragsgrunnlagRequest(
    val behandlingId: UUID,
    val fradragsgrunnlag: List<Fradragsgrunnlag>,
)
