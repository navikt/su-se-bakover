package no.nav.su.se.bakover.domain.brev.beregning

import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragskategori
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragskategoriWrapper
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
            "${it.fradragskategoriWrapper}${
            it.utenlandskInntekt
                ?.let { u ->
                    "${u.valuta}${u.beløpIUtenlandskValuta}"
                }
            }"
        }
        .map { (_, fradrag) ->
            Månedsfradrag(
                type = fradrag[0]
                    .fradragskategoriWrapper
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

fun FradragskategoriWrapper.toReadableTypeName(utenlandsk: Boolean) =
    when (this.kategori) {
        Fradragskategori.Alderspensjon -> "Alderspensjon"
        Fradragskategori.Annet -> "Annet"
        Fradragskategori.Arbeidsavklaringspenger -> "Arbeidsavklaringspenger"
        Fradragskategori.Arbeidsinntekt -> "Arbeidsinntekt"
        Fradragskategori.AvkortingUtenlandsopphold -> "Avkorting på grunn av tidligere utenlandsopphold"
        Fradragskategori.AvtalefestetPensjon -> "Avtalefestet pensjon (AFP)"
        Fradragskategori.AvtalefestetPensjonPrivat -> "Avtalefestet pensjon privat (AFP)"
        Fradragskategori.BeregnetFradragEPS -> "Utregnet fradrag for ektefelle/samboers inntekter"
        Fradragskategori.BidragEtterEkteskapsloven -> "Bidrag etter ekteskapsloven"
        Fradragskategori.Dagpenger -> "Dagpenger"
        Fradragskategori.ForventetInntekt -> "Forventet inntekt etter uførhet"
        Fradragskategori.Gjenlevendepensjon -> "Gjenlevendepensjon"
        Fradragskategori.Introduksjonsstønad -> "Introduksjonsstønad"
        Fradragskategori.Kapitalinntekt -> "Kapitalinntekt"
        Fradragskategori.Kontantstøtte -> "Kontantstøtte"
        Fradragskategori.Kvalifiseringsstønad -> "Kvalifiseringsstønad"
        Fradragskategori.NAVytelserTilLivsopphold -> "NAV-ytelser til livsopphold"
        Fradragskategori.OffentligPensjon -> "Offentlig pensjon"
        Fradragskategori.PrivatPensjon -> "Privat pensjon"
        Fradragskategori.Sosialstønad -> "Sosialstønad"
        Fradragskategori.SupplerendeStønad -> "Supplerende stønad"
        Fradragskategori.Sykepenger -> "Sykepenger"
        Fradragskategori.Uføretrygd -> "uføretrygd"
        Fradragskategori.UnderMinstenivå -> "Beløp under minstegrense for utbetaling"
    }.let { fradragsnavn ->
        if (utenlandsk) {
            "$fradragsnavn — fra utlandet"
        } else {
            fradragsnavn
        }
    }
