package no.nav.su.se.bakover.domain.brev

import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.Fnr

abstract class BrevInnhold {
    fun toJson() = objectMapper.writeValueAsString(this)
    abstract fun brevTemplate(): BrevTemplate
    data class Personalia(
        val dato: String,
        val fødselsnummer: Fnr,
        val fornavn: String,
        val etternavn: String,
    )

    data class AvslagsVedtak(
        val personalia: Personalia,
        val satsbeløp: Int,
        val fradragSum: Int,
        val fradrag: List<FradragPerMåned>,
        val avslagsgrunner: List<Avslagsgrunn>,
        val harFlereAvslagsgrunner: Boolean = avslagsgrunner.size > 1,
        val harEktefelle: Boolean,
        val halvGrunnbeløp: Int,
    ) : BrevInnhold() {
        override fun brevTemplate(): BrevTemplate = BrevTemplate.AvslagsVedtak
    }

    data class InnvilgetVedtak(
        val personalia: Personalia,
        val satsbeløp: Int,
        val fradragSum: Int,
        val månedsbeløp: Int,
        val fradato: String,
        val tildato: String,
        val sats: String,
        val satsGrunn: Satsgrunn,
        val redusertStønadStatus: Boolean,
        val harEktefelle: Boolean,
        val fradrag: List<FradragPerMåned>,
    ) : BrevInnhold() {
        override fun brevTemplate(): BrevTemplate = BrevTemplate.InnvilgetVedtak
    }
}
