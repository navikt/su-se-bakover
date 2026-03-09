package no.nav.su.se.bakover.domain.regulering

import no.nav.su.se.bakover.common.person.Fnr

data class RegulerteFradragEksternKilde(
    val bruker: RegulertFradragEksternKilde, // TODO må bli list fordi bruker kan også ha flere..
    val forEps: List<RegulertFradragEksternKilde>,
)

data class RegulertFradragEksternKilde(

    // TODO periode for eps over tid?
    // TODO kategori elns?
    // TODO bør være BigDecimal?

    val fnr: Fnr,
    val førRegulering: Int,
    val etterRegulering: Int,

)
