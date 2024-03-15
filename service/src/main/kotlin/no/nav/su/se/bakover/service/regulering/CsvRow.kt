package no.nav.su.se.bakover.service.regulering

data class CsvRow(
    val fnr: String,
    val fom: String,
    val tom: String,
    val type: String,
    val beløp: String,
)
