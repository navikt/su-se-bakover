package no.nav.su.se.bakover.domain.brev

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.behandling.Satsgrunn
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import no.nav.su.se.bakover.domain.brev.beregning.Beregningsperiode

abstract class BrevInnhold {
    fun toJson(): String = objectMapper.writeValueAsString(this)

    @get:JsonIgnore
    abstract val brevTemplate: BrevTemplate

    data class AvslagsBrevInnhold(
        val personalia: Personalia,
        val avslagsgrunner: List<Avslagsgrunn>,
        val harEktefelle: Boolean,
        val halvGrunnbeløp: Int,
        val beregningsperioder: List<Beregningsperiode>,
        val saksbehandlerNavn: String,
        val attestantNavn: String
    ) : BrevInnhold() {
        @Suppress("unused")
        @JsonInclude
        val harFlereAvslagsgrunner: Boolean = avslagsgrunner.size > 1

        @Suppress("unused")
        @JsonInclude
        val avslagsparagrafer: List<Int> = avslagsgrunner.getDistinkteParagrafer()

        override val brevTemplate: BrevTemplate = BrevTemplate.AvslagsVedtak
    }

    data class InnvilgetVedtak(
        val personalia: Personalia,
        val fradato: String,
        val tildato: String,
        val sats: String,
        val satsGrunn: Satsgrunn,
        val harEktefelle: Boolean,
        val beregningsperioder: List<Beregningsperiode>,
        val saksbehandlerNavn: String,
        val attestantNavn: String
    ) : BrevInnhold() {
        override val brevTemplate: BrevTemplate = BrevTemplate.InnvilgetVedtak

        @Suppress("unused")
        @JsonInclude
        val totalSumForBeregningsperioder: Int = beregningsperioder.sumBy { it.periode.getAntallMåneder() * it.ytelsePerMåned }
    }

    data class Personalia(
        val dato: String,
        val fødselsnummer: Fnr,
        val fornavn: String,
        val etternavn: String,
    )
}

fun List<Avslagsgrunn>.getDistinkteParagrafer() = this.map { it.getParagrafer() }.flatten().distinct().sorted()
fun Avslagsgrunn.getParagrafer() = when (this) {
    Avslagsgrunn.UFØRHET -> listOf(1, 2)
    Avslagsgrunn.FLYKTNING -> listOf(1, 2)
    Avslagsgrunn.OPPHOLDSTILLATELSE -> listOf(1, 2)
    Avslagsgrunn.PERSONLIG_OPPMØTE -> listOf(17)
    Avslagsgrunn.FORMUE -> listOf(8)
    Avslagsgrunn.BOR_OG_OPPHOLDER_SEG_I_NORGE -> listOf(1, 2, 3, 4)
    Avslagsgrunn.FOR_HØY_INNTEKT -> listOf(5, 6, 7)
    Avslagsgrunn.SU_UNDER_MINSTEGRENSE -> listOf(5, 6, 9)
    Avslagsgrunn.UTENLANDSOPPHOLD_OVER_90_DAGER -> listOf(1, 2, 4)
    Avslagsgrunn.INNLAGT_PÅ_INSTITUSJON -> listOf(12)
}
