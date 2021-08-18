package no.nav.su.se.bakover.service.grunnlag

import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import java.util.UUID

// data class FradragRequest(
//     val periode: Periode,
//     val type: Fradragstype,
//     val månedsbeløp: Double,
//     val utenlandskInntekt: UtenlandskInntekt?,
//     val tilhører: FradragTilhører,
// )

data class LeggTilFradragsgrunnlagRequest(
    val behandlingId: UUID,
    val fradragsrunnlag: List<Grunnlag.Fradragsgrunnlag>,
) {
    // fun toDomain() {
    //     return List<>
    // }
}
