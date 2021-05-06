package no.nav.su.se.bakover.domain.brev

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.behandling.Satsgrunn
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.brev.beregning.Beregningsperiode

abstract class BrevInnhold {
    fun toJson(): String = objectMapper.writeValueAsString(this)

    @get:JsonIgnore
    abstract val brevTemplate: BrevTemplate
    // TODO CHM 05.05.2021: Se på å samle mer av det som er felles for brevinnholdene, f.eks. personalia

    data class AvslagsBrevInnhold(
        val personalia: Personalia,
        val avslagsgrunner: List<Avslagsgrunn>,
        val harEktefelle: Boolean,
        val halvGrunnbeløp: Int,
        val beregningsperioder: List<Beregningsperiode>,
        val saksbehandlerNavn: String,
        val attestantNavn: String,
        val sats: String?,
        val satsGjeldendeFraDato: String?,
        val fritekst: String,
    ) : BrevInnhold() {
        @Suppress("unused")
        @JsonInclude
        val harFlereAvslagsgrunner: Boolean = avslagsgrunner.size > 1

        @Suppress("unused")
        @JsonInclude
        val avslagsparagrafer: List<Int> = avslagsgrunner.getDistinkteParagrafer()

        @JsonInclude
        val satsBeløp = beregningsperioder.firstOrNull()?.satsbeløpPerMåned

        override val brevTemplate: BrevTemplate = BrevTemplate.AvslagsVedtak
    }

    data class InnvilgetVedtak(
        val personalia: Personalia,
        val fradato: String,
        val tildato: String,
        val sats: String,
        val satsGrunn: Satsgrunn,
        val satsBeløp: Int,
        val satsGjeldendeFraDato: String,
        val harEktefelle: Boolean,
        val beregningsperioder: List<Beregningsperiode>,
        val saksbehandlerNavn: String,
        val attestantNavn: String,
        val fritekst: String,
    ) : BrevInnhold() {
        override val brevTemplate: BrevTemplate = BrevTemplate.InnvilgetVedtak

        @Suppress("unused")
        @JsonInclude
        val harFradrag: Boolean = beregningsperioder.harFradrag()
    }

    data class Opphørsvedtak(
        val personalia: Personalia,
        val avslagsgrunner: List<Avslagsgrunn>,
        val avslagsparagrafer: List<Int>,
        val sats: String,
        val satsBeløp: Int,
        val satsGjeldendeFraDato: String,
        val harEktefelle: Boolean,
        val beregningsperioder: List<Beregningsperiode>,
        val saksbehandlerNavn: String,
        val attestantNavn: String,
        val fritekst: String,
    ) : BrevInnhold() {
        override val brevTemplate: BrevTemplate = BrevTemplate.Opphørsvedtak

        @Suppress("unused")
        @JsonInclude
        val harFradrag: Boolean = beregningsperioder.harFradrag()
    }

    data class Personalia(
        val dato: String,
        val fødselsnummer: Fnr,
        val fornavn: String,
        val etternavn: String,
    )

    data class RevurderingAvInntekt(
        val personalia: Personalia,
        val saksbehandlerNavn: String,
        val attestantNavn: String,
        val beregningsperioder: List<Beregningsperiode>,
        val fritekst: String,
        val sats: Sats,
        val satsGjeldendeFraDato: String,
        val harEktefelle: Boolean,
    ) : BrevInnhold() {
        override val brevTemplate = BrevTemplate.Revurdering.Inntekt

        @Suppress("unused")
        @JsonInclude
        val satsBeløp = beregningsperioder.firstOrNull()?.satsbeløpPerMåned

        @Suppress("unused")
        @JsonInclude
        val harFradrag: Boolean = beregningsperioder.harFradrag()
    }

    data class VedtakIngenEndring(
        val personalia: Personalia,
        val saksbehandlerNavn: String,
        val attestantNavn: String,
        val beregningsperioder: List<Beregningsperiode>,
        val fritekst: String,
        val sats: Sats,
        val satsGjeldendeFraDato: String,
        val harEktefelle: Boolean,
    ) : BrevInnhold() {
        override val brevTemplate = BrevTemplate.VedtakIngenEndring

        @Suppress("unused")
        @JsonInclude
        val satsBeløp = beregningsperioder.firstOrNull()?.satsbeløpPerMåned

        @Suppress("unused")
        @JsonInclude
        val harFradrag: Boolean = beregningsperioder.harFradrag()
    }

    data class Forhåndsvarsel(
        val personalia: Personalia,
        val fritekst: String,
    ) : BrevInnhold() {
        override val brevTemplate = BrevTemplate.Forhåndsvarsel
    }
}

fun List<Beregningsperiode>.harFradrag() = this.any { it.fradrag.bruker.isNotEmpty() || it.fradrag.eps.fradrag.isNotEmpty() }
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
