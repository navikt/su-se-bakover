package no.nav.su.se.bakover.domain.brev

import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.Fnr

abstract class Brevdata {
    fun toJson() = objectMapper.writeValueAsString(this)
    abstract fun pdfTemplate(): PdfTemplate
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
        override fun pdfTemplate(): PdfTemplate = PdfTemplate.AvslagVedtak
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
        override fun pdfTemplate(): PdfTemplate = PdfTemplate.InnvilgetVedtak
    }
}

sealed class PdfTemplate(
    private val templateName: String
) {
    fun name() = templateName

    object InnvilgetVedtak : PdfTemplate("vedtakInnvilgelse")
    object AvslagVedtak : PdfTemplate("vedtakAvslag")
}
