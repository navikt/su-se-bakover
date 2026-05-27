package no.nav.su.se.bakover.domain.regulering

import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.tid.periode.Periode
import java.util.UUID

data class ReguleringOppsummering(
    val saksnummer: Saksnummer,
    val behandlingsId: UUID,
    val periode: Periode,
    val reguleringstype: Reguleringstype,
    val erIverksatt: Boolean,
)

fun Regulering.toReguleringForLogResultat(): ReguleringOppsummering {
    return ReguleringOppsummering(
        saksnummer = saksnummer,
        behandlingsId = id.value,
        periode = periode,
        reguleringstype = reguleringstype,
        erIverksatt = this is IverksattRegulering,
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
