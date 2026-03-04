package common.presentation.grunnlag

import no.nav.su.se.bakover.common.infrastructure.PeriodeJson
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson.Companion.toJson
import vilkår.uføre.domain.Uføregrunnlag

data class UføregrunnlagJson(
    val id: String,
    val opprettet: String,
    val periode: PeriodeJson,
    val uføregrad: Int,
    val forventetInntekt: Int,
)

fun Uføregrunnlag.toJson() = UføregrunnlagJson(
    id = this.id.toString(),
    opprettet = this.opprettet.toString(),
    periode = this.periode.toJson(),
    uføregrad = this.uføregrad.value,
    forventetInntekt = this.forventetInntekt,
)

fun List<Uføregrunnlag>.toJson() = this.map {
    it.toJson()
}
