package no.nav.su.se.bakover.domain.brev.beregning

import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.fradrag.F
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import kotlin.math.roundToInt

internal data class BrukerFradragBenyttetIBeregningsperiode(
    private val fradragForBeregningsperiode: List<Fradrag>,
) {
    val fradrag: List<Månedsfradrag> = fradragForBeregningsperiode
        .filter { it.tilhører == FradragTilhører.BRUKER }
        .filter { it.månedsbeløp > 0 }
        .toMånedsfradragPerType()
}

internal data class EpsFradragFraSaksbehandlerIBeregningsperiode(
    private val fradragFraSaksbehandler: List<Fradrag>,
    private val beregningsperiode: Periode,
) {
    val fradrag: List<Månedsfradrag> = fradragFraSaksbehandler
        .filter { it.tilhører == FradragTilhører.EPS }
        .fradragStørreEnn0IPeriode(beregningsperiode)
}

internal fun List<Fradrag>.fradragStørreEnn0IPeriode(periode: Periode) =
    this.filter { it.periode inneholder periode }
        .filter { it.månedsbeløp > 0 }
        .toMånedsfradragPerType()

internal fun List<Fradrag>.toMånedsfradragPerType(): List<Månedsfradrag> =
    this
        .groupBy {
            "${it.fradragstype}${
            it.utenlandskInntekt
                ?.let { u ->
                    "${u.valuta}${u.beløpIUtenlandskValuta}"
                }
            }"
        }
        .map { (_, fradrag) ->
            Månedsfradrag(
                type = fradrag[0]
                    .fradragstype
                    .toReadableTypeName(
                        utenlandsk = fradrag[0].utenlandskInntekt != null,
                    ),
                beløp = fradrag
                    .sumOf { it.månedsbeløp }
                    .roundToInt(),
                utenlandskInntekt = fradrag[0].utenlandskInntekt,
            )
        }
        .sortedBy { it.type }

fun Fradragstype.toReadableTypeName(utenlandsk: Boolean) =
    when (this.type) {
        F.Alderspensjon -> "Alderspensjon"
        F.Annet -> "Annet"
        F.Arbeidsavklaringspenger -> "Arbeidsavklaringspenger"
        F.Arbeidsinntekt -> "Arbeidsinntekt"
        F.AvkortingUtenlandsopphold -> "Avkorting på grunn av tidligere utenlandsopphold"
        F.AvtalefestetPensjon -> "Avtalefestet pensjon (AFP)"
        F.AvtalefestetPensjonPrivat -> "Avtalefestet pensjon privat (AFP)"
        F.BeregnetFradragEPS -> "Utregnet fradrag for ektefelle/samboers inntekter"
        F.BidragEtterEkteskapsloven -> "Bidrag etter ekteskapsloven"
        F.Dagpenger -> "Dagpenger"
        F.ForventetInntekt -> "Forventet inntekt etter uførhet"
        F.Gjenlevendepensjon -> "Gjenlevendepensjon"
        F.Introduksjonsstønad -> "Introduksjonsstønad"
        F.Kapitalinntekt -> "Kapitalinntekt"
        F.Kontantstøtte -> "Kontantstøtte"
        F.Kvalifiseringsstønad -> "Kvalifiseringsstønad"
        F.NAVytelserTilLivsopphold -> "NAV-ytelser til livsopphold"
        F.OffentligPensjon -> "Offentlig pensjon"
        F.PrivatPensjon -> "Privat pensjon"
        F.Sosialstønad -> "Sosialstønad"
        F.SupplerendeStønad -> "Supplerende stønad"
        F.Sykepenger -> "Sykepenger"
        F.Uføretrygd -> "uføretrygd"
        F.UnderMinstenivå -> "Beløp under minstegrense for utbetaling"
    }.let { fradragsnavn ->
        if (utenlandsk) {
            "$fradragsnavn — fra utlandet"
        } else {
            fradragsnavn
        }
    }
