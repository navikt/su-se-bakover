package no.nav.su.se.bakover.web.routes.regulering

data class SupplementInnholdAsCsv(
    val fnr: String,
    val fom: String,
    val tom: String,
    val type: String,
    val beløp: String,
)
