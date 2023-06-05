package no.nav.su.se.bakover.client.skatteetaten

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.domain.skatt.Skattegrunnlag
import no.nav.su.se.bakover.domain.skatt.Stadie
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.Year
import no.nav.su.se.bakover.client.skatteetaten.SpesifisertSummertSkattegrunnlagResponseJson.SpesifisertSummertSkattegrunnlagsobjekt.Spesifisering as EksternSpesifisering

private val log = LoggerFactory.getLogger(SpesifisertSummertSkattegrunnlagResponseJson::class.java)

internal data class SpesifisertSummertSkattegrunnlagResponseJson(
    val grunnlag: List<SpesifisertSummertSkattegrunnlagsobjekt> = emptyList(),
    val skatteoppgjoersdato: String?,
    val svalbardGrunnlag: List<SpesifisertSummertSkattegrunnlagsobjekt>? = null,
) {

    /**
     * @param spesifisering I følge modellen til skatt, kan et innslag ha 0, 1 eller flere spesifiseringer.
     */
    internal data class SpesifisertSummertSkattegrunnlagsobjekt(
        val beloep: String,
        val spesifisering: List<EksternSpesifisering>? = null,
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

        fun toDomain(år: Year, stadie: Stadie): Skattegrunnlag.Grunnlag {
            val spesifisering = spesifisering?.toDomain().also {
                if (!kategori.contains("formue") && !it.isNullOrEmpty()) {
                    log.error("Mottok spesifisering av kjøretøy som ikke er tilknyttet formue. år: $år, stadie: $stadie, kategori: $kategori")
                }
            } ?: emptyList()

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
    }

    companion object {
        fun fromJson(
            json: String,
            fnr: Fnr,
            inntektsår: Year,
            stadie: Stadie,
        ): Either<SkatteoppslagFeil, Skattegrunnlag.SkattegrunnlagForÅr> {
            return Either.catch {
                deserialize<SpesifisertSummertSkattegrunnlagResponseJson>(json)
            }.flatMap {
                it.toDomain(inntektsår, stadie)
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
    år: Year,
    stadie: Stadie,
): Either<Throwable, Skattegrunnlag.SkattegrunnlagForÅr> {
    val oppgjørsdato: LocalDate? = Either.catch {
        this.skatteoppgjoersdato?.let { LocalDate.parse(it) }
    }.getOrElse {
        return it.left()
    }

    val mappet: List<Skattegrunnlag.Grunnlag> = (this.grunnlag + (this.svalbardGrunnlag ?: emptyList()))
        .map { it.toDomain(år, stadie) }

    return Skattegrunnlag.SkattegrunnlagForÅr(
        oppgjørsdato = oppgjørsdato,
        formue = mappet.filterIsInstance<Skattegrunnlag.Grunnlag.Formue>(),
        inntekt = mappet.filterIsInstance<Skattegrunnlag.Grunnlag.Inntekt>(),
        inntektsfradrag = mappet.filterIsInstance<Skattegrunnlag.Grunnlag.Inntektsfradrag>(),
        formuesfradrag = mappet.filterIsInstance<Skattegrunnlag.Grunnlag.Formuesfradrag>(),
        verdsettingsrabattSomGirGjeldsreduksjon = mappet.filterIsInstance<Skattegrunnlag.Grunnlag.VerdsettingsrabattSomGirGjeldsreduksjon>(),
        oppjusteringAvEierinntekter = mappet.filterIsInstance<Skattegrunnlag.Grunnlag.OppjusteringAvEierinntekter>(),
        manglerKategori = mappet.filterIsInstance<Skattegrunnlag.Grunnlag.ManglerKategori>(),
        annet = mappet.filterIsInstance<Skattegrunnlag.Grunnlag.Annet>(),
    ).right()
}
