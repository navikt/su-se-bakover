package no.nav.su.se.bakover.domain.regulering

import no.nav.su.se.bakover.common.person.Fnr

data class RegulerteFradragEksternKilde(
    val fnr: Fnr,
    val bruker: List<RegulertFradragEksternKilde>,
    val forEps: List<RegulertFradragEksternKilde>,
)

data class RegulertFradragEksternKilde(

    // TODO kategori elns?
    // TODO bør være BigDecimal?

    val fnr: Fnr,
    val førRegulering: Int,
    val etterRegulering: Int,

    // Det finnes tilfeller hvor inntekt etter uføre er behandlet manuelt i pesys.
    // Det vil medføre at vi ikke får beløpet og at det må behandles manuelt i SU-App også
    val manueltIeu: Boolean = false,
)
