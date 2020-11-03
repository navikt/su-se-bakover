package no.nav.su.se.bakover.domain.beregning.fradrag

data class UtenlandskInntekt(
    val beløpIUtenlandskValuta: Int,
    val valuta: String,
    val kurs: Double
) {
    fun isValid(): Boolean {
        return beløpIUtenlandskValuta >= 0 && valuta.isNotBlank() && kurs >= 0
    }
}
