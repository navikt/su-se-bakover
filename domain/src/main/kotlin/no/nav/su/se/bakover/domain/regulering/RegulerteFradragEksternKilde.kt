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

    val manueltIeu: Boolean = false,
)
