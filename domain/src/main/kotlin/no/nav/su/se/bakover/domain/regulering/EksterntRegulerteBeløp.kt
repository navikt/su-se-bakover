package no.nav.su.se.bakover.domain.regulering

import no.nav.su.se.bakover.common.person.Fnr
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.math.BigDecimal

/**
 * Representerer eksternt regulerte beløp hentet fra eksternt system (f.eks. Pesys eller Kelvin).
 * Inneholder regulerte beløp for bruker og eventuelt ektefelle/partner (EPS),
 *
 * @property brukerFnr Fødselsnummer til brukeren/saken dette oppslagsresultatet tilhører.
 * @property beløpBruker regulert beløp for bruker
 * @property beløpEps regulert beløp for ektefelle/partner (EPS)
 * @property inntektEtterUføre Regulert beløp for inntekt etter uføre.
 *           Skal alltid være satt for uføre (aldri AP) med et unntak:
 *           Det eneste tilfelle hvor denne er null for uføre er når inntekt etter uføre er behandlet manuelt i Pesys.
 *           Da vil vi ikke får beløpet fra Pesys og det må behandles manuelt i SU-App også.
 */
data class EksterntRegulerteBeløp(
    val brukerFnr: Fnr,
    val beløpBruker: List<RegulertBeløp>,
    val beløpEps: List<RegulertBeløp>,
    val inntektEtterUføre: RegulertBeløp? = null,
)

fun EksterntRegulerteBeløp.maptoAap(): AapGrunnlagForRegulering {
    val aapGrunnlagBruker = beløpBruker.find { it.fradragstype == Fradragstype.Arbeidsavklaringspenger }?.let {
        AapGrunnlagOgBruker(
            fnr = it.fnr,
            aapGrunnlag = AapGrunnlag(
                it.grunnlagAap!!.aapFoer,
                it.grunnlagAap.aapEtter,
            ),
        )
    }

    val aapGrunnlagEps = beløpEps.find { it.fradragstype == Fradragstype.Arbeidsavklaringspenger }?.let {
        AapGrunnlagOgBruker(
            fnr = it.fnr,
            aapGrunnlag = AapGrunnlag(
                it.grunnlagAap!!.aapFoer,
                it.grunnlagAap.aapEtter,
            ),
        )
    }
    return AapGrunnlagForRegulering(
        bruker = aapGrunnlagBruker,
        eps = aapGrunnlagEps,
    )
}

/**
 * Representerer et regulert beløp for en person, med beløp før og etter regulering.
 *
 * @property førRegulering Beløpet før regulering
 * @property etterRegulering Beløpet etter regulering
 */
data class RegulertBeløp(
    val fnr: Fnr,
    val fradragstype: Fradragstype,
    val førRegulering: BigDecimal,
    val etterRegulering: BigDecimal,

    val grunnlagAap: AapGrunnlag? = null,
)

data class AapGrunnlag(
    val aapFoer: BeregnAap.AapBeregning,
    val aapEtter: BeregnAap.AapBeregning,
)

data class AapGrunnlagOgBruker(
    val fnr: Fnr,
    val aapGrunnlag: AapGrunnlag,
)

data class AapGrunnlagForRegulering(
    val bruker: AapGrunnlagOgBruker?,
    val eps: AapGrunnlagOgBruker?,
)
