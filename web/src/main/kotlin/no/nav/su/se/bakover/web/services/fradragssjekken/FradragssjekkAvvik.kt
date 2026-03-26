package no.nav.su.se.bakover.web.services.fradragssjekken

import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import java.math.BigDecimal
import java.math.RoundingMode

private const val BELOPS_TOLERANSE_I_KR = 10.0

internal fun finnAvvikForSak(
    sjekkplan: SjekkPlan,
    oppslagsresultater: EksterneOppslagsresultater,
): Avviksvurdering {
    val avvik = sjekkplan.sjekkpunkter
        .mapNotNull { sjekkpunkt ->
            vurderAvvik(
                sjekkpunkt = sjekkpunkt,
                oppslag = oppslagsresultater.hentLagretResultatFor(sjekkpunkt),
            )
        }

    return if (avvik.isEmpty()) {
        Avviksvurdering.IngenDiff
    } else {
        Avviksvurdering.Diff(avvik)
    }
}

private fun vurderAvvik(
    sjekkpunkt: Sjekkpunkt,
    oppslag: EksterntOppslag?,
): Fradragsfunn? {
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
): Fradragsfunn? {
    val lokaltBeløp = sjekkpunkt.lokaltBeløp

    return when {
        lokaltBeløp == null -> Fradragsfunn.Oppgaveavvik(
            kode = OppgaveConfig.Fradragssjekk.AvvikKode.MANGLER_FRADRAG_I_SUAPP,
            oppgavetekst = "${sjekkpunkt.brukerType()} har ${sjekkpunkt.fradragstype} eksternt med beløp ${formatBeløp(eksterntBeløp)}, men mangler fradrag på saken.",
        )

        beløpsDifferanseErMerEnn10kr(lokaltBeløp, eksterntBeløp) -> Fradragsfunn.Oppgaveavvik(
            kode = OppgaveConfig.Fradragssjekk.AvvikKode.FRADRAG_DIFF_OVER_10KR,
            oppgavetekst = "${sjekkpunkt.brukerType()} har ${sjekkpunkt.fradragstype} eksternt med beløp ${formatBeløp(eksterntBeløp)}, som er mer enn 10kr +- enn vårt registrerte beløp ${formatBeløp(lokaltBeløp)}.",
        )
        harInsigifikantDifferanse(lokaltBeløp, eksterntBeløp) -> Fradragsfunn.Observasjon(
            kode = Observasjonskode.INSIGNIFIKANT_BELOEPSDIFFERANSE,
            loggtekst = "${sjekkpunkt.brukerType()} har ${sjekkpunkt.fradragstype} eksternt med beløp ${formatBeløp(eksterntBeløp)}, som er mindre enn 10kr +- enn vårt registrerte beløp ${formatBeløp(lokaltBeløp)}.",
        )

        else -> Fradragsfunn.Oppgaveavvik(
            kode = OppgaveConfig.Fradragssjekk.AvvikKode.ULIKT_BELOP,
            oppgavetekst = "${sjekkpunkt.brukerType()} har ${sjekkpunkt.fradragstype} med ulikt beløp. Lokalt=${formatBeløp(lokaltBeløp)}, eksternt=${formatBeløp(eksterntBeløp)} fra ${sjekkpunkt.ytelse.ytelseNavn}.",
        )
    }
}

private fun vurderIngenTreff(
    sjekkpunkt: Sjekkpunkt,
): Fradragsfunn? {
    return sjekkpunkt.lokaltBeløp?.let {
        Fradragsfunn.Oppgaveavvik(
            kode = OppgaveConfig.Fradragssjekk.AvvikKode.LOKALT_FRADRAG_MANGLER_EKSTERNT,
            oppgavetekst = "${sjekkpunkt.brukerType()} har ${sjekkpunkt.fradragstype} lokalt med beløp ${formatBeløp(it)}, men det finnes ikke i ${sjekkpunkt.ytelse.ytelseNavn}.",
        )
    }
}

private fun Sjekkpunkt.brukerType(): String = when (tilhører) {
    FradragTilhører.BRUKER -> "Bruker"
    FradragTilhører.EPS -> "EPS"
}

private fun beløpsDifferanseErMerEnn10kr(
    lokaltBeløp: Double,
    eksterntBeløp: Double,
): Boolean {
    return kotlin.math.abs(lokaltBeløp - eksterntBeløp) > BELOPS_TOLERANSE_I_KR
}

private fun harInsigifikantDifferanse(
    lokaltBeløp: Double,
    eksterntBeløp: Double,
): Boolean {
    val beløp = kotlin.math.abs(lokaltBeløp - eksterntBeløp)
    return beløp in 0.0..BELOPS_TOLERANSE_I_KR
}

private fun formatBeløp(beløp: Double): String {
    return BigDecimal.valueOf(beløp).setScale(2, RoundingMode.HALF_UP).toPlainString()
}
