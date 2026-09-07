package no.nav.su.se.bakover.domain.kontrollnotat

data class KontrollsamtaleNotatVedleggInput(
    val filnavn: String,
    val mimeType: String,
    val innhold: ByteArray,
)
