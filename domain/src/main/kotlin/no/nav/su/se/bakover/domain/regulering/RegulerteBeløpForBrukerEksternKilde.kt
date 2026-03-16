package no.nav.su.se.bakover.domain.regulering

import no.nav.su.se.bakover.common.person.Fnr

data class RegulerteBeløpForBrukerEksternKilde(
    val fnr: Fnr,
    val fradrag: List<RegulertBeløpEksternKilde>,
    val fradragEps: List<RegulertBeløpEksternKilde>,

    // Skal alltid være satt for uføre men kun uføre.
    // Et unntak hvor denne er null for uføre er når inntekt etter uføre er behandlet manuelt i Pesys.
    // Da vil vi ikke får beløpet fra Pesys og det må behandles manuelt i SU-App også.
    val inntektEtterUføre: RegulertBeløpEksternKilde? = null,
)

data class RegulertBeløpEksternKilde(

    // TODO kategori elns?
    // TODO bør være BigDecimal?

    val fnr: Fnr,
    val førRegulering: Int,
    val etterRegulering: Int,
)
