package no.nav.su.se.bakover.web.routes.grunnlag

import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vurderingsperiode
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.PeriodeJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.PeriodeJson.Companion.toJson
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal data class FormuevilkårJson(
    val vilkår: String,
    val vurderinger: List<VurderingsperiodeFormueJson>,
    val resultat: Behandlingsinformasjon.Uførhet.Status,
    val formuegrenser: List<FormuegrenseJson>,
)

internal data class VurderingsperiodeFormueJson(
    val id: String,
    val opprettet: String,
    val resultat: Behandlingsinformasjon.Uførhet.Status,
    val grunnlag: FormuegrunnlagJson?,
    val periode: PeriodeJson,
)

internal fun Vilkår.Formue.toJson(): FormuevilkårJson {
    return FormuevilkårJson(
        vilkår = vilkår.toJson(),
        vurderinger = when (this) {
            is Vilkår.Formue.IkkeVurdert -> emptyList()
            is Vilkår.Formue.Vurdert -> vurderingsperioder.map { it.toJson() }
        },
        resultat = resultat.toStatusString(),
        formuegrenser = this.formuegrenser.toJson(),
    )
}

internal fun Vurderingsperiode.Formue.toJson(): VurderingsperiodeFormueJson {
    return VurderingsperiodeFormueJson(
        id = id.toString(),
        opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
        resultat = resultat.toStatusString(),
        grunnlag = grunnlag.toJson(),
        periode = periode.toJson(),
    )
}

internal data class FormuegrenseJson(
    val gyldigFra: String,
    val beløp: Int,
)

internal fun List<Pair<LocalDate, Int>>.toJson(): List<FormuegrenseJson> {
    return this.map {
        FormuegrenseJson(
            gyldigFra = it.first.format(DateTimeFormatter.ISO_DATE),
            beløp = it.second,
        )
    }
}
