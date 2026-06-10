package vedtak.domain

data class GrunnbeløpOgSatsbeløpPåVedtak(
    val benyttetGrunnbeløp: Int?,
    val benyttetSatsbeløp: Double,
    val satskategori: String,
)
