package no.nav.su.se.bakover.service.regulering

import no.nav.su.se.bakover.client.aap.MaksimumVedtakDto
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.regulering.RegulertBeløp
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.math.BigDecimal
import java.math.RoundingMode

private val stonadsdagerPerAr = BigDecimal(260)
private val manederPerAr = BigDecimal(12)

/**
 * SU bruker AAP-vedtakets dagsats som resultat-/inntektsgrunnlag ved G-regulering.
 *
 * Vi bruker ikke `beregningsgrunnlag` i fradragsberegningen, fordi SU trenger den løpende
 * ytelsen som faktisk går til fradrag. Dagsatsen forutsettes å inkludere eventuelt
 * barnetillegg/forsørgingstillegg i tråd med fagregelen.
 */
fun MaksimumVedtakDto.tilMånedsbeløpForSu(): BigDecimal {
    val dagsats = requireNotNull(dagsats) { "Kan ikke beregne AAP til SU uten dagsats" }
    return BigDecimal(dagsats)
        .multiply(stonadsdagerPerAr)
        .divide(manederPerAr, 2, RoundingMode.HALF_UP)
}

fun tilRegulertAapBeløp(
    fnr: Fnr,
    førRegulering: BigDecimal,
    etterRegulering: BigDecimal,
): RegulertBeløp {
    return RegulertBeløp(
        fnr = fnr,
        fradragstype = Fradragstype.Arbeidsavklaringspenger,
        førRegulering = førRegulering,
        etterRegulering = etterRegulering,
    )
}
