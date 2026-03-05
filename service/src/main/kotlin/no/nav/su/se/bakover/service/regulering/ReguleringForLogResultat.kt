package no.nav.su.se.bakover.service.regulering

import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.domain.regulering.Regulering
import no.nav.su.se.bakover.domain.regulering.Reguleringstype
import no.nav.su.se.bakover.domain.regulering.supplement.ReguleringssupplementFor

internal data class ReguleringForLogResultat(
    val saksnummer: Saksnummer,
    val reguleringstype: Reguleringstype,
    val supplementBruker: ReguleringssupplementFor?,
    val supplementEps: List<ReguleringssupplementFor>,
) {
    val harSupplementData: Boolean = supplementBruker != null || supplementEps.isNotEmpty()
}

internal fun Regulering.toReguleringForLogResultat(): ReguleringForLogResultat {
    return ReguleringForLogResultat(
        saksnummer = saksnummer,
        reguleringstype = reguleringstype,
        supplementBruker = eksternSupplementRegulering?.bruker,
        supplementEps = eksternSupplementRegulering?.eps ?: emptyList(),
    )
}
