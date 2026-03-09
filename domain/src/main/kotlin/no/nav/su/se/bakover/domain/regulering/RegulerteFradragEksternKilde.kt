package no.nav.su.se.bakover.domain.regulering

import no.nav.su.se.bakover.common.person.Fnr

data class RegulerteFradragEksternKilde(
    val bruker: RegulertFradragEksternKilde, // TODO må bli list fordi bruker kan også ha flere..
    val forEps: List<RegulertFradragEksternKilde>,
)

data class RegulertFradragEksternKilde(

    // TODO kategori elns?
    // TODO bør være BigDecimal?

    val fnr: Fnr,
    // TODO er denne nødvendig her? behovet for dette beløpet blir jo dekt av feiltype FeilMedRegulertFradrag.GrunnbeløpFraPesysUliktForventetGammelt
    val førRegulering: Int,
    val etterRegulering: Int,

)
