package no.nav.su.se.bakover.domain.brev

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.behandling.Satsgrunn
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn.Companion.getDistinkteParagrafer
import no.nav.su.se.bakover.domain.behandling.avslag.Opphørsgrunn
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
        val forventetInntektStørreEnn0: Boolean,
    ) : BrevInnhold() {
        @Suppress("unused")
        @JsonInclude
        val harFlereAvslagsgrunner: Boolean = avslagsgrunner.size > 1

        @Suppress("unused")
        @JsonInclude
        val avslagsparagrafer: List<Int> = avslagsgrunner.getDistinkteParagrafer()

        @JsonInclude
        val satsBeløp = beregningsperioder.lastOrNull()?.satsbeløpPerMåned

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
        val forventetInntektStørreEnn0: Boolean,
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
        val opphørsgrunner: List<Opphørsgrunn>,
        val avslagsparagrafer: List<Int>,
        val sats: String,
        val satsBeløp: Int,
        val satsGjeldendeFraDato: String,
        val harEktefelle: Boolean,
        val beregningsperioder: List<Beregningsperiode>,
        val saksbehandlerNavn: String,
        val attestantNavn: String,
        val fritekst: String,
        val forventetInntektStørreEnn0: Boolean,
        val halvGrunnbeløp: Int?
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
        val forventetInntektStørreEnn0: Boolean,
    ) : BrevInnhold() {
        override val brevTemplate = BrevTemplate.Revurdering.Inntekt

        @Suppress("unused")
        @JsonInclude
        val satsBeløp = beregningsperioder.lastOrNull()?.satsbeløpPerMåned

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
        val forventetInntektStørreEnn0: Boolean,
        val gjeldendeMånedsutbetaling: Int,
    ) : BrevInnhold() {
        override val brevTemplate = BrevTemplate.VedtakIngenEndring

        @Suppress("unused")
        @JsonInclude
        val satsBeløp = beregningsperioder.lastOrNull()?.satsbeløpPerMåned

        @Suppress("unused")
        @JsonInclude
        val harFradrag: Boolean = beregningsperioder.harFradrag()
    }

    data class Forhåndsvarsel(
        val personalia: Personalia,
        val saksbehandlerNavn: String,
        val fritekst: String,
    ) : BrevInnhold() {
        override val brevTemplate = BrevTemplate.Forhåndsvarsel
    }

    /**
     * Brev for når en revurdering er forhåndsvarslet
     * hvis revurderingen ikke er forhåndsvarslet, er det ikke noe brev.
     */
    data class AvsluttRevurdering(
        val personalia: Personalia,
        val saksbehandlerNavn: String,
        val fritekst: String?,
    ) : BrevInnhold() {
        override val brevTemplate = BrevTemplate.Revurdering.AvsluttRevurdering
    }

    data class InnkallingTilKontrollsamtale(
        val personalia: Personalia,
        val saksbehandlerNavn: String,
    ) : BrevInnhold() {
        override val brevTemplate = BrevTemplate.InnkallingTilKontrollsamtale
    }
}

fun List<Beregningsperiode>.harFradrag() = this.any { it.fradrag.bruker.isNotEmpty() || it.fradrag.eps.fradrag.isNotEmpty() }
