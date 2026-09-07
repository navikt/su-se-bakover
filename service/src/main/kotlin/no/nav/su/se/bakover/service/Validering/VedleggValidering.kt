import io.ktor.http.ContentType

object VedleggValidering {
    val tillatteMimeTyper = setOf(
        ContentType.Image.JPEG.toString(),
        ContentType.Image.PNG.toString(),
        ContentType.Application.Pdf.toString(),
    )

    // 20mb
    const val MAKS_VEDLEGG_STORRELSE_BYTES = 20 * 1024 * 1024

    internal fun matcherFilnavnMimeType(
        filnavn: String,
        mimeType: String,
    ): Boolean {
        val filendelserPerMimeType = mapOf(
            ContentType.Image.JPEG.toString() to setOf("jpg", "jpeg"),
            ContentType.Image.PNG.toString() to setOf("png"),
            ContentType.Application.Pdf.toString() to setOf("pdf"),
        )
        val filendelse = filnavn.substringAfterLast('.', "").lowercase()
        if (filendelse.isBlank()) return false
        return filendelserPerMimeType[mimeType]?.contains(filendelse) == true
    }
}
