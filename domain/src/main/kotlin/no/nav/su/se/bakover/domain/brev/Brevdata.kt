package no.nav.su.se.bakover.domain.brev

import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.Fnr

abstract class Brevdata {
    fun toJson() = objectMapper.writeValueAsString(this)
    abstract fun brevtype(): Brevtype
    data class Personalia(
        val dato: String,
        val fødselsnummer: Fnr,
        val fornavn: String,
        val etternavn: String,
        val adresse: String?,
        val husnummer: String?,
        val bruksenhet: String?,
        val postnummer: String?,
        val poststed: String?,
    )

    data class AvslagsVedtak(
        val personalia: Personalia,
        val satsbeløp: Int,
        val fradragSum: Int,
        val avslagsgrunn: Avslagsgrunn,
        val halvGrunnbeløp: Int,
    ) : Brevdata() {
        override fun brevtype(): Brevtype = Brevtype.AvslagsVedtak
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
    ) : Brevdata() {
        override fun brevtype(): Brevtype = Brevtype.InnvilgetVedtak
    }
}

sealed class Brevtype(
    private val pdfTemplate: PdfTemplate
) {
    fun template() = pdfTemplate.name()

    object InnvilgetVedtak : Brevtype(pdfTemplate = PdfTemplate.InnvilgetVedtak)
    object AvslagsVedtak : Brevtype(pdfTemplate = PdfTemplate.AvslagsVedtak)
}

/**
 * 1-1 mapping to templates defined by pdf-generator.
 */
sealed class PdfTemplate(
    private val templateName: String
) {
    fun name() = templateName

    object InnvilgetVedtak : PdfTemplate("vedtakInnvilgelse")
    object AvslagsVedtak : PdfTemplate("vedtakAvslag")
}
