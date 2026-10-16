package dokument.domain.forsteside

const val KONTROLLNOTAT = "NAV 00-03.01"
const val SØKNAD_ALDER = "NAV 64-21.00"
const val SØKNAD_UFØRE = "NAV 64-01.00"
const val SØKNAD_VEDLEGG_ALDER = "NAVe 64-21.00"
const val SØKNAD_VEDLEGG_UFØRE = "NAVe 64-01.00"

fun hentFakePdfForSkjema(
    skjemaId: String,
    kontrollnotatPdf: ByteArray,
    alderPdf: ByteArray,
    uførePdf: ByteArray,
    vedleggAlderPdf: ByteArray,
    vedleggUførePdf: ByteArray,
): ByteArray {
    return when (skjemaId) {
        KONTROLLNOTAT -> kontrollnotatPdf
        SØKNAD_ALDER -> alderPdf
        SØKNAD_UFØRE -> uførePdf
        SØKNAD_VEDLEGG_ALDER -> vedleggAlderPdf
        SØKNAD_VEDLEGG_UFØRE -> vedleggUførePdf
        else -> throw IllegalArgumentException("Ukjent skjemaId: $skjemaId")
    }
}
