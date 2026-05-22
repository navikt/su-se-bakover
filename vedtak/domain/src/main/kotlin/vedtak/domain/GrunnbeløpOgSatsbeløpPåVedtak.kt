package vedtak.domain

import java.time.LocalDate

data class GrunnbeløpOgSatsbeløpPåVedtak(
    val benyttetGrunnbeløp: Int?,
    val benyttetSatsbeløp: Double,
    val satskategori: String,
    val stansetYtelse: Boolean = false,
    val fraOgMed: LocalDate,
)
