package no.nav.su.se.bakover.domain

/**
 * Mapper responsen fra Skatteetatens API for summert skattegrunnlag
 *
 * Swagger-dokumentasjon for APIet er på
 * https://app.swaggerhub.com/apis/Skatteetaten_Deling/summert-skattegrunnlag-api/1.0.0
 */
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
