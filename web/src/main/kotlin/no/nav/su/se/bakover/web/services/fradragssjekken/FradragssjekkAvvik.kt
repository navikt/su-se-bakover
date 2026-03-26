package no.nav.su.se.bakover.web.services.fradragssjekken

import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import java.math.BigDecimal
import java.math.RoundingMode

private const val BELOPS_TOLERANSE = 10.0

internal fun finnAvvikForSak(
    sjekkplan: SjekkPlan,
    eksterneOppslag: EksterneOppslag,
): Avviksvurdering {
    val avvik = sjekkplan.sjekkpunkter
        .mapNotNull { sjekkpunkt ->
            vurderAvvik(
                sjekkpunkt = sjekkpunkt,
                oppslag = eksterneOppslag.hentOppslag(sjekkpunkt),
            )
        }
        .distinctBy { it.kode to it.oppgavetekst }

    return if (avvik.isEmpty()) {
        Avviksvurdering.IngenDiff
    } else {
        Avviksvurdering.Diff(avvik)
    }
}

private fun vurderAvvik(
    sjekkpunkt: Sjekkpunkt,
    oppslag: EksterntOppslag?,
): Fradragsavvik? {
    return when (oppslag) {
        is EksterntOppslag.Funnet -> vurderFunnetOppslag(sjekkpunkt, oppslag.beløp)
        EksterntOppslag.IngenTreff -> vurderIngenTreff(sjekkpunkt)
        is EksterntOppslag.Feil,
        null,
        -> null
    }
}

private fun vurderFunnetOppslag(
    sjekkpunkt: Sjekkpunkt,
    eksterntBeløp: Double,
): Fradragsavvik? {
    val lokaltBeløp = sjekkpunkt.lokaltBeløp

    return when {
        lokaltBeløp == null -> Fradragsavvik(
            kode = OppgaveConfig.Fradragssjekk.AvvikKode.EKSTERNT_FRADRAG_MANGLER_LOKALT,
            oppgavetekst = "${sjekkpunkt.brukerType()} har ${sjekkpunkt.fradragstype} eksternt med beløp ${formatBeløp(eksterntBeløp)}, men mangler fradrag på saken.",
        )

        erSammeBeløp(lokaltBeløp, eksterntBeløp) -> null

        else -> Fradragsavvik(
            kode = OppgaveConfig.Fradragssjekk.AvvikKode.ULIKT_BELOP,
            oppgavetekst = "${sjekkpunkt.brukerType()} har ${sjekkpunkt.fradragstype} med ulikt beløp. Lokalt=${formatBeløp(lokaltBeløp)}, eksternt=${formatBeløp(eksterntBeløp)} fra ${sjekkpunkt.kilde.kildeNavn}.",
        )
    }
}

private fun vurderIngenTreff(
    sjekkpunkt: Sjekkpunkt,
): Fradragsavvik? {
    return sjekkpunkt.lokaltBeløp?.let {
        Fradragsavvik(
            kode = OppgaveConfig.Fradragssjekk.AvvikKode.LOKALT_FRADRAG_MANGLER_EKSTERNT,
            oppgavetekst = "${sjekkpunkt.brukerType()} har ${sjekkpunkt.fradragstype} lokalt med beløp ${formatBeløp(it)}, men det finnes ikke i ${sjekkpunkt.kilde.kildeNavn}.",
        )
    }
}

private fun Sjekkpunkt.brukerType(): String = when (tilhører) {
    FradragTilhører.BRUKER -> "Bruker"
    FradragTilhører.EPS -> "EPS"
}

private fun erSammeBeløp(
    lokaltBeløp: Double,
    eksterntBeløp: Double,
): Boolean {
    return kotlin.math.abs(lokaltBeløp - eksterntBeløp) < BELOPS_TOLERANSE
}

private fun formatBeløp(beløp: Double): String {
    return BigDecimal.valueOf(beløp).setScale(2, RoundingMode.HALF_UP).toPlainString()
}
