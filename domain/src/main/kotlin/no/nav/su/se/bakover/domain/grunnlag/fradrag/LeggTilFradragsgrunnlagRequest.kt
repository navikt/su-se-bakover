package no.nav.su.se.bakover.domain.grunnlag.fradrag

import vilkår.inntekt.domain.grunnlag.Fradragsgrunnlag
import java.util.UUID

data class LeggTilFradragsgrunnlagRequest(
    val behandlingId: UUID,
    val fradragsgrunnlag: List<Fradragsgrunnlag>,
)
