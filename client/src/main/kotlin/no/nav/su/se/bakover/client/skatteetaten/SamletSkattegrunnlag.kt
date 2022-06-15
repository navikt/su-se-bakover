package no.nav.su.se.bakover.client.skatteetaten

import arrow.core.Either
import no.nav.su.se.bakover.common.log
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Skattegrunnlag.Kategori
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
            inntektsår = inntektsaar.toInt(),
            grunnlag = grunnlag.map {
                no.nav.su.se.bakover.domain.Skattegrunnlag.Grunnlag(
                    navn = it.tekniskNavn,
                    beløp = it.beloep,
                    kategori = it.kategori.mapNotNull { kategoriNavn ->
                        when (kategoriNavn) {
                            Kategori.INNTEKT.stringVerdi -> Kategori.INNTEKT
                            Kategori.FORMUE.stringVerdi -> Kategori.FORMUE
                            Kategori.INNTEKTSFRADRAG.stringVerdi -> Kategori.INNTEKTSFRADRAG
                            Kategori.FORMUESFRADRAG.stringVerdi -> Kategori.FORMUESFRADRAG
                            Kategori.VERDSETTINGSRABATT_SOM_GIR_GJELDSREDUKSJON.stringVerdi -> Kategori.VERDSETTINGSRABATT_SOM_GIR_GJELDSREDUKSJON
                            Kategori.OPPJUSTERING_AV_EIERINNTEKTER.stringVerdi -> Kategori.OPPJUSTERING_AV_EIERINNTEKTER
                            else -> {
                                log.warn("Fant en ukjent mapping for ${this.javaClass.simpleName}: $kategoriNavn")
                                null
                            }
                        }
                    },
                )
            },
            skatteoppgjoersdato = skatteoppgjoersdato?.let { LocalDate.parse(it) },
        )
    }
}
