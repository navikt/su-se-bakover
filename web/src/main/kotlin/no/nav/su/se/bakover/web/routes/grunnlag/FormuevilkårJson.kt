package no.nav.su.se.bakover.web.routes.grunnlag

import no.nav.su.se.bakover.common.avrund
import no.nav.su.se.bakover.common.periode.PeriodeJson
import no.nav.su.se.bakover.common.periode.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vurderingsperiode
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

internal data class FormuevilkårJson(
    val vurderinger: List<VurderingsperiodeFormueJson>,
    val resultat: Behandlingsinformasjon.Formue.Status,
    val formuegrenser: List<FormuegrenseJson>,
)

internal data class VurderingsperiodeFormueJson(
    val id: String,
    val opprettet: String,
    val resultat: Behandlingsinformasjon.Formue.Status,
    val grunnlag: FormuegrunnlagJson,
    val periode: PeriodeJson,
)

internal fun Vilkår.Formue.toJson(satsFactory: SatsFactory): FormuevilkårJson {

    return FormuevilkårJson(
        vurderinger = when (this) {
            is Vilkår.Formue.IkkeVurdert -> emptyList()
            is Vilkår.Formue.Vurdert -> vurderingsperioder.map { it.toJson() }
        },
        resultat = resultat.toFormueStatusString(),
        // TODO("håndter_formue egentlig knyttet til formuegrenser")
        // TODO jah + jacob:  Denne bør flyttes til et eget endepunkt i de tilfellene vi ikke har fylt ut formuegrunnlaget/vilkåret enda.
        //  I de tilfellene vi har fylt ut formue og det har blitt lagret i databasen, bør grunnlaget innholde grensene og frontend bør bruke de derfra.
        // jah: For ufør trenger vi fra 2021-01-01 og da gjelder grunnbeløpet med virkningstidspunkt 2020-05-01 fram til og med 2021-04-30
        formuegrenser = satsFactory.formuegrenserFactory.virkningstidspunkt(YearMonth.of(2020, 5)).toJson(),
    )
}

internal fun Resultat.toFormueStatusString() = when (this) {
    Resultat.Avslag -> Behandlingsinformasjon.Formue.Status.VilkårIkkeOppfylt
    Resultat.Innvilget -> Behandlingsinformasjon.Formue.Status.VilkårOppfylt
    Resultat.Uavklart -> Behandlingsinformasjon.Formue.Status.MåInnhenteMerInformasjon
}

internal fun Vurderingsperiode.Formue.toJson(): VurderingsperiodeFormueJson {
    return VurderingsperiodeFormueJson(
        id = id.toString(),
        opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
        resultat = resultat.toFormueStatusString(),
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
