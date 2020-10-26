package no.nav.su.se.bakover.domain.brev

import no.nav.su.se.bakover.common.ddMMyyyy
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.Fnr
import java.time.LocalDate

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

    data class TrukketSøknad private constructor(
        val personalia: Personalia,
        val datoSøknadOpprettet: String,
        val trukketDato: String
    ) : Brevdata() {
        override fun brevtype(): Brevtype = Brevtype.TrukketSøknad

        constructor(
            personalia: Personalia,
            datoSøknadOpprettet: LocalDate,
            trukketDato: LocalDate
        ) : this(personalia, datoSøknadOpprettet.ddMMyyyy(), trukketDato.ddMMyyyy())
    }
}

sealed class Brevtype(
    private val pdfTemplate: PdfTemplate
) {
    fun template() = pdfTemplate.name()

    object InnvilgetVedtak : Brevtype(pdfTemplate = PdfTemplate.InnvilgetVedtak)
    object AvslagsVedtak : Brevtype(pdfTemplate = PdfTemplate.AvslagsVedtak)
    object TrukketSøknad : Brevtype(pdfTemplate = PdfTemplate.TrukketSøknad)
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
    object TrukketSøknad : PdfTemplate("søknadTrukket")
}
