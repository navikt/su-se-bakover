package vedtak.domain

import no.nav.su.se.bakover.common.tid.periode.Periode

data class GrunnbeløpOgSatsbeløpPåVedtak(
    val periode: Periode,
    val benyttetGrunnbeløp: Int?,
    val benyttetSatsbeløp: Double,
    val satskategori: String,
)
