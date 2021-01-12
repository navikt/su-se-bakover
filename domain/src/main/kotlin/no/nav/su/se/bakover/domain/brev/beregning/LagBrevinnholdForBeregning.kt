package no.nav.su.se.bakover.domain.brev.beregning

import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragStrategy
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.roundToInt
import no.nav.su.se.bakover.domain.beregning.Beregning as FaktiskBeregning

data class LagBrevinnholdForBeregning(
    private val beregning: FaktiskBeregning
) {
    internal fun get(): Beregning {
        val førsteMånedsberegning = beregning.getMånedsberegninger().first() // Støtte for variende beløp i framtiden?

        return Beregning(
            ytelsePerMåned = førsteMånedsberegning.getSumYtelse(),
            satsbeløpPerMåned = førsteMånedsberegning.getSatsbeløp().roundToInt(),
            epsFribeløp =
            FradragStrategy.fromName(beregning.getFradragStrategyName())
                .getEpsFribeløp(førsteMånedsberegning.getPeriode())
                .roundToTwoDecimals(),
            fradrag = when (beregning.getFradrag().isEmpty()) {
                true ->
                    null
                false ->
                    Fradrag(
                        bruker =
                        førsteMånedsberegning.getFradrag()
                            .filter { it.getTilhører() == FradragTilhører.BRUKER }
                            .let {
                                FradragForBruker(
                                    fradrag = it.toMånedsfradragPerType(),
                                    sum = it.sumByDouble { f -> f.getMånedsbeløp() }
                                        .roundToTwoDecimals(),
                                    harBruktForventetInntektIStedetForArbeidsinntekt = it
                                        .any { f -> f.getFradragstype() == Fradragstype.ForventetInntekt }
                                )
                            },
                        eps = beregning
                            .getFradrag()
                            .filter { it.getTilhører() == FradragTilhører.EPS }
                            .let {
                                FradragForEps(
                                    fradrag = it.toMånedsfradragPerType(),
                                    sum = førsteMånedsberegning.getFradrag()
                                        .filter { f -> f.getTilhører() == FradragTilhører.EPS }
                                        .sumByDouble { f -> f.getMånedsbeløp() }
                                        .roundToTwoDecimals()
                                )
                            }
                    )
            }
        )
    }
}

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

fun Double.roundToTwoDecimals() =
    BigDecimal(this).setScale(2, RoundingMode.HALF_UP)
        .toDouble()
