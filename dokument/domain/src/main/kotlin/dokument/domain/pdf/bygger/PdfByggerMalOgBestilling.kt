package dokument.domain.pdf.bygger

interface PdfByggerMalOgBestilling {
    val navn: String
    fun lagBestillingMedMal(data: Verdi): PdfBestilling
}

internal fun lagMal(navn: String, lag: (Verdi) -> PdfBestilling): PdfByggerMalOgBestilling = object : PdfByggerMalOgBestilling {
    override val navn = navn
    override fun lagBestillingMedMal(data: Verdi) = lag(data)
}
