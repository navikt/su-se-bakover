package no.nav.su.se.bakover.domain.brev

import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Grunnbeløp
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

abstract class LagBrevRequest {
    abstract fun getFnr(): Fnr
    abstract fun lagBrevInnhold(personalia: BrevInnhold.Personalia): BrevInnhold

    data class AvslagsVedtak(
        private val behandling: Behandling
    ) : LagBrevRequest() {
        override fun getFnr(): Fnr = behandling.fnr
        override fun lagBrevInnhold(personalia: BrevInnhold.Personalia): BrevInnhold.AvslagsVedtak = BrevInnhold.AvslagsVedtak(
            personalia = personalia,
            avslagsgrunner = avslagsgrunnForBehandling(behandling),
            harEktefelle = behandling.behandlingsinformasjon().ektefelle != Behandlingsinformasjon.EktefellePartnerSamboer.IngenEktefelle,
            halvGrunnbeløp = Grunnbeløp.`0,5G`.fraDato(LocalDate.now()).toInt(),
            beregning = behandling.beregning()?.let { getBrevinnholdberegning(it) }
        )

        private fun avslagsgrunnForBehandling(behandling: Behandling): List<Avslagsgrunn> {
            val avslagsgrunner = behandling.behandlingsinformasjon().utledAvslagsgrunner().toMutableList()

            when {
                behandling.beregning() != null && behandling.beregning()!!.getSumYtelse() <= 0 -> {
                    avslagsgrunner.add(Avslagsgrunn.FOR_HØY_INNTEKT)
                }
                behandling.beregning()?.getSumYtelseErUnderMinstebeløp() == true -> {
                    avslagsgrunner.add(Avslagsgrunn.SU_UNDER_MINSTEGRENSE)
                }
            }
            return avslagsgrunner
        }
    }

    data class InnvilgetVedtak(
        private val behandling: Behandling
    ) : LagBrevRequest() {
        override fun getFnr(): Fnr = behandling.fnr
        override fun lagBrevInnhold(personalia: BrevInnhold.Personalia): BrevInnhold.InnvilgetVedtak {
            val beregning = behandling.beregning()!!
            return BrevInnhold.InnvilgetVedtak(
                personalia = personalia,
                fradato = beregning.getPeriode().getFraOgMed().formatMonthYear(),
                tildato = beregning.getPeriode().getTilOgMed().formatMonthYear(),
                sats = beregning.getSats().toString().toLowerCase(),
                satsGrunn = behandling.behandlingsinformasjon().bosituasjon!!.getSatsgrunn()!!,
                harEktefelle = behandling.behandlingsinformasjon().ektefelle != Behandlingsinformasjon.EktefellePartnerSamboer.IngenEktefelle,
                beregning = getBrevinnholdberegning(beregning)
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
                                    sum = it.sumByDouble { f -> f.getFradragPerMåned() }
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
                                    .sumByDouble { f -> f.getFradragPerMåned() }
                                    .roundToTwoDecimals()
                            )
                        }
                )
        }
    )
}

enum class Avslagsgrunn {
    UFØRHET,
    FLYKTNING,
    OPPHOLDSTILLATELSE,
    PERSONLIG_OPPMØTE,
    FORMUE,
    BOR_OG_OPPHOLDER_SEG_I_NORGE,
    FOR_HØY_INNTEKT,
    SU_UNDER_MINSTEGRENSE,
    UTENLANDSOPPHOLD_OVER_90_DAGER,
    INNLAGT_PÅ_INSTITUSJON
}

enum class Satsgrunn {
    ENSLIG,
    DELER_BOLIG_MED_VOKSNE_BARN_ELLER_ANNEN_VOKSEN,
    DELER_BOLIG_MED_EKTEMAKE_SAMBOER_UNDER_67,
    DELER_BOLIG_MED_EKTEMAKE_SAMBOER_OVER_67,
    DELER_BOLIG_MED_EKTEMAKE_SAMBOER_UNDER_67_UFØR_FLYKTNING
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
                    .sumByDouble { it.getFradragPerMåned() }
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
            "Forventet inntekt"
        Fradragstype.BeregnetFradragEPS ->
            "Utregnet fradrag for ektefelle/samboers inntekter"
    }
