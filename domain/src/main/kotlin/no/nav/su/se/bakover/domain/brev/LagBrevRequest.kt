package no.nav.su.se.bakover.domain.brev

import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragStrategy
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

interface LagBrevRequest {
    fun getPerson(): Person
    fun lagBrevInnhold(personalia: BrevInnhold.Personalia): BrevInnhold

    data class InnvilgetVedtak(
        private val person: Person,
        private val behandling: Behandling,
        private val saksbehandlerNavn: String,
        private val attestantNavn: String
    ) : LagBrevRequest {
        override fun getPerson(): Person = person
        override fun lagBrevInnhold(personalia: BrevInnhold.Personalia): BrevInnhold.InnvilgetVedtak {
            val beregning = behandling.beregning()!!
            return BrevInnhold.InnvilgetVedtak(
                personalia = personalia,
                fradato = beregning.getPeriode().getFraOgMed().formatMonthYear(),
                tildato = beregning.getPeriode().getTilOgMed().formatMonthYear(),
                sats = beregning.getSats().toString().toLowerCase(),
                satsGrunn = behandling.behandlingsinformasjon().bosituasjon!!.getSatsgrunn(),
                harEktefelle = behandling.behandlingsinformasjon().ektefelle != Behandlingsinformasjon.EktefellePartnerSamboer.IngenEktefelle,
                beregning = getBrevinnholdberegning(beregning),
                saksbehandlerNavn = saksbehandlerNavn,
                attestantNavn = attestantNavn
            )
        }
    }
}

fun getBrevinnholdberegning(beregning: Beregning): BrevInnhold.Beregning {
    val førsteMånedsberegning =
        beregning.getMånedsberegninger()
            .first() // Støtte for variende beløp i framtiden?

    return BrevInnhold.Beregning(
        ytelsePerMåned = førsteMånedsberegning.getSumYtelse(),
        satsbeløpPerMåned = førsteMånedsberegning.getSatsbeløp().roundToTwoDecimals(),
        epsFribeløp =
            FradragStrategy.fromName(beregning.getFradragStrategyName())
                .getEpsFribeløp(førsteMånedsberegning.getPeriode())
                .roundToTwoDecimals(),
        fradrag = when (beregning.getFradrag().isEmpty()) {
            true ->
                null
            false ->
                BrevInnhold.Beregning.Fradrag(
                    bruker =
                        førsteMånedsberegning.getFradrag()
                            .filter { it.getTilhører() == FradragTilhører.BRUKER }
                            .let {
                                BrevInnhold.Beregning.FradragForBruker(
                                    fradrag = it.toMånedsfradragPerType(),
                                    sum = it.sumByDouble { f -> f.getTotaltFradrag() }
                                        .roundToTwoDecimals(),
                                    harBruktForventetInntektIStedetForArbeidsinntekt =
                                        it.any
                                        { f -> f.getFradragstype() == Fradragstype.ForventetInntekt }
                                )
                            },
                    eps = beregning
                        .getFradrag()
                        .filter { it.getTilhører() == FradragTilhører.EPS }
                        .let {
                            BrevInnhold.Beregning.FradragForEps(
                                fradrag = it.toMånedsfradragPerType(),
                                sum = førsteMånedsberegning.getFradrag()
                                    .filter { f -> f.getTilhører() == FradragTilhører.EPS }
                                    .sumByDouble { f -> f.getTotaltFradrag() }
                                    .roundToTwoDecimals()
                            )
                        }
                )
        }
    )
}

// TODO Hente Locale fra brukerens målform
fun LocalDate.formatMonthYear(): String =
    this.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.forLanguageTag("nb-NO")))

internal fun List<Fradrag>.toMånedsfradragPerType(): List<BrevInnhold.Månedsfradrag> =
    this
        .groupBy { it.getFradragstype() }
        .map { (type, fradrag) ->
            BrevInnhold.Månedsfradrag(
                type = type.toReadableTypeName(),
                beløp = fradrag
                    .sumByDouble { it.getTotaltFradrag() }
                    .roundToTwoDecimals()
            )
        }

fun Double.roundToTwoDecimals() =
    BigDecimal(this).setScale(2, RoundingMode.HALF_UP)
        .toDouble()

fun Fradragstype.toReadableTypeName() =
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
    }
