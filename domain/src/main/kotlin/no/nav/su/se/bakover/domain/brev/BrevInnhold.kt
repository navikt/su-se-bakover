package no.nav.su.se.bakover.domain.brev

import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype

abstract class BrevInnhold {
    fun toJson() = objectMapper.writeValueAsString(this)
    abstract fun brevTemplate(): BrevTemplate

    data class AvslagsVedtak(
        val personalia: Personalia,
        val avslagsgrunner: List<Avslagsgrunn>,
        val harFlereAvslagsgrunner: Boolean = avslagsgrunner.size > 1,
        val harEktefelle: Boolean,
        val halvGrunnbeløp: Int,
        val beregning: Beregning?
    ) : BrevInnhold() {
        override fun brevTemplate(): BrevTemplate = BrevTemplate.AvslagsVedtak
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
        override fun brevTemplate(): BrevTemplate = BrevTemplate.InnvilgetVedtak
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
        val fradrag: Fradrag?
    ) {
        data class Fradrag(
            val bruker: FradragForPerson,
            val eps: FradragForPerson
        )

        data class FradragForPerson(
            val fradrag: List<Månedsfradrag>,
            val sum: Double
        )
    }

    data class Månedsfradrag(
        val type: Fradragstype,
        val beløp: Double
    )
}
