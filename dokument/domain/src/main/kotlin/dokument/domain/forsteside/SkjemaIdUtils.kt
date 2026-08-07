package dokument.domain.forsteside

const val KONTROLLNOTAT = "NAV 00-03.01"
const val SØKNAD_ALDER = "NAV 64-21.00"

fun hentFakePdfForSkjema(
    skjemaId: String,
    kontrollnotatPdf: ByteArray,
    alderPdf: ByteArray,
): ByteArray {
    return when (skjemaId) {
        KONTROLLNOTAT -> kontrollnotatPdf
        SØKNAD_ALDER -> alderPdf
        else -> throw IllegalArgumentException("Ukjent skjemaId: $skjemaId")
    }
}
