package no.nav.su.se.bakover.domain.regulering

import no.nav.su.se.bakover.common.person.Fnr
import vilkår.inntekt.domain.grunnlag.Fradragstype

/**
 * Representerer eksternt regulerte beløp hentet fra eksternt system (f.eks. Pesys eller Kelvin).
 * Inneholder regulerte beløp for bruker og eventuelt ektefelle/partner (EPS),
 *
 * @property beløpBruker regulert beløp for bruker
 * @property beløpEps regulert beløp for ektefelle/partner (EPS)
 * @property inntektEtterUføre Regulert beløp for inntekt etter uføre.
 *           Skal alltid være satt for uføre (aldri AP) med et unntak:
 *           Det eneste tilfelle hvor denne er null for uføre er når inntekt etter uføre er behandlet manuelt i Pesys.
 *           Da vil vi ikke får beløpet fra Pesys og det må behandles manuelt i SU-App også.
 */
data class EksterntRegulerteBeløp(
    val beløpBruker: List<RegulertBeløp>, // TODO Trolig endres til å fjerne List
    val beløpEps: List<RegulertBeløp>,
    val inntektEtterUføre: RegulertBeløp? = null,
)

/**
 * Representerer et regulert beløp for en person, med beløp før og etter regulering.
 *
 * @property fnr Fødselsnummer til personen beløpet gjelder for
 * @property førRegulering Beløpet før regulering
 * @property etterRegulering Beløpet etter regulering
 */
data class RegulertBeløp(

    // TODO kategori elns?
    // TODO bør være BigDecimal?

    val fnr: Fnr,
    val fradragstype: Fradragstype,
    val førRegulering: Int,
    val etterRegulering: Int,
)
