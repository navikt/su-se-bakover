package no.nav.su.se.bakover.web.routes.grunnlag

import no.nav.su.se.bakover.common.extensions.avrund
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.domain.vilkår.FormueVilkår
import no.nav.su.se.bakover.domain.vilkår.Vurdering
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeFormue
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal data class FormuevilkårJson(
    val vurderinger: List<VurderingsperiodeFormueJson>,
    val resultat: FormuevilkårStatus?,
    val formuegrenser: List<FormuegrenseJson>,
)

internal data class VurderingsperiodeFormueJson(
    val id: String,
    val opprettet: String,
    val resultat: FormuevilkårStatus,
    val grunnlag: FormuegrunnlagJson,
    val periode: PeriodeJson,
)

internal fun FormueVilkår.toJson(satsFactory: SatsFactory): FormuevilkårJson {
    return FormuevilkårJson(
        vurderinger = when (this) {
            is FormueVilkår.IkkeVurdert -> emptyList()
            is FormueVilkår.Vurdert -> vurderingsperioder.map { it.toJson() }
        },
        resultat = when (this) {
            is FormueVilkår.IkkeVurdert -> null
            is FormueVilkår.Vurdert -> vurdering.toFormueStatusString()
        },
        // TODO("håndter_formue egentlig knyttet til formuegrenser")
        // TODO jah + jacob:  Denne bør flyttes til et eget endepunkt i de tilfellene vi ikke har fylt ut formuegrunnlaget/vilkåret enda.
        //  I de tilfellene vi har fylt ut formue og det har blitt lagret i databasen, bør grunnlaget innholde grensene og frontend bør bruke de derfra.
        // jah: For ufør trenger vi fra 2021-01-01 og da gjelder grunnbeløpet med virkningstidspunkt 2020-05-01 fram til og med 2021-04-30
        formuegrenser = satsFactory.formuegrenserFactory.virkningstidspunkt(mai(2020)).toJson(),
    )
}

internal fun Vurdering.toFormueStatusString() = when (this) {
    Vurdering.Avslag -> FormuevilkårStatus.VilkårIkkeOppfylt
    Vurdering.Innvilget -> FormuevilkårStatus.VilkårOppfylt
    Vurdering.Uavklart -> FormuevilkårStatus.MåInnhenteMerInformasjon
}

internal enum class FormuevilkårStatus {
    VilkårOppfylt,
    VilkårIkkeOppfylt,
    MåInnhenteMerInformasjon,
}

internal fun VurderingsperiodeFormue.toJson(): VurderingsperiodeFormueJson {
    return VurderingsperiodeFormueJson(
        id = id.toString(),
        opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
        resultat = vurdering.toFormueStatusString(),
        grunnlag = grunnlag.toJson(),
        periode = periode.toJson(),
    )
}

internal data class FormuegrenseJson(
    val gyldigFra: String,
    val beløp: Int,
)

internal fun List<Pair<LocalDate, BigDecimal>>.toJson(): List<FormuegrenseJson> {
    return this.map {
        FormuegrenseJson(
            gyldigFra = it.first.format(DateTimeFormatter.ISO_DATE),
            beløp = it.second.avrund(),
        )
    }
}
