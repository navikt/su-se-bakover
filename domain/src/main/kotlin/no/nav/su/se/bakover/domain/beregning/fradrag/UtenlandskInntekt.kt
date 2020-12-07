package no.nav.su.se.bakover.domain.beregning.fradrag

import com.fasterxml.jackson.annotation.JsonIgnore

data class UtenlandskInntekt(
    val beløpIUtenlandskValuta: Int,
    val valuta: String,
    val kurs: Double
) {
    @JsonIgnore
    fun isValid(): Boolean {
        return beløpIUtenlandskValuta >= 0 && valuta.isNotBlank() && kurs >= 0
    }
}
