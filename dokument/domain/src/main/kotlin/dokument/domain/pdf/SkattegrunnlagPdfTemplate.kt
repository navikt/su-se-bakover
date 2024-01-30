package dokument.domain.pdf

data object SkattegrunnlagPdfTemplate : PdfTemplateMedDokumentNavn {
    override val pdfTemplate = PdfTemplate.Skattegrunnlag
    override val dokumentNavn = "Skattegrunnlag"
}
