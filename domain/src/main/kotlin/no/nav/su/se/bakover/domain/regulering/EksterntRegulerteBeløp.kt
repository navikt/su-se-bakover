package no.nav.su.se.bakover.domain.regulering

import no.nav.su.se.bakover.common.person.Fnr

// TODO javadoc  - AI hjelp!
data class EksterntRegulerteBeløp(
    val beløpBruker: List<RegulertBeløp>, // TODO Trolig endres til å fjerne List
    val beløpEps: List<RegulertBeløp>,

    // Skal alltid være satt for uføre men kun uføre.
    // Et unntak hvor denne er null for uføre er når inntekt etter uføre er behandlet manuelt i Pesys.
    // Da vil vi ikke får beløpet fra Pesys og det må behandles manuelt i SU-App også.
    val inntektEtterUføre: RegulertBeløp? = null,
)

data class RegulertBeløp(

    // TODO kategori elns?
    // TODO bør være BigDecimal?

    val fnr: Fnr,
    val førRegulering: Int,
    val etterRegulering: Int,
)
