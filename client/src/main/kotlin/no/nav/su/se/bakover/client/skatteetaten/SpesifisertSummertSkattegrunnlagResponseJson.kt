package no.nav.su.se.bakover.client.skatteetaten

import arrow.core.Either
import arrow.core.flatMap
import no.nav.su.se.bakover.client.skatteetaten.SpesifisertSummertSkattegrunnlagResponseJson.SpesifisertSummertSkattegrunnlagsobjekt.Companion.toDomain
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.log
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.domain.skatt.Skattegrunnlag
import no.nav.su.se.bakover.domain.skatt.SkatteoppslagFeil
import no.nav.su.se.bakover.domain.skatt.Stadie
import java.time.LocalDate
import java.time.Year
import no.nav.su.se.bakover.client.skatteetaten.SpesifisertSummertSkattegrunnlagResponseJson.SpesifisertSummertSkattegrunnlagsobjekt.Spesifisering as EksternSpesifisering

internal data class SpesifisertSummertSkattegrunnlagResponseJson(
    val grunnlag: List<SpesifisertSummertSkattegrunnlagsobjekt> = emptyList(),
    val skatteoppgjoersdato: String?,
    val svalbardGrunnlag: List<SpesifisertSummertSkattegrunnlagsobjekt> = emptyList(),
) {
    /**
     * @param spesifisering I følge modellen til skatt, kan et innslag ha 0, 1 eller flere spesifiseringer.
     */
    internal data class SpesifisertSummertSkattegrunnlagsobjekt(
        val beloep: String,
        val spesifisering: List<EksternSpesifisering> = emptyList(),
        val tekniskNavn: String,
        val kategori: String,
    ) {
        /**
         * Finnes mange forskjellige typer spesifisering. Vi er bare interessert i kjøretøy
         */
        data class Spesifisering(
            val type: String,
            val beloep: String?,
            val registreringsnummer: String?,
            val fabrikatnavn: String?,
            val aarForFoerstegangsregistrering: String?,
            val formuesverdi: String?,
            val antattVerdiSomNytt: String?,
            val antattMarkedsverdi: String?,
        )

        fun toDomain(): Skattegrunnlag.Grunnlag {
            val spesifisering = spesifisering.toDomain().also {
                if (!kategori.contains("formue")) {
                    log.error("Mottok spesifisering av kjøretøy som ikke er tilknyttet formue.")
                }
            }

            return when (this.kategori) {
                "inntekt" -> Skattegrunnlag.Grunnlag.Inntekt(
                    navn = this.tekniskNavn,
                    beløp = this.beloep,
                )
                "formue" -> Skattegrunnlag.Grunnlag.Formue(
                    navn = this.tekniskNavn,
                    beløp = this.beloep,
                    spesifisering = spesifisering,
                )
                "inntektsfradrag" -> Skattegrunnlag.Grunnlag.Inntektsfradrag(
                    navn = this.tekniskNavn,
                    beløp = this.beloep,
                )
                "formuesfradrag" -> Skattegrunnlag.Grunnlag.Formuesfradrag(
                    navn = this.tekniskNavn,
                    beløp = this.beloep,
                )
                "verdsettingsrabattSomGirGjeldsreduksjon" ->
                    Skattegrunnlag.Grunnlag.VerdsettingsrabattSomGirGjeldsreduksjon(
                        navn = this.tekniskNavn,
                        beløp = this.beloep,
                    )
                "oppjusteringAvEierinntekter" -> Skattegrunnlag.Grunnlag.OppjusteringAvEierinntekter(
                    navn = this.tekniskNavn,
                    beløp = this.beloep,
                )
                "-" -> Skattegrunnlag.Grunnlag.ManglerKategori(
                    navn = this.tekniskNavn,
                    beløp = this.beloep,
                )
                else -> Skattegrunnlag.Grunnlag.Annet(
                    navn = this.tekniskNavn,
                    beløp = this.beloep,
                ).also {
                    log.error("Ukjent Skattekategori: ${this.kategori}. Denne bør legges til asap.")
                }
            }
        }

        companion object {
            fun List<SpesifisertSummertSkattegrunnlagsobjekt>.toDomain(): Skattegrunnlag.Grunnlagsliste {
                return this.map { it.toDomain() }.let {
                    Skattegrunnlag.Grunnlagsliste(
                        formue = it.filterIsInstance<Skattegrunnlag.Grunnlag.Formue>(),
                        inntekt = it.filterIsInstance<Skattegrunnlag.Grunnlag.Inntekt>(),
                        inntektsfradrag = it.filterIsInstance<Skattegrunnlag.Grunnlag.Inntektsfradrag>(),
                        formuesfradrag = it.filterIsInstance<Skattegrunnlag.Grunnlag.Formuesfradrag>(),
                        verdsettingsrabattSomGirGjeldsreduksjon = it.filterIsInstance<Skattegrunnlag.Grunnlag.VerdsettingsrabattSomGirGjeldsreduksjon>(),
                        oppjusteringAvEierinntekter = it.filterIsInstance<Skattegrunnlag.Grunnlag.OppjusteringAvEierinntekter>(),
                        annet = it.filterIsInstance<Skattegrunnlag.Grunnlag.Annet>(),
                    )
                }
            }
        }
    }

    companion object {
        fun fromJson(
            json: String,
            fnr: Fnr,
            inntektsår: Year,
            stadie: Stadie,
        ): Either<SkatteoppslagFeil, Skattegrunnlag.Årsgrunnlag> {
            return Either.catch {
                deserialize<SpesifisertSummertSkattegrunnlagResponseJson>(json)
            }.flatMap {
                it.toDomain(
                    inntektsår = inntektsår,
                    stadie = stadie,
                )
            }.mapLeft {
                log.error("Feil skjedde under deserialisering/mapping av data fra Sigrun/Skatteetaten. Se sikkerlogg.")
                sikkerLogg.error(
                    "Feil skjedde under deserialisering/mapping av data fra Sigrun/Skatteetaten. Fnr: $fnr, Inntekstår:$inntektsår, Stadie: $stadie, Json: $json",
                    it,
                )
                SkatteoppslagFeil.UkjentFeil(it)
            }
        }
    }
}

private fun List<EksternSpesifisering>.toDomain(): List<Skattegrunnlag.Spesifisering.Kjøretøy> {
    return this.mapNotNull {
        when (it.type) {
            "Kjoeretoey" -> Skattegrunnlag.Spesifisering.Kjøretøy(
                beløp = it.beloep,
                registreringsnummer = it.registreringsnummer,
                fabrikatnavn = it.fabrikatnavn,
                årForFørstegangsregistrering = it.aarForFoerstegangsregistrering,
                formuesverdi = it.formuesverdi,
                antattVerdiSomNytt = it.antattVerdiSomNytt,
                antattMarkedsverdi = it.antattMarkedsverdi,
            )

            else -> {
                log.error("Fant ukjent skattegrunnlagsspesifisering: ${it.type}. Denne bør legges til asap.")
                null
            }
        }
    }
}

private fun SpesifisertSummertSkattegrunnlagResponseJson.toDomain(
    inntektsår: Year,
    stadie: Stadie,
): Either<Throwable, Skattegrunnlag.Årsgrunnlag> {
    return Either.catch {
        Skattegrunnlag.Årsgrunnlag(
            inntektsår = inntektsår,
            grunnlag = grunnlag.toDomain() + svalbardGrunnlag.toDomain(),
            skatteoppgjørsdato = skatteoppgjoersdato?.let { LocalDate.parse(it) },
            stadie = stadie,
        )
    }
}
