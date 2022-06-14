package no.nav.su.se.bakover.client.skatteetaten

import arrow.core.Either
import no.nav.su.se.bakover.domain.Fnr
import java.time.LocalDate

/**
 * Mapper responsen fra Skatteetatens API for summert skattegrunnlag
 *
 * Swagger-dokumentasjon for APIet er på
 * https://app.swaggerhub.com/apis/Skatteetaten_Deling/summert-skattegrunnlag-api/1.0.0
 */
internal data class SamletSkattegrunnlag(
    val personidentifikator: String,
    val inntektsaar: String,
    val skjermet: Boolean,
    val grunnlag: List<Skattegrunnlag>,
    val skatteoppgjoersdato: String?,
)

internal data class Skattegrunnlag(
    val tekniskNavn: String,
    val beloep: Int,
    val kategori: List<String>,
)

internal fun SamletSkattegrunnlag.toDomain(): Either<Throwable, no.nav.su.se.bakover.domain.Skattegrunnlag> {
    return Either.catch {
        no.nav.su.se.bakover.domain.Skattegrunnlag(
            fnr = Fnr(personidentifikator),
            intektsår = inntektsaar.toInt(),
            grunnlag = grunnlag.map {
                no.nav.su.se.bakover.domain.Skattegrunnlag.Grunnlag(
                    navn = it.tekniskNavn,
                    beløp = it.beloep,
                    kategori = it.kategori.map { kategoriNavn ->
                        no.nav.su.se.bakover.domain.Skattegrunnlag.Kategori.valueOf(kategoriNavn)
                    },
                )
            },
            skatteoppgjoersdato = skatteoppgjoersdato?.let { LocalDate.parse(it) },
        )
    }
}
