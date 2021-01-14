package no.nav.su.se.bakover.domain.brev.beregning

import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype

data class BrukerFradragForBeregningsperiode(val alleFradrag: List<Fradrag>, val beregningsperiode: Periode) {
    val fradrag: List<Månedsfradrag> = alleFradrag
        .filter { it.getTilhører() == FradragTilhører.BRUKER }
        .fradragStørreEnn0IPeriode(beregningsperiode)
}

data class EpsFradragForBeregningsperiode(val alleFradrag: List<Fradrag>, val beregningsperiode: Periode) {
    val fradrag: List<Månedsfradrag> = alleFradrag
        .filter { it.getTilhører() == FradragTilhører.EPS }
        .fradragStørreEnn0IPeriode(beregningsperiode)
}

fun List<Fradrag>.fradragStørreEnn0IPeriode(periode: Periode) =
    this.filter { it.getPeriode() inneholder periode }
        .filter { it.getMånedsbeløp() > 0 }
        .toMånedsfradragPerType()

internal fun List<Fradrag>.toMånedsfradragPerType(): List<Månedsfradrag> =
    this
        .groupBy {
            "${it.getFradragstype()}${
                it.getUtenlandskInntekt()
                    ?.let { u ->
                        "${u.valuta}${u.beløpIUtenlandskValuta}"
                    }
            }"
        }
        .map { (_, fradrag) ->
            Månedsfradrag(
                type = fradrag[0]
                    .getFradragstype()
                    .toReadableTypeName(
                        utenlandsk = fradrag[0].getUtenlandskInntekt() != null
                    ),
                beløp = fradrag
                    .sumByDouble { it.getMånedsbeløp() }
                    .roundToTwoDecimals(),
                utenlandskInntekt = fradrag[0].getUtenlandskInntekt()
            )
        }
        .sortedBy { it.type }

fun Fradragstype.toReadableTypeName(utenlandsk: Boolean) =
    when (this) {
        Fradragstype.NAVytelserTilLivsopphold ->
            "NAV-ytelser til livsopphold"
        Fradragstype.Arbeidsinntekt ->
            "Arbeidsinntekt"
        Fradragstype.OffentligPensjon ->
            "Offentlig pensjon"
        Fradragstype.PrivatPensjon ->
            "Privat pensjon"
        Fradragstype.Sosialstønad ->
            "Sosialstønad"
        Fradragstype.Kontantstøtte ->
            "Kontantstøtte"
        Fradragstype.Introduksjonsstønad ->
            "Introduksjonsstønad"
        Fradragstype.Kvalifiseringsstønad ->
            "Kvalifiseringsstønad"
        Fradragstype.BidragEtterEkteskapsloven ->
            "Bidrag etter ekteskapsloven"
        Fradragstype.Kapitalinntekt ->
            "Kapitalinntekt"
        Fradragstype.ForventetInntekt ->
            "Forventet inntekt etter uførhet"
        Fradragstype.BeregnetFradragEPS ->
            "Utregnet fradrag for ektefelle/samboers inntekter"
    }.let { fradragsnavn ->
        if (utenlandsk) {
            "$fradragsnavn — fra utlandet"
        } else {
            fradragsnavn
        }
    }
