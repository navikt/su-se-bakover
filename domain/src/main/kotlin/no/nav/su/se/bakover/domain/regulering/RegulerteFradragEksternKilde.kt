package no.nav.su.se.bakover.domain.regulering

import no.nav.su.se.bakover.common.person.Fnr

data class SakerMedRegulerteFradragEksternKilde(
    val regulerteFradragEksternKilde: List<RegulerteFradragEksternKilde>,
)

data class RegulerteFradragEksternKilde(
    val bruker: RegulertFradragEksternKilde,
    val forEps: List<RegulertFradragEksternKilde>, // TODO AUTO-REG-26 hvordan håndteres flere eps over tid? en eps frem til mai og en fra mai?
)

data class RegulertFradragEksternKilde(

    // TODO periode for eps over tid?
    // TODO kategori elns?
    // TODO bør være BigDecimal?

    val fnr: Fnr,
    val førRegulering: Int,
    val etterRegulering: Int,

)
