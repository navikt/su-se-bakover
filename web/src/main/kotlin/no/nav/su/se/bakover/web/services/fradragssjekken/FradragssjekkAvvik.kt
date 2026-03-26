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
    val lokaltBeløp = sjekkpunkt.lokaltBeløp ?: return Fradragsfunn.Oppgaveavvik(
        kode = OppgaveConfig.Fradragssjekk.AvvikKode.MANGLER_FRADRAG_I_SUAPP,
        oppgavetekst = "${sjekkpunkt.brukerType()} har ${sjekkpunkt.fradragstype} eksternt med beløp ${
            formatBeløp(
                eksterntBeløp,
            )
        }, men mangler fradrag på saken.",
    )

    return when (vurderBeløpsdifferanse(lokaltBeløp, eksterntBeløp)) {
        Beløpsvurdering.IngenDifferanse -> null
        is Beløpsvurdering.InsignifikantDifferanse -> Fradragsfunn.Observasjon(
            kode = Observasjonskode.INSIGNIFIKANT_BELOEPSDIFFERANSE,
            loggtekst = "${sjekkpunkt.brukerType()} har ${sjekkpunkt.fradragstype} eksternt med beløp ${formatBeløp(eksterntBeløp)}, som er mindre enn eller lik 10kr fra vårt registrerte beløp ${formatBeløp(lokaltBeløp)}.",
        )

        is Beløpsvurdering.SignifikantDifferanse -> Fradragsfunn.Oppgaveavvik(
            kode = OppgaveConfig.Fradragssjekk.AvvikKode.FRADRAG_DIFF_OVER_10KR,
            oppgavetekst = "${sjekkpunkt.brukerType()} har ${sjekkpunkt.fradragstype} eksternt med beløp ${formatBeløp(eksterntBeløp)}, som er mer enn 10kr +- enn vårt registrerte beløp ${formatBeløp(lokaltBeløp)}.",
        )

        is Beløpsvurdering.UgyldigDifferanse -> Fradragsfunn.Oppgaveavvik(
            kode = OppgaveConfig.Fradragssjekk.AvvikKode.ULIKT_BELOP,
            oppgavetekst = "${sjekkpunkt.brukerType()} har ${sjekkpunkt.fradragstype} med ugyldig beløpsdifferanse. Lokalt=${formatBeløp(lokaltBeløp)}, eksternt=${formatBeløp(eksterntBeløp)} fra ${sjekkpunkt.ytelse.ytelseNavn}.",
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

private fun vurderBeløpsdifferanse(
    lokaltBeløp: Double,
    eksterntBeløp: Double,
): Beløpsvurdering {
    val differanse = kotlin.math.abs(lokaltBeløp.minus(eksterntBeløp))

    return when {
        differanse == 0.0 -> Beløpsvurdering.IngenDifferanse
        differanse < BELOPS_TOLERANSE_I_KR -> Beløpsvurdering.InsignifikantDifferanse
        differanse >= BELOPS_TOLERANSE_I_KR -> Beløpsvurdering.SignifikantDifferanse
        else -> Beløpsvurdering.UgyldigDifferanse
    }
}

private sealed interface Beløpsvurdering {
    data object IngenDifferanse : Beløpsvurdering
    data object InsignifikantDifferanse : Beløpsvurdering
    data object SignifikantDifferanse : Beløpsvurdering
    data object UgyldigDifferanse : Beløpsvurdering
}

private fun formatBeløp(beløp: Double): String {
    return BigDecimal.valueOf(beløp).setScale(2, RoundingMode.HALF_UP).toPlainString()
}
