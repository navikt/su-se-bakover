package no.nav.su.se.bakover.domain

data class SamletSkattegrunnlag(
    val personidentifikator: String,
    val inntektsaar: String,
    val skjermet: Boolean,
    val grunnlag: List<Skattegrunnlag>,
    val skatteoppgjoersdato: String?,
)

data class Skattegrunnlag(
    val tekniskNavn: String,
    val beloep: Int,
    val kategori: List<String>
)
