package no.nav.su.se.bakover.domain.brev.beregning

import no.nav.su.se.bakover.common.tid.periode.Periode
import vilkår.inntekt.domain.grunnlag.Fradrag
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragstype
import kotlin.math.roundToInt

internal data class BrukerFradragBenyttetIBeregningsperiode(
    private val fradragForBeregningsperiode: List<Fradrag>,
) {
    val fradrag: List<MånedsfradragForBrev> = fradragForBeregningsperiode
        .filter { it.tilhører == FradragTilhører.BRUKER }
        .filter { it.månedsbeløp > 0 }
        .toMånedsfradragPerType()
}

internal data class EpsFradragFraSaksbehandlerIBeregningsperiode(
    private val fradragFraSaksbehandler: List<Fradrag>,
    private val beregningsperiode: Periode,
) {
    val fradrag: List<MånedsfradragForBrev> = fradragFraSaksbehandler
        .filter { it.tilhører == FradragTilhører.EPS }
        .fradragStørreEnn0IPeriode(beregningsperiode)
}

internal fun List<Fradrag>.fradragStørreEnn0IPeriode(periode: Periode) =
    this.filter { it.periode inneholder periode }
        .filter { it.månedsbeløp > 0 }
        .toMånedsfradragPerType()

internal fun List<Fradrag>.toMånedsfradragPerType(): List<MånedsfradragForBrev> =
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
            MånedsfradragForBrev(
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
    when (this) {
        Fradragstype.Alderspensjon -> "Alderspensjon"
        is Fradragstype.Annet -> "Annet"
        Fradragstype.Arbeidsavklaringspenger -> "Arbeidsavklaringspenger"
        Fradragstype.Arbeidsinntekt -> "Arbeidsinntekt"
        // jah: Selvom avkorting er historisk, har vi 4 innvilget søknadsbehandlinger med avkortingsfradrag vi må støtte.
        Fradragstype.AvkortingUtenlandsopphold -> "Avkorting på grunn av tidligere utenlandsopphold"
        Fradragstype.AvtalefestetPensjon -> "Avtalefestet pensjon (AFP)"
        Fradragstype.AvtalefestetPensjonPrivat -> "Avtalefestet pensjon privat (AFP)"
        Fradragstype.BeregnetFradragEPS -> "Utregnet fradrag for ektefelle/samboers inntekter"
        Fradragstype.BidragEtterEkteskapsloven -> "Bidrag etter ekteskapsloven"
        Fradragstype.Dagpenger -> "Dagpenger"
        Fradragstype.ForventetInntekt -> "Forventet inntekt etter uførhet"
        Fradragstype.Fosterhjemsgodtgjørelse -> "Fosterhjemgodtgjørelse"
        Fradragstype.Gjenlevendepensjon -> "Gjenlevendepensjon"
        Fradragstype.Introduksjonsstønad -> "Introduksjonsstønad"
        Fradragstype.Kapitalinntekt -> "Kapitalinntekt"
        Fradragstype.Kontantstøtte -> "Kontantstøtte"
        Fradragstype.Kvalifiseringsstønad -> "Kvalifiseringsstønad"
        Fradragstype.NAVytelserTilLivsopphold -> "NAV-ytelser til livsopphold"
        Fradragstype.OffentligPensjon -> "Offentlig pensjon"
        Fradragstype.PrivatPensjon -> "Privat pensjon"
        Fradragstype.Sosialstønad -> "Sosialstønad"
        Fradragstype.StatensLånekasse -> "Statens lånekasse"
        Fradragstype.SupplerendeStønad -> "Supplerende stønad"
        Fradragstype.Sykepenger -> "Sykepenger"
        Fradragstype.Uføretrygd -> "Uføretrygd"
        Fradragstype.UnderMinstenivå -> "Beløp under minstegrense for utbetaling"
        Fradragstype.Tiltakspenger -> "Tiltakspenger"
        Fradragstype.Ventestønad -> "Ventestønad"
    }.let { fradragsnavn ->
        if (utenlandsk) {
            "$fradragsnavn — fra utlandet"
        } else {
            fradragsnavn
        }
    }
