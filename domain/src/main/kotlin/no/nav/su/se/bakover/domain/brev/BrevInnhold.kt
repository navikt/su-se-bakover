package no.nav.su.se.bakover.domain.brev

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.behandling.Satsgrunn
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn

abstract class BrevInnhold {
    fun toJson(): String = objectMapper.writeValueAsString(this)

    @get:JsonIgnore
    abstract val brevTemplate: BrevTemplate

    data class AvslagsVedtak(
        val personalia: Personalia,
        val avslagsgrunner: List<Avslagsgrunn>,
        val harEktefelle: Boolean,
        val halvGrunnbeløp: Int,
        val beregning: Beregning?
    ) : BrevInnhold() {
        @Suppress("unused")
        @JsonInclude
        val harFlereAvslagsgrunner: Boolean = avslagsgrunner.size > 1

        override val brevTemplate: BrevTemplate = BrevTemplate.AvslagsVedtak
    }

    data class InnvilgetVedtak(
        val personalia: Personalia,
        val fradato: String,
        val tildato: String,
        val sats: String,
        val satsGrunn: Satsgrunn,
        val harEktefelle: Boolean,
        val beregning: Beregning
    ) : BrevInnhold() {
        override val brevTemplate: BrevTemplate = BrevTemplate.InnvilgetVedtak
    }

    data class Personalia(
        val dato: String,
        val fødselsnummer: Fnr,
        val fornavn: String,
        val etternavn: String,
    )

    data class Beregning(
        val ytelsePerMåned: Int,
        val satsbeløpPerMåned: Double,
        val epsFribeløp: Double,
        val fradrag: Fradrag?,
    ) {
        data class Fradrag(
            val bruker: FradragForBruker,
            val eps: FradragForEps,
        )

        data class FradragForBruker(
            val fradrag: List<Månedsfradrag>,
            val sum: Double,
            val harBruktForventetInntektIStedetForArbeidsinntekt: Boolean,
        )

        data class FradragForEps(
            val fradrag: List<Månedsfradrag>,
            val sum: Double,
        )
    }

    data class Månedsfradrag(
        val type: String,
        val beløp: Double
    )
}
