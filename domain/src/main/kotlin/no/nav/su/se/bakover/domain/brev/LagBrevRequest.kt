package no.nav.su.se.bakover.domain.brev

import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Grunnbeløp
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

abstract class LagBrevRequest {
    abstract fun getFnr(): Fnr
    abstract fun lagBrevInnhold(personalia: BrevInnhold.Personalia): BrevInnhold

    data class AvslagsVedtak(
        private val behandling: Behandling
    ) : LagBrevRequest() {
        override fun getFnr(): Fnr = behandling.fnr
        override fun lagBrevInnhold(personalia: BrevInnhold.Personalia): BrevInnhold.AvslagsVedtak = BrevInnhold.AvslagsVedtak(
            personalia = personalia,
            satsbeløp = behandling.beregning()?.getMånedsberegninger()?.firstOrNull()?.getSatsbeløp()?.toInt()
                ?: 0, // TODO: avrunding
            fradragPerMåned = behandling.beregning()?.getFradrag()?.toFradragPerMåned() ?: emptyList(),
            // TODO: burde kanskje sende over doubles?
            fradragSum = behandling.beregning()?.getSumFradrag()?.roundToInt() ?: 0,
            avslagsgrunner = avslagsgrunnForBehandling(behandling),
            harEktefelle = behandling.behandlingsinformasjon().ektefelle != Behandlingsinformasjon.EktefellePartnerSamboer.IngenEktefelle,
            halvGrunnbeløp = Grunnbeløp.`0,5G`.fraDato(LocalDate.now()).toInt()
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
            val førsteMånedsberegning =
                behandling.beregning()!!.getMånedsberegninger().firstOrNull()!! // Støtte för variende beløp i framtiden?
            return BrevInnhold.InnvilgetVedtak(
                personalia = personalia,
                månedsbeløp = førsteMånedsberegning.getSumYtelse().toInt(), // TODO: avrunding
                fradato = behandling.beregning()!!.getPeriode().getFraOgMed().formatMonthYear(),
                tildato = behandling.beregning()!!.getPeriode().getTilOgMed().formatMonthYear(),
                sats = behandling.beregning()?.getSats().toString().toLowerCase(),
                satsbeløp = førsteMånedsberegning.getSatsbeløp().toInt(), // TODO: avrunding
                satsGrunn = behandling.behandlingsinformasjon().bosituasjon!!.getSatsgrunn()!!,
                redusertStønadStatus = behandling.beregning()?.getFradrag()?.isNotEmpty() ?: false,
                harEktefelle = behandling.behandlingsinformasjon().ektefelle != Behandlingsinformasjon.EktefellePartnerSamboer.IngenEktefelle,
                fradrag = behandling.beregning()!!.getFradrag().toFradragPerMåned(),
                // TODO: burde kanskje sende over doubles?
                fradragSum = behandling.beregning()!!.getSumFradrag().roundToInt(),
            )
        }
    }
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

data class FradragPerMåned(val type: Fradragstype, val beløp: Int)

// TODO Hente Locale fra brukerens målform
fun LocalDate.formatMonthYear(): String =
    this.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.forLanguageTag("nb-NO")))

internal fun List<Fradrag>.toFradragPerMåned(): List<FradragPerMåned> =
    this.map {
        FradragPerMåned(it.getFradragstype(), it.getFradragPerMåned().toInt())
    }
