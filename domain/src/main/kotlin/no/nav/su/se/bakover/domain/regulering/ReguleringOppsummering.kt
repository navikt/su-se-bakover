package no.nav.su.se.bakover.domain.regulering

import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.regulering.supplement.ReguleringssupplementFor

data class ReguleringOppsummering(
    val saksnummer: Saksnummer,
    val periode: Periode,
    val reguleringstype: Reguleringstype,
    val erIverksatt: Boolean,
    val supplementBruker: ReguleringssupplementFor?,
    val supplementEps: List<ReguleringssupplementFor>,
) {
    val harSupplementData: Boolean = supplementBruker != null || supplementEps.isNotEmpty()
}

fun Regulering.toReguleringForLogResultat(): ReguleringOppsummering {
    return ReguleringOppsummering(
        saksnummer = saksnummer,
        periode = periode,
        reguleringstype = reguleringstype,
        erIverksatt = this is IverksattRegulering,
        // TODO AUTO-REG-26 - erstatte med ny EksterntRegulerteBeløp - hvis denne klassen fortsatt skal benyttes
        supplementBruker = null,
        supplementEps = emptyList(),
    )
}
