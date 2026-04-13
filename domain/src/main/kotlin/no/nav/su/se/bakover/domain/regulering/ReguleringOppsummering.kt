package no.nav.su.se.bakover.domain.regulering

import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.regulering.supplement.ReguleringssupplementFor
import java.util.UUID

data class ReguleringOppsummering(
    val saksnummer: Saksnummer,
    val behandlingsId: UUID,
    val periode: Periode,
    val reguleringstype: Reguleringstype,
    val erIverksatt: Boolean,

    // TODO skal fjrnes
    val supplementBruker: ReguleringssupplementFor?,
    val supplementEps: List<ReguleringssupplementFor>,
) {
    val harSupplementData: Boolean = supplementBruker != null || supplementEps.isNotEmpty()
}

fun Regulering.toReguleringForLogResultat(): ReguleringOppsummering {
    return ReguleringOppsummering(
        saksnummer = saksnummer,
        behandlingsId = id.value,
        periode = periode,
        reguleringstype = reguleringstype,
        erIverksatt = this is IverksattRegulering,
        // TODO AUTO-REG-26 - erstatte med ny EksterntRegulerteBeløp - hvis denne klassen fortsatt skal benyttes
        supplementBruker = null,
        supplementEps = emptyList(),
    )
}

fun ReguleringOppsummering.toResultat(
    beskrivelse: String,
    utfall: Reguleringsresultat.Utfall,
) = Reguleringsresultat(
    saksnummer = saksnummer,
    behandlingsId = behandlingsId,
    utfall = utfall,
    beskrivelse = beskrivelse,
)
