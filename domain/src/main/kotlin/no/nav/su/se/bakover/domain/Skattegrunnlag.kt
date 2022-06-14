package no.nav.su.se.bakover.domain

import java.time.LocalDate

data class Skattegrunnlag(
    val fnr: Fnr,
    val intektsår: Int,
    val grunnlag: List<Grunnlag>,
    val skatteoppgjoersdato: LocalDate?,
) {
    enum class Kategori(val stringVerdi: String) {
        INNTEKT("inntekt"),
        FORMUE("formue"),
        INNTEKTSFRADRAG("inntektsfradrag"),
        FORMUESFRADRAG("formuesfradrag"),
    }

    data class Grunnlag(
        val navn: String,
        val beløp: Int,
        val kategori: List<Kategori>,
    )
}
