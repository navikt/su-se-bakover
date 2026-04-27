package no.nav.su.se.bakover.domain.regulering

import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.regulering.supplement.ReguleringssupplementFor
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

data class ReguleringOppsummering(
    val saksnummer: Saksnummer,
    val behandlingsId: UUID,
    val periode: Periode,
    val reguleringstype: Reguleringstype,
    val erIverksatt: Boolean,

    // TODO skal fjrnes
    val supplementBruker: ReguleringssupplementFor? = null,
    val harSupplementData: Boolean = false,

    val tidsbrukSekunder: Int,
)

fun Regulering.toReguleringForLogResultat(
    startTid: LocalDateTime,
): ReguleringOppsummering {
    return ReguleringOppsummering(
        saksnummer = saksnummer,
        behandlingsId = id.value,
        periode = periode,
        reguleringstype = reguleringstype,
        erIverksatt = this is IverksattRegulering,
        tidsbrukSekunder = Duration.between(startTid, LocalDateTime.now()).seconds.toInt(),
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
    tidsbrukSekunder = tidsbrukSekunder,
)
