package no.nav.su.se.bakover.domain.beregning

import arrow.core.Either
import kotlinx.coroutines.runBlocking
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.UUID

enum class Fradragstype {
    Uføretrygd,
    Barnetillegg,
    Arbeidsinntekt,
    Pensjon,
    Kapitalinntekt,
    UtenlandskInntekt,
    ForventetInntekt,
    AndreYtelser;

    companion object {
        fun isValid(s: String) =
            runBlocking {
                Either.catch { valueOf(s) }
                    .isRight()
            }
    }
}

data class Fradrag(
    val id: UUID = UUID.randomUUID(),
    val type: Fradragstype,
    val beløp: Int,
    val utenlandskInntekt: UtenlandskInntekt?,
    val inntektDelerAvPeriode: InntektDelerAvPeriode?
) {
    fun perMåned(): Int = BigDecimal(beløp).divide(BigDecimal(12), 0, RoundingMode.HALF_UP).toInt()
}

data class UtenlandskInntekt(
    val beløpIUtenlandskValuta: Int,
    val valuta: String,
    val kurs: Double
) {
    fun isValid(): Boolean {
        return beløpIUtenlandskValuta >= 0 && valuta.isNotEmpty() && kurs >= 0
    }
}

data class InntektDelerAvPeriode(
    val fraOgMed: LocalDate,
    val tilOgMed: LocalDate
) {
    fun isValid(): Boolean {
        return fraOgMed.dayOfMonth == 1 && tilOgMed.dayOfMonth == tilOgMed.lengthOfMonth()
    }
}
