package no.nav.su.se.bakover.domain.regulering

import no.nav.su.se.bakover.common.person.Fnr

data class RegulerteFradragEksternKilde(
    val fnr: Fnr,
    val fradrag: List<RegulertBeløpEksternKilde>,
    val fradragEps: List<RegulertBeløpEksternKilde>,

    // Skal alltid være satt når bruker er uføre med et unntak.
    // Det finnes tilfeller hvor inntekt etter uføre er behandlet manuelt i pesys.
    // Det vil medføre at vi ikke får beløpet (denne er null selv om uføre) og at det må behandles manuelt i SU-App også.
    val inntektEtterUføre: RegulertBeløpEksternKilde? = null,
)

data class RegulertBeløpEksternKilde(

    // TODO kategori elns?
    // TODO bør være BigDecimal?

    val fnr: Fnr,
    val førRegulering: Int,
    val etterRegulering: Int,
)
