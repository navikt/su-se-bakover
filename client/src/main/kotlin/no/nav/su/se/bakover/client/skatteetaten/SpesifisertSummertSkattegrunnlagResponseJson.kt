package no.nav.su.se.bakover.client.skatteetaten

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.client.skatteetaten.SpesifisertSummertSkattegrunnlagResponseJson.SpesifisertSummertSkattegrunnlagsobjekt.Companion.toDomain
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.log
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.domain.skatt.Skattegrunnlag
import no.nav.su.se.bakover.domain.skatt.Stadie
import java.time.Clock
import java.time.LocalDate
import java.time.Year
import no.nav.su.se.bakover.client.skatteetaten.SpesifisertSummertSkattegrunnlagResponseJson.SpesifisertSummertSkattegrunnlagsobjekt.Spesifisering as EksternSpesifisering

internal data class SpesifisertSummertSkattegrunnlagResponseJson(
    val grunnlag: List<SpesifisertSummertSkattegrunnlagsobjekt> = emptyList(),
    val skatteoppgjoersdato: String?,
    val svalbardGrunnlag: List<SpesifisertSummertSkattegrunnlagsobjekt> = emptyList(),
) {
    /**
     * https://skatteetaten.github.io/datasamarbeid-api-dokumentasjon/download/spesifisertSkattegrunnlag/Oversikt.png
     *
     * @param spesifisering I følge modellen til skatt, kan et innslag ha 0, 1 eller flere spesifiseringer.
     * @param kategori I følge modellen til skatt, kan et innslag ha 0, 1 eller flere kategorier.
     */
    internal data class SpesifisertSummertSkattegrunnlagsobjekt(
        val beloep: String,
        val spesifisering: List<EksternSpesifisering> = emptyList(),
        val tekniskNavn: String,
        val kategori: List<String> = emptyList(),
    ) {
        /**
         * @type oneOf (Kjoeretoey)
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

        fun toDomain(): List<Skattegrunnlag.Grunnlag> {
            val spesifisering = spesifisering.toDomain().also {
                if (!kategori.contains("formue")) {
                    log.error("Mottok spesifisering av kjøretøy som ikke er tilknyttet formue.")
                }
            }

            return this.kategori.map {
                when (it) {
                    "inntekt" -> {
                        Skattegrunnlag.Grunnlag.Inntekt(
                            navn = this.tekniskNavn,
                            beløp = this.beloep,
                        )
                    }

                    "formue" -> {
                        Skattegrunnlag.Grunnlag.Formue(
                            navn = this.tekniskNavn,
                            beløp = this.beloep,
                            spesifisering = spesifisering,
                        )
                    }

                    "inntektsfradrag" -> {
                        Skattegrunnlag.Grunnlag.Inntektsfradrag(
                            navn = this.tekniskNavn,
                            beløp = this.beloep,
                        )
                    }

                    "formuesfradrag" -> {
                        Skattegrunnlag.Grunnlag.Formuesfradrag(
                            navn = this.tekniskNavn,
                            beløp = this.beloep,
                        )
                    }

                    "verdsettingsrabattSomGirGjeldsreduksjon" -> {
                        Skattegrunnlag.Grunnlag.VerdsettingsrabattSomGirGjeldsreduksjon(
                            navn = this.tekniskNavn,
                            beløp = this.beloep,
                        )
                    }

                    "oppjusteringAvEierinntekter" -> {
                        Skattegrunnlag.Grunnlag.OppjusteringAvEierinntekter(
                            navn = this.tekniskNavn,
                            beløp = this.beloep,
                        )
                    }

                    else -> {
                        log.error("Ukjent Skattekategori: $it. Denne bør legges til asap.")
                        Skattegrunnlag.Grunnlag.Annet(
                            navn = this.tekniskNavn,
                            beløp = this.beloep,
                        )
                    }
                }
            }
        }

        companion object {
            fun List<SpesifisertSummertSkattegrunnlagsobjekt>.toDomain(): Skattegrunnlag.Grunnlagsliste {
                return this.flatMap {
                    it.toDomain()
                }.let {
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
            clock: Clock,
            fnr: Fnr,
            inntektsår: Year,
            stadie: Stadie,
        ): Either<SkatteoppslagFeil, Skattegrunnlag> {
            return Either.catch {
                deserialize<SpesifisertSummertSkattegrunnlagResponseJson>(json)
            }.flatMap {
                it.toDomain(
                    clock = clock,
                    fnr = fnr,
                    inntektsår = inntektsår,
                    stadie = stadie,
                )
            }.mapLeft {
                log.error("Feil skjedde under deserialisering/mapping av data fra Sigrun/Skatteetaten. Se sikkerlogg.")
                sikkerLogg.error("Feil skjedde under deserialisering/mapping av data fra Sigrun/Skatteetaten.", it)
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
    clock: Clock,
    fnr: Fnr,
    inntektsår: Year,
    stadie: Stadie,
): Either<Throwable, Skattegrunnlag> {
    return Either.catch {
        Skattegrunnlag(
            fnr = fnr,
            stadie = stadie,
            hentetTidspunkt = Tidspunkt.now(clock),
            årsgrunnlag = nonEmptyListOf(
                Skattegrunnlag.Årsgrunnlag(
                    inntektsår = inntektsår,
                    grunnlag = grunnlag.toDomain() + svalbardGrunnlag.toDomain(),
                    skatteoppgjørsdato = skatteoppgjoersdato?.let { LocalDate.parse(it) },
                ),
            ),
        )
    }
}
