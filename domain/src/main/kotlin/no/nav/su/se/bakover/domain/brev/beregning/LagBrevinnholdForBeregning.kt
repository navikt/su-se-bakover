package no.nav.su.se.bakover.domain.brev.beregning

import no.nav.su.se.bakover.domain.beregning.GrupperEkvivalenteMånedsberegninger
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
    internal val brevInnhold: List<Beregningsperiode> =
        GrupperEkvivalenteMånedsberegninger(beregning.getMånedsberegninger()).grupper.map { gruppertMånedsberegning ->
            Beregningsperiode(
                // TODO mangler totalt beløp
                // TODO filtrer vekk 0 beløp
                // TODO ikke vis eps fradrag som er under fribeløp
                // TODO eps firbeløp ikke safe vel?
                periode = gruppertMånedsberegning.getPeriode(),
                ytelsePerMåned = gruppertMånedsberegning.getSumYtelse(),
                satsbeløpPerMåned = gruppertMånedsberegning.getSatsbeløp().roundToInt(),
                epsFribeløp = FradragStrategy.fromName(beregning.getFradragStrategyName())
                    .getEpsFribeløp(gruppertMånedsberegning.getPeriode()).let {
                        it / gruppertMånedsberegning.getPeriode().getAntallMåneder()
                    }.roundToTwoDecimals(),
                fradrag = when (beregning.getFradrag().isEmpty()) {
                    true -> null
                    false -> Fradrag(
                        bruker = gruppertMånedsberegning.getFradrag()
                            .filter { it.getTilhører() == FradragTilhører.BRUKER }
                            .let {
                                FradragForBruker(
                                    fradrag = it.toMånedsfradragPerType(),
                                    sum = it.sumByDouble { f -> f.getMånedsbeløp() }.roundToTwoDecimals()
                                )
                            },
                        eps = beregning.getFradrag()
                            .filter { it.getTilhører() == FradragTilhører.EPS }
                            .filter { it.getPeriode() inneholder gruppertMånedsberegning.getPeriode() }
                            .let {
                                FradragForEps(
                                    fradrag = it.toMånedsfradragPerType(),
                                    sum = it.sumByDouble { f -> f.getMånedsbeløp() }.roundToTwoDecimals()
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
