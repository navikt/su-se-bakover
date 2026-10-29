package dokument.domain.generer

/**
 * @param tittel - overrideable tittel for dokumentet
 */
data class SkattegrunnlagPdfTemplate(val tittel: String? = null) : PdfTemplateMedDokumentNavn {
    override val dokumentMal = DokumentMal.Skattegrunnlag
    override val dokumentNavn = tittel ?: "Skattegrunnlag"
}
